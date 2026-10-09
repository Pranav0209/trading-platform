# Order Service

Welcome to the **Order Service**! This is the primary backend gateway for user interactions regarding portfolio management, market data streaming, and order placements.

## 📖 Overview

The Order Service is a **Java Spring Boot** microservice. It serves as the API backend for the frontend applications. It receives REST requests, validates JWT tokens for security, persists user orders, and publishes messages to **Kafka** to be processed by the Executor Service.

It combines standard **Spring Data JPA** for simple CRUD operations and **MyBatis** for complex, highly-optimised SQL queries (like generating portfolio reports). It also streams market data and manages user notifications.

## 🛠️ Tech Stack

- **Framework:** Spring Boot (v3.3.5)
- **Language:** Java 21
- **Database:** PostgreSQL
- **ORM & Data:** Spring Data JPA, MyBatis
- **Messaging:** Spring Kafka
- **Security:** Spring Security, JWT (`jjwt`)
- **Documentation:** Springdoc OpenAPI (Swagger UI)
- **Observability:** Spring Boot Actuator, Spring AOP for logging
- **Testing:** JUnit 5, Testcontainers (Kafka & PostgreSQL)

## 📁 Architecture & Package Breakdown

Located under `src/main/java/com/zkuba/trading/`:

- **`controller/`**: REST APIs exposed to the frontend. Handles HTTP requests/responses and input validation.
- **`service/`**: Core business logic. Notable components include:
  - `MarketDataStreamService`: Manages live market data feeds.
  - `NotificationServiceImpl` & `NotificationListener`: Handles user alerts and notifications.
- **`repository/`**: Database access. Contains both Spring Data JPA interfaces and MyBatis mappers.
- **`security/`**: JWT validation filters and Spring Security config. Secures all endpoints.
- **`event/`**: Kafka producers. Publishes events (e.g., `OrderPlacedEvent`) to the broker.
- **`client/`**: External HTTP clients communicating with the Fauxnance API.
- **`aop/`**: Aspect-Oriented Programming classes handling cross-cutting concerns like automatic method entry/exit logging.
- **`mapper/`**: MapStruct or manual mappers converting between DTOs and internal Entities.
- **`dto/`**: Data Transfer Objects (`request` and `response`) ensuring internal entities are never leaked to the client.
- **`exception/`**: Custom application exceptions mapped to appropriate HTTP status codes via `@ControllerAdvice`.

## ⚙️ Environment Variables

Create a `.env` file in `services/order-service/` matching `.env.example`:

| Variable | Description |
|---|---|
| `DATABASE_URL` | JDBC URL for PostgreSQL |
| `DATABASE_USERNAME` | DB User |
| `DATABASE_PASSWORD` | DB Password |
| `JWT_SECRET` | Secret key for verifying JWTs created by the Auth Service |
| `FAUXNANCE_API_KEY` | API Key for external Fauxnance APIs |
| `FAUXNANCE_BASE_URL` | URL for external Fauxnance APIs |
| `KAFKA_BOOTSTRAP_SERVERS`| URL of your Kafka broker (e.g., `localhost:9092`) |
| `SERVER_PORT` | Spring Boot server port |
| `POLL_INTERVAL_SECONDS` | Interval for background syncing tasks |
| `DEFAULT_INITIAL_BALANCE` | Initial cash balance for newly onboarded accounts |
| `LOG_LEVEL` & `APP_LOG_LEVEL`| Configures standard and application-specific log verbosity |

## 🚀 Getting Started

### 1. Prerequisites
- Java 21 JDK installed
- Maven installed (`mvn`)
- A running PostgreSQL instance
- A running Kafka cluster

### 2. Build the Project
```bash
cd services/order-service
mvn clean install -DskipTests
```

### 3. Run the Application
```bash
mvn spring-boot:run
```

Once running, access the Swagger UI documentation at: `http://localhost:<SERVER_PORT>/swagger-ui.html`

## 🧪 Testing

The Order Service utilises **Testcontainers**. This means running tests will automatically spin up lightweight, throwaway Docker containers for PostgreSQL and Kafka. Make sure your Docker daemon is running!

- **Run tests:** `mvn test`
- **Coverage report:** `mvn verify`

## 🤝 Development Workflow for New Devs

1. **API Contracts**: If you modify a `controller`, update the DTOs and ensure the Swagger annotations (`@Operation`, `@ApiResponse`) remain accurate.
2. **JPA vs MyBatis**: 
   - Use **JPA** (`CrudRepository`/`JpaRepository`) for simple inserts, updates, and lookups by ID. 
   - Use **MyBatis** for complex aggregations, joining multiple tables, or bulk data extraction where JPA becomes inefficient.
3. **Security**: The `security/` package automatically intercepts requests to validate the JWT. Do not bypass this unless exposing a strictly public endpoint.
4. **Logging**: Do not clutter services with `System.out.println` or excessive loggers. The `aop/` package handles basic method tracing automatically. Use SLF4J specifically for important business milestones.
