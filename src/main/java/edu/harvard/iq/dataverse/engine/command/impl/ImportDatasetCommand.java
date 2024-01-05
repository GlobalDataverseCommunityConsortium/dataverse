package edu.harvard.iq.dataverse.engine.command.impl;

import edu.harvard.iq.dataverse.Dataset;
import edu.harvard.iq.dataverse.engine.command.CommandContext;
import edu.harvard.iq.dataverse.engine.command.DataverseRequest;
import edu.harvard.iq.dataverse.engine.command.exception.CommandException;
import edu.harvard.iq.dataverse.engine.command.exception.CommandExecutionException;
import edu.harvard.iq.dataverse.engine.command.exception.IllegalCommandException;
import edu.harvard.iq.dataverse.engine.command.exception.PermissionException;
import edu.harvard.iq.dataverse.pidproviders.FakeDOIProvider;
import edu.harvard.iq.dataverse.pidproviders.PidProvider;

import java.io.IOException;
import java.util.Collections;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.GetMethod;
import static org.apache.commons.lang3.StringUtils.isEmpty;

/**
 * Imports a dataset from a different system. This command validates that the PID
 * of the new dataset exists, and then inserts the new dataset into the database.
 * 
 * <b>NOTE:</b> At the moment, this command only supports a single version in the 
 * dataset, and was tested with package files only.
 * 
 * @author michael
 */
public class ImportDatasetCommand extends AbstractCreateDatasetCommand {
    
    private static final Logger logger = Logger.getLogger(ImportDatasetCommand.class.getName());
    
    /**
     * Creates a new instance of the command.
     * @param theDataset The dataset we want to import.
     * @param aRequest Request context for the command.
     */
    public ImportDatasetCommand(Dataset theDataset, DataverseRequest aRequest) {
        super(theDataset, aRequest);
    }

    /**
     * Validate that the PID of the dataset, if any, exists.
     * @param ctxt
     * @throws CommandException 
     */
    @Override
    protected void additionalParameterTests(CommandContext ctxt) throws CommandException {
        
        if ( ! getUser().isSuperuser() ) {
            throw new PermissionException("ImportDatasetCommand can only be issued by a super-user.", this, Collections.emptySet(), getDataset());
        }
        
        Dataset ds = getDataset();
        
        if ( isEmpty(ds.getIdentifier()) ) {
            throw new IllegalCommandException("Imported datasets must have a persistent global identifier.", this);
        }
        
        if ( ! ctxt.dvObjects().isGlobalIdLocallyUnique(ds.getGlobalId()) ) {
            throw new IllegalCommandException("Persistent identifier " + ds.getGlobalId().asString() + " already exists in this Dataverse installation.", this);
        }
        
        String pid = ds.getPersistentURL();
        GetMethod httpGet = new GetMethod(pid); 
        httpGet.setFollowRedirects(false);

        HttpClient client = new HttpClient();

        try {
            int responseStatus = client.executeMethod(httpGet);

            if (responseStatus == 404) {
                /*
                 * Using test DOIs from DataCite, we'll get a 404 when trying to resolve the DOI
                 * to a landing page, but the DOI may already exist. An extra check here allows
                 * use of DataCite test DOIs. It also changes import slightly in allowing PIDs
                 * that exist (and accessible in the PID provider account configured in
                 * Dataverse) but aren't findable to be used. That could be the case if, for
                 * example, someone was importing a draft dataset from elsewhere.
                 */
                PidProvider pidProvider = ctxt.pidProviderFactory().getPidProvider(ds);
                if (pidProvider != null) {
                    if (pidProvider.alreadyRegistered(ds.getGlobalId(), true)) {
                        return;
                    }
                }
                throw new CommandExecutionException(
                        "Provided PID does not exist. Status code for GET '" + pid + "' is 404.", this);
            }

        } catch (Exception ex) {
            logger.log(Level.WARNING,
                    "Error while validating PID at '" + pid + "' for an imported dataset: " + ex.getMessage(), ex);
            throw new CommandExecutionException("Cannot validate PID due to an error: " + ex.getMessage(), this);
        }

    }
    
    @Override
    protected void handlePid(Dataset theDataset, CommandContext ctxt) {
        theDataset.setGlobalIdCreateTime( getTimestamp() );
    }

    @Override
    protected void postPersist(Dataset theDataset, CommandContext ctxt) throws CommandException {
    }
    
    
    
}
