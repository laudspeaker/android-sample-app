package com.laudspeaker.android;

import java.util.Date;
import java.util.Map;
import java.util.UUID;

public class LaudspeakerEvent {
    /*
    Event name as a string.
     */
    private final String event;

    /*
    ID of the laudspeaker customer
     */
    private final String correlationKey = "_id";

    private final String correlationValue;
    /*
    Event properties.
     */
    private final Map<String, Object> payload;
    /*
    Evvent timestamp.
     */
    private final Date timestamp;
    /*
    Event ID
     */
    private final UUID uuid;

    private final String source = "mobile";


    public LaudspeakerEvent(String event, String id, Map<String, Object> payload) {
        this.event = event;
        this.correlationValue = id;
        this.payload = payload;
        this.timestamp = new Date(); // Refactor to use PostHogDateProvider if needed
        this.uuid = UUID.randomUUID();
    }

    // Getters (and setters if needed)
    public String getEvent() {
        return event;
    }

    public String getId() {
        return correlationValue;
    }

    public Map<String, Object> getPayload() {
        return payload;
    }

    public Date getTimestamp() {
        return timestamp;
    }

    public UUID getEventId() {
        return uuid;
    }
}