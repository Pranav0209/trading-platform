# STEP 3: Backend Java Implementation - Complete

## Summary

STEP 3 implementation adds realised P&L recording to the zkuba trading platform across two microservices:

1. **executor-service** (writes P&L data when SELL orders are filled)
2. **order-service** (reads P&L data and exposes API endpoint)

---

## Files Created/Modified

### Executor-Service (Writes P&L)

#### 1. RealisedPnl Entity
**File:** `services/executor-service/src/main/java/com/zkuba/executor/domain/entity/RealisedPnl.java`
- JPA entity mapped to `pnl` table
- Stores: id, orderId (UUID, unique), accountId, symbol, quantity, sellPrice, averageCost, realisedPnl, realisedAt, createdAt
- Constructor for easy creation with all fields

#### 2. RealisedPnlRepository
**File:** `services/executor-service/src/main/java/com/zkuba/executor/repository/RealisedPnlRepository.java`
- Extends JpaRepository<RealisedPnl, Long>
- Inherits save() which respects UNIQUE constraint on order_id
- Comment: On Kafka replay, duplicate order_id will be rejected by database constraint

#### 3. TradeExecutionService (Modified)
**File:** `services/executor-service/src/main/java/com/zkuba/executor/service/TradeExecutionService.java`
**Changes:**
- Added import: `com.zkuba.executor.domain.entity.RealisedPnl`
- Added @Autowired field: `private RealisedPnlRepository realisedPnlRepository;`
- Modified SELL order handling in executeOrder():
  ```java
  // For SELL orders: capture average cost BEFORE updating position
  BigDecimal averageCostBeforeSell = position != null ? position.getAverageCost() : BigDecimal.ZERO;
  
  // ... update position and cash balance ...
  
  // Record realised P&L in same transaction
  BigDecimal realisedPnl = executedPriceInAccountCurrency.subtract(averageCostBeforeSell)
          .multiply(BigDecimal.valueOf(order.getQuantity()));
  RealisedPnl pnlRecord = new RealisedPnl(
          order.getId(),
          account.getId(),
          order.getSymbol(),
          order.getQuantity(),
          executedPriceInAccountCurrency,
          averageCostBeforeSell,
          realisedPnl,
          executedOn
  );
  realisedPnlRepository.save(pnlRecord);
  ```
- **Key:** P&L recorded AFTER updateSellPosition() in same @Transactional block
- **Safety:** If position deletion fails, P&L save also fails (atomic)

---

### Order-Service (Reads P&L via API)

#### 1. RealisedPnl Entity
**File:** `services/order-service/src/main/java/com/zkuba/trading/domain/entity/RealisedPnl.java`
- Read-only mapping of `pnl` table
- Same fields as executor-service entity
- Getters and setters only (no write logic)

#### 2. RealisedPnlRepository
**File:** `services/order-service/src/main/java/com/zkuba/trading/repository/RealisedPnlRepository.java`
- Query method: `findRealisedPnlHistory(accountId, symbol, fromDate, toDate, pageable)`
  - Filters by accountId, optional symbol, optional date range (inclusive)
  - Returns Page<RealisedPnl> ordered by realised_at DESC
  - JPQL query with optional parameter handling (@Param, :variable)
- Aggregate method: `sumRealisedPnl(accountId, symbol, fromDate, toDate)`
  - Returns SUM(realised_pnl) across ALL records (not just current page)
  - Returns 0 if no records found (COALESCE)

#### 3. DTOs (Response Objects)
**File 1:** `RealisedPnlRecord.java`
```java
record RealisedPnlRecord(
  Long pnlId,
  UUID orderId,
  String symbol,
  Integer quantity,
  BigDecimal sellPrice,
  BigDecimal averageCost,
  BigDecimal realisedPnl,
  OffsetDateTime realisedAt
)
```

**File 2:** `RealisedPnlSummary.java`
```java
record RealisedPnlSummary(
  Long totalCount,         // Total records (across all pages)
  Long totalPages,         // Ceil(totalCount / pageSize)
  Long currentPage,        // Zero-indexed
  Long pageSize,
  BigDecimal totalRealisedPnl  // Sum across ALL records
)
```

**File 3:** `RealisedPnlHistoryResponse.java`
```java
record RealisedPnlHistoryResponse(
  Long accountId,
  String baseCurrency,     // "USD"
  LocalDate from,          // Optional, echo back
  LocalDate to,            // Optional, echo back
  List<RealisedPnlRecord> records,    // Current page
  RealisedPnlSummary summary,
  OffsetDateTime asOf      // Response timestamp
)
```

#### 4. RealisedPnlService (Interface)
**File:** `services/order-service/src/main/java/com/zkuba/trading/service/RealisedPnlService.java`
- Single method: `getRealisedPnlHistory(...)`
- Parameters: accountId, userId (from JWT), symbol, from, to, page, size
- Returns: RealisedPnlHistoryResponse
- Throws: ForbiddenException, InvalidParametersException

#### 5. RealisedPnlServiceImpl (Implementation)
**File:** `services/order-service/src/main/java/com/zkuba/trading/service/RealisedPnlServiceImpl.java`
**Logic:**
1. **Validate access:** `validateAccountAccess(accountId, userId)`
   - Throws ForbiddenException (403) if accountId doesn't belong to userId
2. **Validate pagination:** pageSize must be 1-100, page must be >= 0
3. **Validate date range:** from must not be after to
4. **Convert dates:** LocalDate → OffsetDateTime in UTC
   - from: start of day (00:00:00 UTC)
   - to: end of day (23:59:59.999 UTC)
