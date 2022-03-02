package edu.harvard.iq.dataverse.util;

import java.io.StringReader;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonNumber;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;

public class ExternalResource {

    private String uri;
    private String type;
    private String relationship;

    public ExternalResource(String jsonString) {
        JsonObject resource = null;
        try (StringReader rdr = new StringReader(jsonString)) {
            resource = Json.createReader(rdr).readObject();
        }
        setContent(resource);
    }

    public ExternalResource(JsonObject resource) {
        setContent(resource);
    }

    private void setContent(JsonObject resource) {
        setUri(resource.getString("@id"));
        setType(resource.getString("@type"));
        setRelationship(resource.getString("relationship"));
    }
    
    public String serialize() {
        JsonObjectBuilder job = Json.createObjectBuilder();
        job.add("relationship",getRelationship());
        job.add("@id",getUri());
        job.add("@type",getType());
        return job.build().toString();
    }

    public String getUri() {
        return uri;
    }

    public void setUri(String uri) {
        this.uri = uri;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getRelationship() {
        return relationship;
    }

    public void setRelationship(String relationship) {
        this.relationship = relationship;
    }

}
