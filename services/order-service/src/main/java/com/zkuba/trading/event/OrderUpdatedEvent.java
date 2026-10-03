package com.zkuba.trading.event;

import com.zkuba.trading.domain.entity.Order;

public record OrderUpdatedEvent(Order order) {
}