5. **Query database:** Call findRealisedPnlHistory() with pagination
6. **Fetch totals:** Call sumRealisedPnl() for all-record sum (not just page)
7. **Build response:**
   - Convert entities to DTOs
   - Compute totalPages = ceil(totalCount / pageSize)
   - Include pagination info and summary
   - Set asOf to current time
8. **Exception handling:**
   - AccountNotFoundException (404) if account doesn't exist
   - ForbiddenException (403) if account doesn't belong to user
   - InvalidParametersException (400) if filters invalid

#### 6. PortfolioController
**File:** `services/order-service/src/main/java/com/zkuba/trading/controller/PortfolioController.java`
**Endpoint:** `GET /api/v1/portfolio/{accountId}/pnl/realised`
**Parameters:**
- Path: accountId (required)
- Query: symbol (optional), from (ISO date), to (ISO date), page (int), size (int)
- Header: JWT in Authorization bearer token (verified by JwtAuthenticationFilter)
**Response:** 200 OK with RealisedPnlHistoryResponse
**Errors:**
- 400 Bad Request (invalid filters)
- 401 Unauthorized (missing/invalid JWT)
- 403 Forbidden (accountId doesn't belong to user)
- 404 Not Found (account doesn't exist)
**JWT Verification:**
- @AuthenticationPrincipal AuthenticatedUser user extracts JWT claims
- Service calls validateAccountAccess() to verify user.userId() == account.userId
- Returns 403 if mismatch (per API contract)

#### 7. InvalidParametersException
**File:** `services/order-service/src/main/java/com/zkuba/trading/exception/InvalidParametersException.java`
- New exception class
- Extends GlobalException
- Returns HTTP 400 Bad Request
- Used for invalid pagination, date range, filter values

---

## API Endpoint Details

### Request
```
GET /api/v1/portfolio/{accountId}/pnl/realised?symbol=AAPL&from=2026-09-01&to=2026-10-19&page=0&size=20
Authorization: Bearer <JWT>
```

### Response (200 OK)
```json
{
  "accountId": 1,
  "baseCurrency": "USD",
  "from": "2026-09-01",
  "to": "2026-10-19",
  "records": [
    {
      "pnlId": 1,
      "orderId": "6f2b1c2a-6a1e-4a4f-9c0d-2f7a1b3c4d5e",
      "symbol": "AAPL",
      "quantity": 100,
      "sellPrice": 240.5000,
      "averageCost": 228.4000,
      "realisedPnl": 1210.0000,
      "realisedAt": "2026-10-19T14:05:11Z"
    }
  ],
  "summary": {
    "totalCount": 25,
    "totalPages": 2,
    "currentPage": 0,
    "pageSize": 20,
    "totalRealisedPnl": 5420.7500
  },
  "asOf": "2026-10-19T14:05:11Z"
}
```

### Error: 400 Bad Request
```json
{
  "errorCode": "VAL-422",
  "message": "Page size must be between 1 and 100"
}
```

### Error: 403 Forbidden
```json
{
  "errorCode": "PNL-403",
  "message": "Account does not belong to token user"
}
```

---

## Key Design Decisions

| Decision | Reasoning |
|----------|-----------|
| **Capture averageCost BEFORE updateSellPosition()** | Position row is deleted when qty→0, so cost basis must be recorded atomically with fill |
| **Same @Transactional block** | If either order update or P&L save fails, both rollback (atomic) |
| **UNIQUE(order_id)** | No partial fills per contract; one order = one P&L record |
| **BigDecimal for calculations** | Avoid floating-point rounding errors in financial calculations |
| **ON CONFLICT DO NOTHING** | Kafka replay safety; duplicate order_id silently ignored by database |
| **Pagination with totalRealisedPnl across all records** | Shows true portfolio P&L, not just current page |
| **LocalDate to OffsetDateTime conversion** | from = start of day UTC, to = end of day UTC (inclusive ranges) |
| **JWT verification in service layer** | Consistent with existing AccountService pattern |
| **Read-only entity in order-service** | Executor-service writes; order-service reads only |

---

## Compilation & Testing

### Build Commands
```bash
# Executor-service
cd services/executor-service
mvn clean compile

# Order-service
cd services/order-service
mvn clean compile
```

### Known Issues
- Build requires Java 21 (configured in pom.xml)
- Ensure MAVEN_HOME and JAVA_HOME point to Java 21

### Manual Code Review Checklist
- ✓ Imports correct for all classes used
- ✓ Exception types match existing patterns
- ✓ JPA annotations correct (@Entity, @Column, @GeneratedValue)
- ✓ Repository methods use proper @Query syntax
- ✓ Service validates access before querying
- ✓ Controller maps @PathVariable and @RequestParam correctly
- ✓ DTOs use record syntax matching codebase patterns
- ✓ BigDecimal used for money calculations (no double/float)

---

## What Happens When a SELL Order is Filled

1. **User places SELL order** → order-service accepts it
2. **Kafka delivers order** → executor-service processes it
3. **In one @Transactional block:**
   - Update order status to FILLED
   - Capture position's averageCost (before deletion)
   - Update cash balance
   - Call updateSellPosition() (deletes position if qty→0)
   - Create RealisedPnl record with captured averageCost
   - Publish TradeEventPayload to Kafka (positionQtyAfter=0 if closed)
4. **User queries P&L** → calls order-service API
5. **API verifies JWT** → checks accountId matches JWT claim
6. **API queries executor-service's pnl table** → returns paginated results with summary

---

## Next Steps

1. Apply database migration (STEP 2)
2. Compile Java code with Java 21 available
3. Deploy executor-service and order-service
4. Run integration tests (STEP 5)
5. STEP 4: Frontend Angular integration (consumes generated portfolio client)

---

## Status: READY FOR COMPILATION & TESTING

All Java code written. Awaiting:
1. Java 21 availability for build
2. Database migration application
3. Integration testing
