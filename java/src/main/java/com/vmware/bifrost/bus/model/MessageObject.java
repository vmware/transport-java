package com.vmware.bifrost.bus.model;

import com.fasterxml.jackson.module.jsonSchema.JsonSchema;

@SuppressWarnings("unchecked")
public class MessageObject<T> implements Message<T> {

    protected MessageType type;
    protected Class<T> payloadClass;
    protected T payload;
    protected JsonSchema schema;


    public MessageObject() {
    }

    public MessageObject(MessageType type, T payload) {
        this.type = type;
        this.payload = payload;
        try {
            this.payloadClass = (Class<T>) payload.getClass();
        } catch (ClassCastException exp) {
            this.payloadClass = null;
        }
    }

    public MessageObject(MessageType type, T payload, JsonSchema schema) {
        this(type, payload);
        this.schema = schema;
    }

    public void setPayloadClass(Class<T> payloadClass) {

        this.payloadClass = payloadClass;
    }

    public JsonSchema getSchema() {
        return schema;
    }

    public void setSchema(JsonSchema schema) {

        this.schema = schema;
    }

    public Class<T> getPayloadClass() {

        return payloadClass;
    }

    public MessageType getType() {

        return type;
    }

    public void setType(MessageType type) {

        this.type = type;
    }

    public T getPayload() {

        return payload;
    }

    public void setPayload(T payload) {

        this.payload = payload;
    }

    public boolean isRequest() {

        return (this.type == MessageType.MessageTypeRequest ||
                this.type == MessageType.MessageTypeError);
    }

    public boolean isResponse() {
        return (this.type == MessageType.MessageTypeResponse ||
                this.type == MessageType.MessageTypeError);
    }

    public boolean isError() {

        return this.type == MessageType.MessageTypeError;
    }

    public String toString() {
        return this.getPayload().toString();
    }

}
