# Watchlists and Price Alerts - Design Document

## Overview

This document describes the design and implementation of the Watchlists and Price Alerts module for the Enterprise Trading Platform. The module allows customers to:

1. **Create and manage watchlists** - Named collections of instruments they own or are curious about
2. **Set price alerts** - Get notified when prices cross specified thresholds
3. **Monitor prices in real-time** - See live market quotes alongside watchlist instruments
4. **Manage alert state** - View alert status (ACTIVE, TRIGGERED, DORMANT) and receive delivery confirmations

## Architecture

### System Components

```
┌─────────────────────────────────────────────────────────────┐
│                    Angular Frontend                          │
│  (Watchlist UI + Price Alerts UI + Live Price Updates)     │
└────────────────────┬────────────────────────────────────────┘
                     │ REST API
                     ▼
┌─────────────────────────────────────────────────────────────┐
│                Trade REST API (Java Spring Boot)             │
│  ┌──────────────────┐  ┌──────────────────┐                │
│  │ Watchlist CRUD   │  │ Price Alert CRUD │                │
│  │ Controller       │  │ Controller       │                │
│  └─────────┬────────┘  └────────┬─────────┘                │
│            │                    │                           │
│  ┌─────────▼────────┐  ┌────────▼─────────┐                │
│  │ Watchlist        │  │ PriceAlert       │                │
│  │ Service          │  │ Service          │                │
│  └─────────┬────────┘  └────────┬─────────┘                │
│            │                    │                           │
│  ┌─────────▼──────────────────────────┐                    │
│  │      PostgreSQL Database           │                    │
│  │  ┌─────────────┐  ┌──────────────┐ │                    │
│  │  │ watchlists  │  │price_alerts  │ │                    │
│  │  │ watchlist_  │  │              │ │                    │
│  │  │ items       │  │              │ │                    │
│  │  └─────────────┘  └──────────────┘ │                    │
│  └────────────────────────────────────┘                    │
│                                                              │
│  ┌──────────────────────────────────────┐                  │
│  │   PriceAlertEvaluator (Kafka)        │                  │
│  │   - Consumes market-data topic      │                  │
│  │   - Evaluates thresholds            │                  │
│  │   - Transitions alert state         │                  │
│  │   - Delivers notifications          │                  │
│  └──────────────────────────────────────┘                  │
└─────────────────────────────────────────────────────────────┘
                     ▲         ▲
           Kafka Topic│         │
         market-data  │         │ REST call
                     │         ▼
        ┌────────────────────────────────┐
        │ Executor Service (Java)        │
        │ MarketDataPoller               │
        │ (Polls Fauxnance API)         │
        └────────────────────────────────┘

        ┌──────────────────────────────────────┐
        │ Customer Notifications Module        │
        │ (Resolves channel + sends alert)    │
        └──────────────────────────────────────┘
```

### Data Model

#### Watchlists Table
```sql
watchlists (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL,
  name VARCHAR(100) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL,
  UNIQUE(user_id, name)
)
```

#### Watchlist Items Table
```sql
watchlist_items (
  watchlist_id BIGINT NOT NULL,
  symbol VARCHAR(20) NOT NULL,
  added_at TIMESTAMPTZ NOT NULL,
  PRIMARY KEY (watchlist_id, symbol)
)
```

#### Price Alerts Table
```sql
price_alerts (
  id BIGSERIAL PRIMARY KEY,
  account_id BIGINT NOT NULL,
  symbol VARCHAR(20) NOT NULL,
  direction VARCHAR(5) NOT NULL,       -- ABOVE or BELOW
  threshold NUMERIC(19,4) NOT NULL,
  state VARCHAR(20) NOT NULL,          -- ACTIVE, TRIGGERED, DORMANT
  last_fired_at TIMESTAMPTZ,
  delivery_status VARCHAR(20),         -- PENDING, DELIVERED, FAILED
  failure_reason VARCHAR(500),
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL,
  
  -- Indexes for hot path
  INDEX (account_id, state),           -- For alert filtering
  INDEX (symbol, state)                -- For market-data consumer
)
```

## API Contract

See `contracts/watchlist-alerts-api.yaml` for the complete OpenAPI 3.1 specification.

### Key Endpoints

