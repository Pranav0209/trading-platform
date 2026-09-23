package com.zkuba.trading.event;

import com.zkuba.trading.domain.entity.Order;

/**
 * Internal Spring application event that triggers the Kafka publisher.
 */
public record OrderPlacedEvent(Order order) {
}
