# Trade Executor Service

Welcome to the **Trade Executor Service**! This service is the backend engine responsible for executing the actual trading operations in an event-driven architecture.

## 📖 Overview

The Executor Service is a **Java Spring Boot** application. It operates entirely asynchronously from the frontend. It listens to trade commands published to a **Kafka** topic by the Order Service, interacts with the **Fauxnance API** to validate market conditions or execute external trades, and updates its internal **PostgreSQL** database. 

It guarantees fault tolerance, retries failed executions, and acts as the "worker" node of the trading platform.

## 🛠️ Tech Stack

- **Framework:** Spring Boot (v3.3.4)
- **Language:** Java 21
- **Database:** PostgreSQL
- **ORM:** Spring Data JPA
- **Messaging:** Spring Kafka (Event Backbone)
- **Code Quality:** JaCoCo, SonarQube support
- **Testing:** JUnit 5, Mockito, Spring Boot Test

## 📁 Architecture & Package Breakdown

Located under `src/main/java/com/zkuba/executor/`, the architecture is built around event-driven domain design:

- **`listener/`**: Contains Kafka listeners (e.g., `@KafkaListener`) that consume events like `TradeCreatedEvent`. This is the primary entry point for the service.
- **`engine/`**: Contains the core logic for validating and matching trades. This is the "brain" of the executor.
- **`poller/`**: Scheduled tasks (`@Scheduled`) that poll the Fauxnance API for market data updates or check the status of pending/long-running executions.
- **`client/`**: HTTP/REST clients configured to talk to the Fauxnance API.
- **`service/`**: General business logic orchestration.
- **`repository/`**: Spring Data JPA interfaces for database access.
- **`domain/`**: 
  - `entity/`: JPA Entities mapping to PostgreSQL tables.
  - `enums/`: Standardised states (e.g., `TradeStatus.PENDING`, `TradeStatus.COMPLETED`).
- **`event/`**: POJOs representing the payloads sent and received via Kafka.

## ⚙️ Environment Variables

Create a `.env` file in `services/executor-service/` matching `.env.example`:

| Variable | Description |
|---|---|
| `DATABASE_URL` | JDBC URL for PostgreSQL |
| `DATABASE_USERNAME` | DB User |
| `DATABASE_PASSWORD` | DB Password |
| `JWT_SECRET` | Secret key (used if executor needs to validate incoming tokens or sign internal requests) |
| `FAUXNANCE_API_KEY` | API Key for external Fauxnance APIs |
| `FAUXNANCE_BASE_URL` | URL for external Fauxnance APIs |
| `KAFKA_BOOTSTRAP_SERVERS`| URL of your Kafka broker (e.g., `localhost:9092`) |
| `SERVER_PORT` | Spring Boot server port |
| `POLL_INTERVAL_SECONDS` | Frequency of the market data poller |

## 🚀 Getting Started

### 1. Prerequisites
- Java 21 JDK installed
- Maven installed (`mvn`)
- A running PostgreSQL instance
- A running Kafka cluster (Zookeeper + Kafka Broker)

### 2. Build the Project
```bash
cd services/executor-service
mvn clean install -DskipTests # Skip tests on initial build if desired
```

### 3. Run the Application
```bash
mvn spring-boot:run
```

## 🧪 Testing & Code Quality

The project has strict code coverage requirements enforced via JaCoCo.
- **Run Tests**: `mvn test`
- **Coverage Report**: `mvn verify`
  - The generated report is located at `target/site/jacoco/index.html`.
  - Certain packages (e.g., config, domain, event) are excluded from coverage metrics as defined in the `pom.xml`.

## 🤝 Development Workflow for New Devs

1. **Event Contracts**: If the payload structure of a Kafka event changes, ensure the models in the `event/` package are updated and kept in sync with the `order-service`.
2. **Idempotency**: Kafka can deliver messages more than once (at-least-once delivery). Ensure the logic in `engine/` and `service/` handles duplicate events gracefully without double-executing a trade.
3. **External Calls**: All calls to the Fauxnance API inside `client/` should handle timeouts and 5xx errors appropriately using Spring Retry.
