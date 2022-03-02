package edu.harvard.iq.dataverse.api;

import edu.harvard.iq.dataverse.Dataset;
import edu.harvard.iq.dataverse.DatasetServiceBean;
import edu.harvard.iq.dataverse.DataverseRoleServiceBean;
import edu.harvard.iq.dataverse.DvObject;
import edu.harvard.iq.dataverse.GlobalId;
import edu.harvard.iq.dataverse.MailServiceBean;
import edu.harvard.iq.dataverse.RoleAssigneeServiceBean;
import edu.harvard.iq.dataverse.RoleAssignment;
import edu.harvard.iq.dataverse.UserNotification;
import edu.harvard.iq.dataverse.UserNotificationServiceBean;
import edu.harvard.iq.dataverse.api.AbstractApiBean.WrappedResponse;
import edu.harvard.iq.dataverse.authorization.Permission;
import edu.harvard.iq.dataverse.authorization.groups.impl.ipaddress.ip.IpAddress;
import edu.harvard.iq.dataverse.branding.BrandingUtil;
import edu.harvard.iq.dataverse.dataset.DatasetThumbnail;
import edu.harvard.iq.dataverse.engine.command.DataverseRequest;
import edu.harvard.iq.dataverse.engine.command.impl.UpdateDatasetThumbnailCommand;
import edu.harvard.iq.dataverse.ldn.LDNReleaseAction;
import edu.harvard.iq.dataverse.settings.SettingsServiceBean;
import edu.harvard.iq.dataverse.util.BundleUtil;
import edu.harvard.iq.dataverse.util.SystemConfig;
import edu.harvard.iq.dataverse.util.json.JSONLDUtil;
import edu.harvard.iq.dataverse.util.json.JsonLDNamespace;
import edu.harvard.iq.dataverse.util.json.JsonLDTerm;

import java.util.Arrays;
import java.util.Date;
import java.util.Optional;
import java.util.Set;
import java.io.InputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.sql.Timestamp;
import java.util.logging.Logger;

import jakarta.ejb.EJB;
import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;
import jakarta.json.JsonWriter;
import jakarta.mail.internet.InternetAddress;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ServiceUnavailableException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.ServerErrorException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.glassfish.jersey.media.multipart.FormDataParam;

import com.apicatalog.jsonld.JsonLd;
import com.apicatalog.jsonld.JsonLdError;
import com.apicatalog.jsonld.document.JsonDocument;

@Path("inbox")
public class LDNInbox extends AbstractApiBean {

    private static final Logger logger = Logger.getLogger(LDNInbox.class.getName());

    @EJB
    SettingsServiceBean settingsService;

    @EJB
    DatasetServiceBean datasetService;

    @EJB
    MailServiceBean mailService;

    @EJB
    UserNotificationServiceBean userNotificationService;

    @EJB
    DataverseRoleServiceBean roleService;

    @EJB
    RoleAssigneeServiceBean roleAssigneeService;
    @Context
    protected HttpServletRequest httpRequest;

