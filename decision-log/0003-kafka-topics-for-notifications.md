# 0003 Notification Listener consumes orders and trade-events topics

| Field | Value |
|---|---|
| Status | accepted |
| Date | 2026-10-06 |
| Decided by | the whole team |

## Context

The Customer Notifications module must notify users when their orders reach a terminal state. The platform splits this state across two topics: `orders` contains user-initiated commands (Placed, Cancelled), while `trade-events` contains execution outcomes from the matching engine (Filled, Rejected).

## Options considered

| Option | For | Against |
|---|---|---|
| Consume only `trade-events` | Simpler consumer logic; only one topic to subscribe to. | We would miss manual user cancellations (which only exist on the `orders` topic), leaving the customer uninformed about their own actions. |
| Consume both `orders` and `trade-events` | Comprehensive coverage of all final terminal states (Filled, Rejected, Cancelled). | Requires filtering logic to avoid sending notifications for non-terminal states (e.g., ignoring `PLACED` events so we don't spam the user). |

## Decision

We chose to consume both topics using the same consumer group ID (`order-service-notifications-group`). Inside `NotificationListener.java`, we apply strict filtering: we only trigger an email for `CANCELLED` on the `orders` topic, and `ORDER_FILLED` or `ORDER_REJECTED` on the `trade-events` topic.

## Consequences

The consumer logic is slightly more complex, and the service must maintain connections to two separate partitions. Because we use the same group ID for two `@KafkaListener` methods, Kafka manages the offsets cleanly. If we ever add new intermediate order states (e.g., `PARTIALLY_FILLED`), we must explicitly update the filtering logic to handle them.
