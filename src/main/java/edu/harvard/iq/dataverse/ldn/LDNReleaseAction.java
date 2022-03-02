package edu.harvard.iq.dataverse.ldn;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;

import edu.harvard.iq.dataverse.Dataset;

public class LDNReleaseAction extends LDNMessage {

    private Dataset localDataset;
    private JsonObject remoteResource;
    private String relationship;
    private final static JsonArray type = Json.createArrayBuilder().add("Announce").add("coar-notify:ReleaseAction").build(); 
    
    public LDNReleaseAction(JsonObject target, JsonObject origin, Dataset localDataset, JsonObject remoteResource, String relationship) {
        super(target, origin, type);
        this.setLocalDataset(localDataset);
        this.setRemoteResource(remoteResource);
        this.setRelationship(relationship);
    }

    public Dataset getLocalDataset() {
        return localDataset;
    }

    public void setLocalDataset(Dataset localDataset) {
        this.localDataset = localDataset;
    }

    public JsonObject getRemoteResource() {
        return remoteResource;
    }

    public void setRemoteResource(JsonObject remoteResource) {
        this.remoteResource = remoteResource;
    }

    public String getRelationship() {
        return relationship;
    }

    public void setRelationship(String relationship) {
        this.relationship = relationship;
    }

}
