package com.zkuba.trading.event;

import java.time.Instant;

/**
 * Envelope matching the exact Kafka topic catalogue structure.
 * @param <T> The payload type
 */
public record Envelope<T>(
    String eventId,
    String eventType,
    Instant eventTime,
    String source,
    int schemaVersion,
    T payload
) {
}