**Watchlists:**
- `GET /api/v1/accounts/{accountId}/watchlists` - List all watchlists
- `POST /api/v1/accounts/{accountId}/watchlists` - Create watchlist
- `GET /api/v1/accounts/{accountId}/watchlists/{watchlistId}` - Get watchlist with live prices
- `DELETE /api/v1/accounts/{accountId}/watchlists/{watchlistId}` - Delete watchlist
- `POST /api/v1/accounts/{accountId}/watchlists/{watchlistId}/instruments/{symbol}` - Add instrument
- `DELETE /api/v1/accounts/{accountId}/watchlists/{watchlistId}/instruments/{symbol}` - Remove instrument

**Price Alerts:**
- `GET /api/v1/accounts/{accountId}/alerts` - List all alerts (optional state filter)
- `POST /api/v1/accounts/{accountId}/alerts` - Create alert
- `GET /api/v1/accounts/{accountId}/alerts/{alertId}` - Get alert details
- `DELETE /api/v1/accounts/{accountId}/alerts/{alertId}` - Delete alert
- `POST /api/v1/accounts/{accountId}/alerts/{alertId}/reset` - Reset triggered alert

## Design Decisions

### 1. Alert Lifecycle: ACTIVE → TRIGGERED → Reset to ACTIVE

**Decision**: Alerts transition from `ACTIVE` (monitoring) to `TRIGGERED` (fired, awaiting reset) back to `ACTIVE`, skipping the `DORMANT` state in this iteration.

**Rationale**:
- Prevents "alert fatigue" when price oscillates around a level (e.g., bouncing on 150.00)
- Gives customer control: they see the alert fired and explicitly reset it when ready
- Simpler state machine than continuous re-firing
- `DORMANT` state reserved for future use (e.g., snooze functionality)

### 2. Threshold Crossing: Directional (ABOVE/BELOW)

**Decision**: 
- `ABOVE`: Alert fires when price **rises through** the threshold (price >= threshold)
- `BELOW`: Alert fires when price **falls through** the threshold (price <= threshold)

**Rationale**:
- Intuitive semantics: "Alert me when AAPL goes above 150" means the price rose to that level
- Avoids ambiguity of "crossing" (could mean either direction)
- Matches industry practice (e.g., "break above", "break below")
- Non-directional "crosses" can be built as two alerts if needed

### 3. Alert Delivery Through Notifications Module

**Decision**: Alerts are delivered in-process through the Customer Notifications module's REST interface, not published to Kafka.

**Rationale**:
- Reduces system coupling: watchlist alerts don't own delivery logic
- Notifications module already resolves channel from preferences
- Prevents two places on platform deciding how to reach customers
- If delivery fails, alert state still reflects TRIGGERED (customer sees it)
- In-process call won't timeout; failures are logged and recoverable

**Failure Handling**:
When notification delivery fails (channel unknown, provider error, etc.):
1. Alert state transitions to TRIGGERED (as if delivered)
2. `deliveryStatus` set to FAILED with reason in `failureReason` field
3. Customer sees alert state and can investigate settings
4. No retry loop; customer initiates reset after fixing channel

### 4. Per-Account Alert Limit: 100

**Decision**: Hard limit of 100 active + triggered alerts per account.

**Rationale**:
- Prevents one account from filling the alert table
- Protects market-data consumer from becoming the bottleneck (every quote checks alerts)
- Enforced with `countActiveAlertsForAccount()` query returning 409 Conflict when exceeded
- Soft warning in UI: shows progress bar and count
- Customer can delete old alerts to create new ones

**Trade-off**: Customer with legitimate use case (monitoring 200+ symbols) hits limit but can still create watchlists.

### 5. Hot-Path Indexing: (symbol, state)

**Decision**: `price_alerts` table indexed on `(symbol, state)` for market-data consumer.

**Rationale**:
- Market-data consumer queries: "Are there ACTIVE or TRIGGERED alerts for this symbol?"
- Query: `SELECT * FROM price_alerts WHERE symbol = ? AND state IN ('ACTIVE', 'TRIGGERED')`
- Without index: full table scan on every quote (6+ quotes/second × 120+ trading symbols = expensive)
- With index: O(log n) lookup per symbol per quote
- Secondary indexes on `(account_id, state)` and `account_id` for API queries

