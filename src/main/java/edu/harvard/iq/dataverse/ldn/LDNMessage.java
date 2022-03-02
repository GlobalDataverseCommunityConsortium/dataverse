package edu.harvard.iq.dataverse.ldn;

import jakarta.json.JsonArray;
import jakarta.json.JsonObject;

public class LDNMessage {

    private JsonObject target;
    private JsonObject origin;
    private JsonArray messageType;

    public LDNMessage(JsonObject target, JsonObject origin, JsonArray messageType) {
        this.target = target;
        this.origin = origin;
        this.messageType = messageType;
    }

    public JsonObject getTarget() {
        return target;
    }

    public void setTarget(JsonObject target) {
        this.target = target;
    }

    public JsonObject getOrigin() {
        return origin;
    }

    public void setOrigin(JsonObject origin) {
        this.origin = origin;
    }

    public JsonArray getMessageType() {
        return messageType;
    }

    public void setMessageType(JsonArray messageType) {
        this.messageType = messageType;
    }
}
