package com.zkuba.executor.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.UUID;
import java.util.Map;

/**
 * Standard Envelope structure as per the contracts/kafka-topics.md
 * Using @JsonIgnoreProperties(ignoreUnknown = true) is an additional safeguard
 * aligning with the requirement that consumers ignore unknown fields.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Envelope {
    private UUID eventId;
    private String eventType;
    private String eventTime;
    private String source;
    private Integer schemaVersion;
    
    // Using a Map for arbitrary payload, though in practice this would often be 
    // bound to specific types based on eventType.
    private Map<String, Object> payload;

    public Envelope() {
    }

    public UUID getEventId() { return eventId; }
    public void setEventId(UUID eventId) { this.eventId = eventId; }

    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }

    public String getEventTime() { return eventTime; }
    public void setEventTime(String eventTime) { this.eventTime = eventTime; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public Integer getSchemaVersion() { return schemaVersion; }
    public void setSchemaVersion(Integer schemaVersion) { this.schemaVersion = schemaVersion; }

    public Map<String, Object> getPayload() { return payload; }
    public void setPayload(Map<String, Object> payload) { this.payload = payload; }
}
