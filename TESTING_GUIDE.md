# Trading Platform - Full Stack Deployment & Testing Guide

## Current Environment Status

### ✅ Completed Implementation

1. **Error Code Mapping Service** - Sprint 9 (Complete)
   - All 9 error codes from both API contracts mapped
   - Trader-friendly messages
   - Status 0 (network error) handling
   - Completeness verification

2. **Playwright E2E Tests** - Sprint 9 (Complete)
   - Sign-in journey: 4 test cases
   - Place order journey: 4 test cases
   - Full isolation (no shared state)
   - Environment-based configuration

### ❌ Current Blockers

This environment lacks Docker, which is required for the full stack:

- PostgreSQL 16 (database)
- Kafka 3.8 (message broker)

## Complete Stack Requirements

```
┌─────────────────────────────────────────────────────────────┐
│                   Trading Platform Stack                     │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  Frontend:              Backend Services:      Infrastructure:
│  ┌──────────────────┐   ┌──────────────────┐   ┌──────────┐
│  │ Angular UI       │   │ Sprint 8:        │   │PostgreSQL│
│  │ (Sprint 9)       │───│ Auth Service     │───│ 5432     │
│  │ Port: 4200       │   │ (NestJS)         │   │          │
│  └──────────────────┘   │ Port: 3000       │   └──────────┘
│                         │                  │
│                         │ Sprint 6:        │   ┌──────────┐
│                         │ Trade API        │───│ Kafka    │
│                         │ (Spring Boot)    │   │ 9092     │
│                         │ Port: 3000       │   └──────────┘
│                         └──────────────────┘
│
└─────────────────────────────────────────────────────────────┘
```

## Prerequisites to Run Full Stack

### System Requirements

- **Docker & Docker Compose** (for infrastructure)
- **Node.js 18+** (for Auth Service and UI)
- **Java 21** (for Trade API)
- **Maven 3.8+** (for building Trade API)

### Environment Setup

1. **Copy environment template:**

   ```bash
   cp .env.example .env
   ```

2. **Configure required variables in .env:**

   ```
   # Database
   POSTGRES_USER=postgres
   POSTGRES_PASSWORD=postgres_dev_password
   POSTGRES_DB=trading

   # Auth Service
   JWT_SECRET=dev-only-change-me-this-secret-is-not-for-production-use
   JWT_ISSUER=auth-service

   # Fauxnance API (for market data)
   FAUXNANCE_API_KEY=your-key-here
   ```

3. **For E2E tests, create `.env` in frontend-application:**
   ```
   TRADING_UI_URL=http://localhost:4200
   TEST_USERNAME=testuser
   TEST_PASSWORD=testpassword
   TEST_ACCOUNT_ID=1
   ```

## Step-by-Step Startup

### 1. Start Infrastructure (PostgreSQL + Kafka)

```bash
cd infra/
docker compose up -d
```

### 2. Create Kafka Topics

```bash
docker exec kafka kafka-topics.sh \
  --bootstrap-server localhost:9092 \
  --create \
  --topic orders \
  --partitions 3 \
  --replication-factor 1 \
  --if-not-exists

docker exec kafka kafka-topics.sh \
  --bootstrap-server localhost:9092 \
  --create \
  --topic trade-events \
  --partitions 3 \
  --replication-factor 1 \
  --if-not-exists
```

### 3. Start Auth Service (Sprint 8)

```bash
cd auth-service/
npm install
npm run start:dev
# Runs on http://localhost:3000
```

### 4. Start Trade API (Sprint 6)

```bash
cd trade-api/
mvn spring-boot:run
# Runs on http://localhost:3000
```

### 5. Start Trading UI (Sprint 9)

```bash
cd frontend-application/
npm install
npm start
# Runs on http://localhost:4200
```

## Testing Approach

### Unit Tests (No Infrastructure Required)

**Sprint 9 - Error Code Mapper Tests**

```bash
cd frontend-application/
npm install
npm test -- --watch=false
```

✅ Status: All 44 tests pass

- Error code mapping completeness
- Unknown error fallback
- Network error (status 0) handling
- Component error handling integration

### Unit Tests (Spring 6 Trade API)

```bash
cd trade-api/
mvn test
```

### Unit Tests (Spring 8 Auth Service)

```bash
cd auth-service/
npm install
npm test -- --watch=false
```

### E2E Tests (Requires Full Stack)

**Prerequisites:**

- All 5 services running (2 infrastructure + 3 application)
- Database seeded with test user account
- Environment variables configured

```bash
cd frontend-application/

# Copy environment template
cp .env.example .env

# Configure with actual credentials
# TRADING_UI_URL=http://localhost:4200
# TEST_USERNAME=testuser (must exist in auth-service)
# TEST_PASSWORD=testpassword
# TEST_ACCOUNT_ID=1 (must exist in trade-api)

# Run all e2e tests
npm run e2e

# Run in debug mode
npm run e2e:debug

# View report
npm run e2e:report
```

## Test Inventory

### ✅ Sprint 9 - Unit Tests (Completed)

