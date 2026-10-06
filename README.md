# Kafka Event-Driven Flow: Complete Demonstration Guide

This guide walks you through setting up and demonstrating the full asynchronous trading flow across the **Order Service**, **Event Backbone Executor**, and the **Frontend Application**, entirely from scratch.

By following this guide, you will observe the complete lifecycle of a trade event:
`HTTP Request -> Order Service -> Kafka (orders) -> Executor -> Kafka (trade-events)`

## Prerequisites

1. **Clean Environment**: Make sure no other instances of the applications are currently running (kill them in your terminal or IDE).
2. **Ports**: Ensure ports `8081` (Order Service), `8082` (Executor), `5432` (PostgreSQL), `9092` (Kafka), `3000` (Auth Service), and `4200` (Frontend) are free.

---

## Step 1: Wipe and Recreate Infrastructure

We have updated the Docker Compose file to use the default `trading` database with the `postgres` user, so you no longer need to pass environment variables when starting the Java apps!

Open **Terminal 1** and run:

```bash
cd Infrastructure/kafka/scripts
docker-compose down -v
docker-compose up -d
```

_(Wait 10-15 seconds for PostgreSQL and Kafka to fully initialize)._

---

## Step 2: Create Kafka Topics

Kafka does not automatically create topics in this project. You must run the provisioning script.

Still in **Terminal 1**, run:

```bash
./create-topics.sh
```

You should see `orders`, `trade-events`, `market-data`, and their `DLT` counterparts created successfully.

---

## Step 3: Start the Order Service (Trade API)

Open **Terminal 2** and run:

```bash
cd services/order-service
mvn spring-boot:run
```

_(Wait until you see the application has successfully started in the logs)._

---

## Step 4: Start the Event Backbone Executor

The Executor consumes from the `orders` topic and publishes to the `trade-events` topic. It also polls live market data.

Open **Terminal 3** and run:

```bash
cd services/executor-service
mvn spring-boot:run
```

_(Wait until you see the application has successfully started in the logs)._

---

## Step 5: Start the Auth Service

The Auth Service issues JWT tokens. We must start it so users can authenticate.

Open **Terminal 4** and run:

```bash
cd services/auth-service
npm run start
```

_(Wait until you see the application successfully started in the logs)._

---

## Step 6: Start the Frontend Application

Open **Terminal 5** and run:

```bash
cd Frontend/frontend-application
npm run start
```

_(Wait until the frontend compiles and starts, usually accessible at http://localhost:4200)._

---

## Step 7: Monitor the Kafka Streams

To visually prove that events are flowing asynchronously through the broker, we can attach consumers directly to the Kafka container.

Open **Terminal 6** (Watch Orders):

```bash
docker exec -it kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic orders
```

Open **Terminal 7** (Watch Trade Events):

```bash
docker exec -it kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic trade-events
```

---

## Step 8: Access the Application

Once all services are running, you can access the full trading application from your browser!

1. Open your browser and navigate to the frontend URL (e.g., `http://localhost:4200`).
2. Log in using the available test credentials or register a new user.
3. Start placing trades and watch them execute in real-time through the event-driven backbone!
