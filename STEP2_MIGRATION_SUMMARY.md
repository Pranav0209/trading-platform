# STEP 2: Database Migration - Final Corrected SQL & Test Plan

## 1. Git Status - All Clean

**Modified files:**
```
M  Frontend/frontend-application/package.json
M  contracts/portfolio-api.yaml
```

**New files (untracked):**
```
?? Databases/PostgresSQL/init/03-realised-pnl-table.sql
?? Databases/PostgresSQL/init/test-pnl-backfill.sql
?? Frontend/frontend-application/openapi/portfolio-client.json
?? Frontend/frontend-application/src/generated/portfolio/
```

**Verified:**
- ✓ src/generated/auth/ UNCHANGED (line ending issue reverted)
- ✓ src/generated/trade/ UNCHANGED (line ending issue reverted, still uses trade-api.yaml)
- ✓ trade-client.json CORRECT (capital C "Contracts/trade-api.yaml")
- ✓ Temporary diff files cleaned up

---

## 2. Partial Fills Question - UNIQUE(order_id) is CORRECT

**Contract Line (trade-api.yaml:715):**
> "There is no partial-fill state, so the Trade Executor fills an order in full or rejects it."

**Code Evidence (TradeExecutionService.executeOrder):**
- Fills order completely in one atomic transaction
- No API for partial fills
- Order status transitions directly: NEW → FILLED (entire quantity) or REJECTED/CANCELLED

**Conclusion:** ✓ UNIQUE(order_id) is correct. Each order has at most one P&L record.

---

## 3. Corrected Table Definition

**File:** `Databases/PostgresSQL/init/03-realised-pnl-table.sql`

```sql
CREATE TABLE IF NOT EXISTS pnl (
    id BIGSERIAL PRIMARY KEY,
    order_id UUID NOT NULL UNIQUE REFERENCES orders(id),
    account_id BIGINT NOT NULL REFERENCES accounts(id),
    symbol VARCHAR(20) NOT NULL REFERENCES instruments(symbol),
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    sell_price NUMERIC(19,4) NOT NULL,
    average_cost NUMERIC(19,4) NOT NULL CHECK (average_cost >= 0),
    realised_pnl NUMERIC(19,4) NOT NULL,
    realised_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_pnl_account_realised_at 
    ON pnl(account_id, realised_at DESC);
```

**Changes from previous version:**
- ✓ Added REFERENCES clauses for account_id and symbol (FKs now explicit)
- ✓ order_id now has FK REFERENCES orders(id)
- ✓ Removed redundant idx_pnl_order_id (UNIQUE constraint creates index automatically)
- ✓ Kept idx_pnl_account_realised_at for pagination queries

---

## 4. Corrected Backfill Logic - Recursive CTE

**Problem Fixed:**
The previous version accumulated all BUY costs without resetting when a position closes. This violates weighted average cost rules.

**Example Bug:**
```
BUY 10 @ $100
SELL 10 @ $130  (position closes, P&L = $300)
BUY 10 @ $200   (fresh position, avg should be $200)
SELL 10 @ $210  (P&L should be $100)

OLD LOGIC: Would accumulate BUY costs as: (100*10 + 200*10) = $3000
           avg_cost = 3000/20 = $150 ✗ WRONG
           P&L = (210 - 150)*10 = $600 ✗ WRONG

NEW LOGIC: After SELL closes position, avg resets to 0
           Next BUY: avg = 200
           P&L = (210 - 200)*10 = $100 ✓ CORRECT
```

**Algorithm (Recursive CTE - position_tracker):**
```
1. Base case: For each (account_id, symbol), initialize held_qty=0, avg_cost=0

2. For each fill in time order:
   
   IF side = 'BUY':
       new_held_qty = old_held_qty + quantity
       new_avg_cost = (old_avg_cost * old_held_qty + executed_price * quantity) / (old_held_qty + quantity)
   
   ELSE IF side = 'SELL':
       Record P&L using OLD avg_cost (from position BEFORE this sell)
       new_held_qty = old_held_qty - quantity
       
       IF new_held_qty == 0:
           new_avg_cost = 0  ← KEY: Position closed, reset average
       ELSE:
           new_avg_cost = old_avg_cost  ← Average unchanged on partial sell

3. Tie-breaking (deterministic order):
   - Primary: executed_on ASC
   - Secondary: created_at ASC  
   - Tertiary: order id ASC

4. Filter output: Only SELL orders where held_qty >= quantity
   (Skip invalid SELLs that would go negative)

5. Idempotency: ON CONFLICT (order_id) DO NOTHING
```

**Key Insight:** The position_tracker CTE maintains the state AFTER each fill. When we join to get the state BEFORE a SELL, we use `last_fill_seq = fill_seq - 1`. This gives us the average_cost from the previous iteration, which is exactly what we need for the P&L calculation.

