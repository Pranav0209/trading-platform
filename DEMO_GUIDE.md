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
mvn spring-boot:run
```

_(Wait until you see `Started TradeExecutorApplication` in the logs)._

---

## Step 5: Start the Auth Service

The Auth Service issues JWT tokens. We must start it so users can authenticate.

Open **Terminal 4** and run:

```bash
cd sprint-08-auth-service
npm run start
```

_(Wait until you see `Nest application successfully started` in the logs)._

---

## Step 6: Monitor the Kafka Streams

To visually prove that events are flowing asynchronously through the broker, we will attach consumers directly to the Kafka container.

Open **Terminal 5** (Watch Orders):

```bash
docker exec -it kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic orders
```

Open **Terminal 6** (Watch Trade Events):

```bash
docker exec -it kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic trade-events
```

---

## Step 7: Execute the Demonstration!

### The Grand Finale: What You Will See

The exact moment you press enter on that curl command:

1. You will receive an immediate `201 Created` HTTP response from the API.
2. In **Terminal 4**, an `ORDER_PLACED` event will instantly appear.
3. In **Terminal 3**, the Executor will log that it received and executed the order.
4. In **Terminal 5**, an `ORDER_FILLED` event will instantly appear!

---

## Advanced: Registering a Custom User

If you want to create your own user and place trades with it (instead of using the seeded `john_doe`), follow these steps:

### 1. Register the User on the Auth Service

First, register a new user using the Auth Service on port `3001`.

```bash
curl -s -X POST http://localhost:3001/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"new_user", "password":"password123", "email":"new_user@example.com"}'
```

_Note the `id` field in the JSON response (e.g., `4`)._

### 2. Log in as Admin

Trading accounts must be explicitly provisioned by an admin. Log in using the seeded admin credentials.

```bash
curl -s -X POST http://localhost:3001/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin", "password":"password123"}'
```

_Copy the `accessToken` from the response._

### 3. Create the Trading Account via Trade API

Finally, use the admin token to create an account for your new user's ID via the Trade API on port `8081`. Replace `<ADMIN_TOKEN>` with the token from Step 2, and `<USER_ID>` with the ID from Step 1.

```bash
curl -s -X POST http://localhost:8081/api/v1/admin/accounts \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <ADMIN_TOKEN>" \
  -d '{
    "userId": <USER_ID>,
    "currency": "USD"
  }'
```

Your new user is now ready to log in and trade!
