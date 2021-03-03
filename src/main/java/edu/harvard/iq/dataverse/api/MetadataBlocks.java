package edu.harvard.iq.dataverse.api;

import edu.harvard.iq.dataverse.MetadataBlock;
import edu.harvard.iq.dataverse.util.json.JsonPrinter;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;
import javax.ws.rs.PathParam;

/**
 * Api bean for managing metadata blocks.
 * @author michael
 */
@Path("metadatablocks")
@Produces("application/json")
public class MetadataBlocks extends AbstractApiBean {
    
    @Inject JsonPrinter jsonPrinter;
    
    @GET
    public Response list()  {
        return ok(metadataBlockSvc.listMetadataBlocks().stream().map(jsonPrinter.brief::json).collect(jsonPrinter.toJsonArray()));
    }
    
    @Path("{identifier}")
    @GET
    public Response getBlock( @PathParam("identifier") String idtf ) {
        MetadataBlock b = findMetadataBlock(idtf);
        
        return   (b != null ) ? ok(jsonPrinter.json(b)) : notFound("Can't find metadata block '" + idtf + "'");
    }
    
}