---

## 5. Test Cases

**File:** `Databases/PostgresSQL/init/test-pnl-backfill.sql`

### Case A: Partial Sell After Two Buys
```
BUY  10 @ $100  (avg = $100, qty = 10)
BUY  10 @ $120  (avg = $110, qty = 20)
SELL 5 @ $130   (P&L = (130-110)*5 = $100) ✓
```
Expected: realised_pnl = 100.0000

### Case B: Complete Sell (Position Closes)
```
BUY  10 @ $100  (avg = $100, qty = 10)
SELL 10 @ $130  (P&L = (130-100)*10 = $300, avg resets to 0) ✓
```
Expected: realised_pnl = 300.0000

### Case C: Sell, Close Position, Buy Fresh, Sell Again
```
BUY  10 @ $100  (avg = $100, qty = 10)
SELL 10 @ $130  (P&L = $300, position closes, avg = 0)
BUY  10 @ $200  (avg = $200, qty = 10)
SELL 10 @ $210  (P&L = (210-200)*10 = $100) ✓  ← Tests reset logic
```
Expected: 
- First SELL: realised_pnl = 300.0000
- Second SELL: realised_pnl = 100.0000

**Why Case C is Critical:**
It catches the cumulative-sum bug where all prior BUY costs are accumulated without resetting. If the old logic were used, the second SELL would calculate an incorrect average based on ALL historical BUYs, not just the current position.

---

## 6. How to Run

### Step 1: Apply Migration to Existing Database

```bash
# From command line (using psql):
psql -h localhost -U postgres -d zkuba_trading < Databases/PostgresSQL/init/03-realised-pnl-table.sql
```

**Alternative (if password needed):**
```bash
psql -h localhost -U postgres -d zkuba_trading -W < Databases/PostgresSQL/init/03-realised-pnl-table.sql
```

**Alternative (from within psql interactive):**
```sql
\i Databases/PostgresSQL/init/03-realised-pnl-table.sql
```

**Note:** The init/ folder is for fresh database setup only. To apply to an EXISTING database:
- Use the psql command above, OR
- Copy the SQL from 03-realised-pnl-table.sql and paste into your psql session

### Step 2: Verify Migration Applied Successfully

```sql
-- Check table exists
\dt pnl

-- Check indexes created
\di idx_pnl*

-- Count backfilled P&L records (if you have existing FILLED orders)
SELECT COUNT(*) FROM pnl;

-- Example: Check P&L for a specific account
SELECT * FROM pnl WHERE account_id = YOUR_ACCOUNT_ID ORDER BY realised_at DESC LIMIT 10;
```

### Step 3: Run Test Cases (Optional but Recommended)

```bash
# Run full test with validation
psql -h localhost -U postgres -d zkuba_trading < Databases/PostgresSQL/init/test-pnl-backfill.sql
```

**Test output will show:**
- 3 test cases (A, B, C) with their expected P&L values
- Validation results (✓ PASS or ✗ FAIL with explanation)
- Summary of total rows inserted

### Step 4: Cleanup (After Testing)

```sql
-- Delete test data only
DELETE FROM pnl WHERE account_id IN (999, 998, 997);
DELETE FROM orders WHERE account_id IN (999, 998, 997);
DELETE FROM accounts WHERE id IN (999, 998, 997);
DELETE FROM auth.users WHERE id IN (999, 998, 997);
```

---

## 7. Key Safety Features

| Feature | How It Works | Why It Matters |
|---------|--------------|-----------------|
| **UNIQUE(order_id)** | One P&L record per SELL order | Prevents double-counting fills |
| **ON CONFLICT DO NOTHING** | Kafka replays are silently ignored | Safe to replay same fill multiple times |
| **Recursive CTE with position_tracker** | Replays order sequence with running state | Correctly resets avg_cost when position closes |
| **Tie-breaking (executed_on, created_at, id)** | Deterministic ordering | Consistent results across replays |
| **CHECK (quantity > 0)** | Only positive quantities | Prevents invalid P&L records |
| **CHECK (average_cost >= 0)** | Non-negative costs | Prevents calculation errors |
| **(account_id, realised_at DESC) index** | Fast pagination queries | Portfolio API queries are efficient |

---

## 8. Next Steps

1. **User reviews and approves:** SQL migration, test cases, and execution plan
2. **User applies migration:** Run psql command against existing database
3. **Optional: Run tests** to validate backfill logic
4. **Then: Proceed to STEP 3** (Java implementation in executor-service and order-service)

---

## Status: WAITING FOR APPROVAL

Ready to apply? Respond with confirmation and I will NOT start STEP 3 until you've applied the migration and verified it works.
