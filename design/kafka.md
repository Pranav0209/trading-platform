# Kafka Topic Design Decisions

This document contains the justifications for the Kafka topic configurations, specifically partition counts, key choices, and dead-letter topics, as part of Sprint 7: Event Backbone.

## 1. Key Choices and Partitioning

**The Rule: The key decides the partition, and the partition decides the ordering.** Kafka guarantees message ordering only within a single partition, but provides no guarantees across partitions.

### `orders` and `trade-events` topics
- **Key chosen:** `accountId`
- **Why this key:** Two orders placed by the same account must be processed in the exact order they were received. For example, if an account submits a "BUY" and then a "SELL", processing the SELL first might fail due to insufficient position, which the BUY would have provided. Keying by `accountId` ensures all events for that account land on the same partition and are consumed sequentially.
- **Why not `orderId`:** If we keyed by `orderId`, every single order would likely hash to a different partition. A single account's history would be scattered across all partitions, and consumers (like the Trade Executor) would process them in parallel, completely losing the critical per-account ordering guarantee.

### `market-data` topic
- **Key chosen:** `symbol`
- **Why this key:** Market quotes only need to be ordered per instrument. A consumer tracking `AAPL` must see price updates chronologically to avoid using stale prices. Updates for `AAPL` and `MSFT` are independent and can be processed in parallel.

## 2. Partition Counts

**The Rule: Partitions can be increased later but never decreased. Increasing partitions rehashes keys, splitting historical ordering.**

### `orders` and `trade-events` (3 partitions)
- **Why 3:** Three partitions is the sweet spot for our local setup and initial scale. It allows us to run up to three instances of the `trade-executor` consumer group simultaneously, sharing the workload. If we added a 4th instance, it would sit idle as consumer groups cannot usefully exceed the partition count.
- **Impact of increasing later:** If we increase from 3 to 6 partitions in a later sprint, `accountId` keys will rehash. The account's history will split from that point onward, potentially disrupting stateful operations that rely on historical aggregation unless specifically mitigated. We chose 3 to start deliberately to avoid premature over-partitioning.

### `market-data` (6 partitions)
- **Why 6:** Market data represents a significantly higher volume of messages (low value per message, high throughput). Six partitions accommodate this higher rate by allowing more consumer instances to read quotes in parallel.

## 3. Topic Creation and Dead-Letter Topics (DLT)

Auto-creation of topics is explicitly disabled on the broker (`KAFKA_AUTO_CREATE_TOPICS_ENABLE="false"`). This ensures that if a producer writes to a misspelled or non-existent topic, we receive a failure immediately, rather than the broker silently creating a 1-partition topic with incorrect retention policies.

### Dead-Letter Topics
We explicitly created:
- `orders.DLT`
- `trade-events.DLT`
- `market-data.DLT`

**Why these settings (1 partition, 30 days retention):**
- **Partitions (1):** DLTs act as exception queues for poison messages or exhausted retries. Throughput is very low, and original ordering guarantees are no longer strictly enforceable or relevant once a message fails out of the main flow. A single partition is sufficient.
- **Retention (30 days):** Unlike `orders` (7 days) or `market-data` (1 day), DLTs require human intervention or a specialized replay tool to investigate and resolve issues. A 30-day retention gives the team ample time to analyze failures after the weekend or a busy period without the message aging out.

### How to start the stack and create topics

The local infrastructure (Kafka and PostgreSQL) and the topic creation script are located in the `infra/` directory. Follow these steps to spin up the broker and create the topics:

1. **Start the infrastructure:**
   ```bash
   cd infra
   docker compose up -d
   ```
   *Wait a few seconds for the Kafka broker to fully start up and elect a KRaft controller.*

2. **Create the topics:**
   ```bash
   ./create-kafka-topics.sh
   ```
   *This will create the three main topics and their corresponding dead-letter topics (DLTs).*

## 4. Market Data Poller Quota Arithmetic

The Fauxnance API provides a quota of 2000 requests per day per key. The `TradeExecutor`'s scheduled poller batches up to 25 symbols per request.

To stay within the 2000 requests/day quota, the polling interval was calculated as follows:
- **Maximum API Requests / Day**: 2000
- **Time per Request (at max frequency)**: 86,400 seconds / 2000 = **43.2 seconds**

If we set our polling interval to exactly 43.2 seconds for a single batch (<= 25 symbols), we would precisely consume the daily quota. 
To ensure a safety margin against clock drift or minor network retries, we enforce a strict minimum floor of **45 seconds** in the code.
- **Configured default**: 60 seconds (1440 requests per day, leaving 560 for manual testing or scaling).
- **Floor validation**: Using SpEL (`#{T(java.lang.Math).max(${poll.interval.seconds:60}, 45) * 1000}`), the application refuses to run polls faster than once every 45 seconds. 

If the database ever exceeds 25 unique symbols of interest, requiring 2 or more requests per interval, this configured interval must be increased (e.g., 90 seconds for 2 batches).
