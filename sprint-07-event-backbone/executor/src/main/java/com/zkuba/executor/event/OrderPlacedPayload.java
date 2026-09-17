package com.zkuba.executor.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.UUID;

// what is extracted from envelope, ignores everything and just takes orderId and queries db
@JsonIgnoreProperties(ignoreUnknown = true)
public record OrderPlacedPayload(
        UUID orderId) {
}
