# Kafka Event-Driven Flow: Complete Demonstration Guide

This guide walks you through setting up and demonstrating the full asynchronous trading flow across the **Trade API** and the **Event Backbone Executor**, entirely from scratch.

By following this guide, you will observe the complete lifecycle of a trade event:
`HTTP Request -> Trade API -> Kafka (orders) -> Executor -> Kafka (trade-events)`

## Prerequisites

1. **Clean Environment**: Make sure no other instances of the applications are currently running (kill them in your terminal or IDE).
2. **Ports**: Ensure ports `8080` (Trade API), `8082` (Executor), `5432` (PostgreSQL), and `9092` (Kafka) are free.

---

## Step 1: Wipe and Recreate Infrastructure

We have updated the Docker Compose file to use the default `trading` database with the `postgres` user, so you no longer need to pass environment variables when starting the Java apps!

However, because your Docker volume was previously initialized differently, you **must** destroy the old volume and let it recreate cleanly.

Open **Terminal 1** and run:

```bash
cd infra
docker-compose down -v
docker-compose up -d
```

_(Wait 10-15 seconds for PostgreSQL and Kafka to fully initialize)._

---

## Step 2: Create Kafka Topics

Kafka does not automatically create topics in this project. You must run the provisioning script.

Still in **Terminal 1**, run:

```bash
./create-kafka-topics.sh
```

You should see `orders`, `trade-events`, `market-data`, and their `DLT` counterparts created successfully.

---

## Step 3: Start the Trade API

The Trade API will automatically run the `schema.sql` and `data.sql` scripts to seed the `trading` database upon startup.

Open **Terminal 2** and run:

```bash
cd sprint-06-trade-api
mvn spring-boot:run
```

_(Wait until you see `Started TradingApplication` in the logs)._

---

## Step 4: Start the Event Backbone Executor

The Executor consumes from the `orders` topic and publishes to the `trade-events` topic. It also polls live market data. To allow it to fetch real prices (so your trades actually fill instead of getting rejected), you must pass your Fauxnance API key.

Open **Terminal 3** and run:

```bash
cd sprint-07-event-backbone/executor
FAUXNANCE_API_KEY=put here \
mvn spring-boot:run
```

_(Wait until you see `Started TradeExecutorApplication` in the logs)._

---

## Step 5: Monitor the Kafka Streams

To visually prove that events are flowing asynchronously through the broker, we will attach consumers directly to the Kafka container.

Open **Terminal 4** (Watch Orders):

```bash
docker exec -it kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic orders
```

Open **Terminal 5** (Watch Trade Events):

```bash
docker exec -it kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic trade-events
```

---

## Step 6: Execute the Demonstration!

Now it's time to act as a user. We will log in as `john_doe` and place a `MARKET BUY` order.

Open **Terminal 6** and run the login command to get a JWT token:

```bash
curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"john_doe", "password":"password123"}'
```

Copy the `accessToken` string from the JSON response.

Finally, place the order by substituting `<YOUR_TOKEN>` with the token you just copied:

```bash
curl -s -X POST http://localhost:8080/api/v1/orders \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <YOUR_TOKEN>" \
  -d '{
    "accountId": 2,
    "symbol": "AMZN",
    "side": "BUY",
    "orderType": "MARKET",
    "quantity": 5,
    "idempotencyKey": "demo-trade-001"
  }'
```

### The Grand Finale: What You Will See

The exact moment you press enter on that curl command:

1. You will receive an immediate `201 Created` HTTP response from the API.
2. In **Terminal 4**, an `ORDER_PLACED` event will instantly appear.
3. In **Terminal 3**, the Executor will log that it received and executed the order.
4. In **Terminal 5**, an `ORDER_FILLED` event will instantly appear!