    @POST
    @Path("/")
    @Consumes(MediaType.APPLICATION_JSON + "+ld")
    public Response acceptMessage(String body) {
        IpAddress origin = new DataverseRequest(null, httpRequest).getSourceAddress();
        String whitelist = settingsService.get(SettingsServiceBean.Key.MessageHosts.toString(), "*");
        // Only do something if we listen to this host
        if (whitelist.equals("*") || whitelist.contains(origin.toString())) {
            String citingPID = null;
            String citingType = null;
            boolean sent = false;
            JsonObject jsonld = JSONLDUtil.decontextualizeJsonLD(body);
            if (jsonld == null) {
                throw new BadRequestException("Could not parse message to find acceptable citation link to a dataset.");
            }
            String relationship = "isRelatedTo";
            if (jsonld.containsKey(JsonLDTerm.schemaOrg("identifier").getUrl())) {
                citingPID = jsonld.getJsonObject(JsonLDTerm.schemaOrg("identifier").getUrl()).getString("@id");
                logger.fine("Citing PID: " + citingPID);
                if (jsonld.containsKey("@type")) {
                    citingType = jsonld.getString("@type");
                    if (citingType.startsWith(JsonLDNamespace.schema.getUrl())) {
                        citingType = citingType.replace(JsonLDNamespace.schema.getUrl(), "");
                    }
                    logger.fine("Citing Type: " + citingType);
                    if (jsonld.containsKey(JsonLDTerm.schemaOrg("citation").getUrl())) {
                        JsonObject citation = jsonld.getJsonObject(JsonLDTerm.schemaOrg("citation").getUrl());
                        if (citation != null) {
                            if (citation.containsKey("@type")
                                    && citation.getString("@type").equals(JsonLDTerm.schemaOrg("Dataset").getUrl())
                                    && citation.containsKey(JsonLDTerm.schemaOrg("identifier").getUrl())) {
                                String pid = citation.getString(JsonLDTerm.schemaOrg("identifier").getUrl());
                                logger.fine("Raw PID: " + pid);
                                if (pid.startsWith(GlobalId.DOI_RESOLVER_URL)) {
                                    pid = pid.replace(GlobalId.DOI_RESOLVER_URL, GlobalId.DOI_PROTOCOL + ":");
                                } else if (pid.startsWith(GlobalId.HDL_RESOLVER_URL)) {
                                    pid = pid.replace(GlobalId.HDL_RESOLVER_URL, GlobalId.HDL_PROTOCOL + ":");
                                }
                                logger.fine("Protocol PID: " + pid);
                                Optional<GlobalId> id = GlobalId.parse(pid);
                                Dataset dataset = datasetSvc.findByGlobalId(pid);
                                if (dataset != null) {
                                    JsonObject citingResource = Json.createObjectBuilder().add("@id", citingPID)
                                            .add("@type", citingType).add("relationship", relationship).build();
                                    StringWriter sw = new StringWriter(128);
                                    try (JsonWriter jw = Json.createWriter(sw)) {
                                        jw.write(citingResource);
                                    }
                                    String jsonstring = sw.toString();
                                    Set<RoleAssignment> ras = roleService.rolesAssignments(dataset);

                                    roleService.rolesAssignments(dataset).stream()
                                            .filter(ra -> ra.getRole().permissions()
                                                    .contains(Permission.PublishDataset))
                                            .flatMap(
                                                    ra -> roleAssigneeService
                                                            .getExplicitUsers(roleAssigneeService
                                                                    .getRoleAssignee(ra.getAssigneeIdentifier()))
                                                            .stream())
                                            .distinct() // prevent double-send
                                            .forEach(au -> {

                                                if (au.isSuperuser()) {
                                                    userNotificationService.sendNotification(au,
                                                            new Timestamp(new Date().getTime()),
                                                            UserNotification.Type.DATASETMENTIONED, dataset.getId(),
                                                            null, null, true, jsonstring);

                                                }
                                            });
                                    sent = true;
                                }
                                // .forEach( au -> userNotificationService.sendNotificationInNewTransaction(au,
                                // timestamp, type, dataset.getLatestVersion().getId()) );

// Subject: <<<Root: You have been assigned a role>>>. Body: Root Support, the Root has just been notified that the http://schema.org/ScholarlyArticle <a href={3}/dataset.xhtml?persistentId={4}>http://ec2-3-236-45-73.compute-1.amazonaws.com</a> cites "<a href={5}>Une Démonstration</a>.<br><br>You may contact us for support at qqmyers@hotmail.com.<br><br>Thank you,<br>Root Support
                            }
                        }
                    }
                }
            }

            if (!sent) {
                if (citingPID == null || citingType == null) {
                    throw new BadRequestException(
                            "Could not parse message to find acceptable citation link to a dataset.");
                } else {
                    throw new ServiceUnavailableException(
                            "Unable to process message. Please contact the administrators.");
                }
            }
        } else

        {
            logger.info("Ignoring message from IP address: " + origin.toString());
            throw new ForbiddenException("Inbox does not acept messages from this address");
        }

        return

        ok("Message Received");
    }
}