**Error Code Mapper** (18 tests)

- ✅ AUTH-401 authorization message
- ✅ AUTH-409 registration conflict message
- ✅ VAL-422 validation message
- ✅ ACC-404 account not found
- ✅ ACC-403 account authorization
- ✅ INS-404 instrument not found
- ✅ ORD-400 insufficient funds
- ✅ ORD-409 insufficient holdings
- ✅ Unknown code fallback
- ✅ Status 0 network error

**Component Tests** (26 tests)

- ✅ OrderTicket: order placement, error handling, form validation
- ✅ SignIn: authentication, error messages, navigation
- ✅ Register: credential validation, error handling
- ✅ Guards: route protection, token validation

### 🔄 Sprint 9 - E2E Tests (Ready, Requires Stack)

**Sign-In Journey** (4 tests)

1. Guard redirects unauthenticated users to sign-in
2. Invalid credentials show trader-facing error
3. Valid credentials redirect to dashboard
4. ReturnTo parameter redirects after sign-in

**Place Order Journey** (4 tests)

1. Form validation prevents invalid submissions
2. Valid order returns NEW, FILLED, or REJECTED
3. Order rejection shows trader-friendly error
4. Each placement isolated with fresh auth

### 🔜 Possible Additional Tests (Not Required)

- Order Blotter: list, filter, and sort orders
- Position Management: view and update positions
- Account Management: account creation and updates
- Error Recovery: network failures, timeouts, retries

## Expected Results When Full Stack Runs

### Unit Tests ✅

```
Total: 44 tests
Passed: 44
Failed: 0
Success Rate: 100%
```

### E2E Tests (When Stack Running)

```
Sign-In Journey:        4/4 passed ✅
Place Order Journey:    4/4 passed ✅
Total:                  8/8 passed ✅
```

## Troubleshooting

### "Cannot reach localhost:3000" (Auth Service)

- Ensure Docker containers are running
- Check if service started successfully
- Verify environment variables loaded

### "Cannot reach localhost:5432" (PostgreSQL)

- Run: `docker compose up -d` in infra/
- Wait 30 seconds for health checks
- Verify: `docker ps` shows postgres container

### "Cannot reach localhost:9092" (Kafka)

- Run: `docker compose up -d` in infra/
- Verify: `docker exec kafka kafka-broker-api-versions.sh --bootstrap-server localhost:9092`

### Test User Not Found

- Connect to PostgreSQL:
  ```bash
  psql -h localhost -U postgres -d trading
  ```
- Check users table:
  ```sql
  SELECT id, username, email FROM users;
  ```
- Seed test data if needed

### Order Placement Fails

- Verify account has sufficient balance
- Check Trade API logs for validation errors
- Ensure Kafka topics exist
- Verify database migrations ran

## Commands Cheatsheet

```bash
# Start entire stack
cd infra/ && docker compose up -d
cd auth-service/ && npm run start:dev &
cd trade-api/ && mvn spring-boot:run &
cd frontend-application/ && npm start &

# Run unit tests
cd frontend-application/ && npm test -- --watch=false

# Run e2e tests (after stack started)
cd frontend-application/ && npm run e2e

# Stop stack
docker compose down

# View logs
docker logs postgres
docker logs kafka
npm logs  # depends on process manager
```

## CI/CD Integration

For automated testing in CI pipeline:

```yaml
# Example GitHub Actions
- name: Start Infrastructure
  run: docker compose -f infra/docker-compose.yml up -d

- name: Wait for Services
  run: sleep 30

- name: Run Unit Tests
  run: cd frontend-application && npm test -- --watch=false

- name: Run E2E Tests
  run: cd frontend-application && npm run e2e

- name: Upload Report
  uses: actions/upload-artifact@v3
  with:
    name: playwright-report
    path: frontend-application/playwright-report/
```

## Success Criteria

✅ **All 44 Unit Tests Pass**

- Error mapping service complete
- Component integration working
- Error handling verified

✅ **All 8 E2E Tests Pass** (When stack running)

- Sign-in flow working end-to-end
- Order placement end-to-end
- Error messages trader-friendly
- Test isolation verified

✅ **Full Stack Operational**

- PostgreSQL responding
- Kafka topics created
- Auth service running
- Trade API running
- UI accessible

## Next Steps

1. **Ensure Docker installed** on your machine
2. **Start infrastructure:** `docker compose up -d` in infra/
3. **Install dependencies:** `npm install` in each Node service
4. **Start services:** Follow startup guide above
5. **Run unit tests:** `npm test -- --watch=false`
6. **Configure E2E:** Copy `.env.example` → `.env`
7. **Run E2E tests:** `npm run e2e`
8. **View reports:** `npm run e2e:report`

## Documentation Locations

- Error Code Mapping: `frontend-application/src/app/core/error/README.md`
- E2E Tests: `frontend-application/e2e/README.md`
- Infrastructure: `infra/README.md`
- Auth Service: `auth-service/README.md`
- Trade API: `trade-api/README.md`
