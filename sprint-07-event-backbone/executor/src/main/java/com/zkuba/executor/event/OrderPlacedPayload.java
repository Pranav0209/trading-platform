package com.zkuba.executor.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OrderPlacedPayload(
    UUID orderId
) {
}