### 6. Watchlist Symbols Include Alerts

**Decision**: Market-data poller also polls symbols with active price alerts.

**Rationale**:
- Ensures alert evaluation has fresh prices
- No separate polling loop needed
- Query: `SELECT symbol FROM positions UNION SELECT symbol FROM watchlist_items UNION SELECT symbol FROM price_alerts WHERE state IN ('ACTIVE', 'TRIGGERED')`

### 7. Access Control: accountId Claim Verification

**Decision**: Every route compares JWT `accountId` claim against resource `accountId` path parameter.

**Rationale**:
- Bearer token proves who you are; claim proves what you may reach
- Prevents horizontal escalation: can't query another customer's watchlists
- Implementation: `verifyAuthorization(user.accountId(), resourceAccountId)`
- Returns 403 Forbidden if mismatch

### 8. Frontend: Standalone Angular Components

**Decision**: Watchlist and Price Alerts built as standalone Angular components.

**Rationale**:
- Modern Angular 14+ approach (no NgModule coupling)
- Reusable in different layouts
- Easy to test and integrate
- Can be lazy-loaded if needed

## Acceptance Criteria Met

✅ **Watchlists**: Customers can create and add instruments (owned or watched)
✅ **Live Prices**: Watchlist displays current quotes from market-data stream
✅ **Price Alerts**: Create, view, and manage with threshold + direction
✅ **Alert State**: Visible to customer (ACTIVE/TRIGGERED/DORMANT)
✅ **Delivery**: Alerts delivered through Customer Notifications module
✅ **Access Control**: JWT accountId claim verified on all routes
✅ **Per-Account Cap**: 100 active/triggered alerts enforced
✅ **Error Handling**: Delivery failures logged; alert state independent of outcome

## Known Limitations and Future Work

1. **Push Channel Not Implemented**: Alert delivery to browser push channel is not implemented (EMAIL only)
2. **Dormant State Unused**: DORMANT state in enum but not used; reserved for snooze feature
3. **Percentage-Move Alerts Out of Scope**: Only absolute price thresholds supported
4. **Alert History**: No per-delivery audit trail; only last fire time recorded
5. **No Bulk Operations**: Can't create 10 alerts in one request; must POST each individually
6. **Live Price Push**: Watchlist prices refreshed only when component reloads; could use WebSocket for push

## Testing Strategy

### Unit Tests
- `PriceAlertServiceTest`: 13 tests covering CRUD, validation, limits, authorization
- Mock repositories and dependencies
- Test happy paths and error cases

### Integration Tests (Future)
- End-to-end: Create alert → Publish quote → Evaluate → Deliver
- Use TestContainers for PostgreSQL and Kafka
- Verify alert state transitions and delivery outcomes

### Manual Testing
1. Create watchlist and add instruments
2. Verify live prices display (refresh every 45 seconds from poller)
3. Create price alert (above and below)
4. Monitor alert count against limit
5. Publish test quote crossing threshold
6. Verify alert transitions to TRIGGERED and notification sent
7. Reset alert and verify returns to ACTIVE

## Deployment Considerations

### Configuration
```properties
# spring-boot:application.properties
poll.interval.seconds=60
emailjs.service-id=...
emailjs.template-id=...
emailjs.public-key=...
emailjs.private-key=...
```

### Migration
1. Run `Databases/PostgreSQL/apply.sh` to create tables
2. Deploy updated Trade API service (with new Controller + Service)
3. Restart Executor service (updated PositionRepository query)
4. Deploy updated Frontend (new components)

### Monitoring
- Alert: Consumer lag on `price-alert-service` group
- Metric: `price_alerts_evaluated` counter (per symbol)
- Metric: `alerts_triggered` counter
- Metric: `alert_delivery_failures` counter

## References

- OpenAPI Contract: `contracts/watchlist-alerts-api.yaml`
- Database Schema: `Databases/PostgreSQL/init/03-price-alerts.sql`
- Backend: `services/order-service/src/main/java/com/zkuba/trading/`
- Frontend: `Frontend/frontend-application/src/app/core/trade/`
- Notifications: `0002-emailjs-for-notifications.md`, `0003-kafka-topics-for-notifications.md`
- Market Data: `contracts/kafka-topics.md` (market-data topic schema)
