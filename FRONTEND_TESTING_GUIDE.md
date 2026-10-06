# Frontend P&L Testing Guide - Mock Data Mode

## Overview

The P&L component has been configured with a **mock data service** for testing without needing real backend APIs, databases, or Kafka topics. This allows you to test all UI features and user flows locally.

---

## Setup & Activation

### Current Status
✅ Mock data is **ENABLED by default** in `src/environments/environment.ts`:
```typescript
useMockPnlData: true  // Set to false to use real API
```

### How to Toggle
Edit `src/environments/environment.ts`:
```typescript
// Use mock data (for testing)
useMockPnlData: true

// Use real API (production)
useMockPnlData: false
```

### Start Testing
```bash
cd Frontend/frontend-application
ng serve
```

Then navigate to:
- Dashboard: `http://localhost:4200/dashboard`
- P&L Full Page: `http://localhost:4200/realised-pnl` or `http://localhost:4200/pnl`

---

## Mock Data Overview

### 8 Test Transactions Included

| # | Symbol | Side | Qty | Sell Price | Avg Cost | P&L | Status | Scenario |
|---|--------|------|-----|-----------|----------|-----|--------|----------|
| 1 | INFY.NS | SELL | 10 | 2510.00 | 2500.00 | **+100.00** 🟢 | PROFIT | Basic profit |
| 2 | TCS.NS | SELL | 5 | 3900.00 | 4000.00 | **-500.00** 🔴 | LOSS | Basic loss |
| 3 | RELIANCE.NS | SELL | 2 | 2750.00 | 2750.00 | **0.00** ⚪ | BREAK-EVEN | Zero P&L |
| 4 | HDFCBANK.NS | SELL | 20 | 1805.50 | 1750.00 | **+1110.00** 🟢 | LARGE PROFIT | High value trade |
| 5 | WIPRO.NS | SELL | 15 | 380.00 | 425.00 | **-675.00** 🔴 | LARGE LOSS | Significant loss |
| 6 | MARUTI.NS | SELL | 3 | 10150.25 | 10145.75 | **+13.50** 🟢 | SMALL PROFIT | Low precision test |
| 7 | INFY.NS | SELL | 8 | 2520.00 | 2510.00 | **+80.00** 🟢 | DUPLICATE SYMBOL | Same ticker filtering |
| 8 | SUNPHARMA.NS | SELL | 7 | 752.3456 | 748.1234 | **+29.4556** 🟢 | HIGH PRECISION | 4 decimal test |

**Total P&L Across All Records:** **+89.0056**

---

## Test Cases & Expected Behavior

### TEST 1: Initial Load (Dashboard Card Mode)
**Action:** 
1. Start Angular app with mock data enabled
2. Login to dashboard
3. Wait for RealisedPnlComponent to load

**Expected Behavior:**
- ✓ Component loads within 1 second
- ✓ Shows "Recent Realised P&L" title (from dashboard card mode)
- ✓ Displays first 20 records (default page size)
- ✓ Shows total P&L: **+89.0056** in green (🟢 PROFIT)
- ✓ Shows "Total Records: 8"
- ✓ All 8 transactions visible in table (fits in default page size)
- ✓ "View all" link present (if enabled)
- ✓ No loading spinner (data cached)
- ✓ No error messages

**Verify:**
```
Dashboard → Scroll down → See "Recent Realised P&L" card
Expected: 
  Total P&L: +89.0056 USD 🟢
  Total Records: 8
  Table rows: 8 visible
```

---

### TEST 2: Standalone Page Mode
**Action:**
1. Click "P&L History" in sidebar navigation
2. Wait for page to load

**Expected Behavior:**
- ✓ Page title: "Your Realised P&L" (standalone mode header)
- ✓ Subtitle: "Transaction-by-transaction history..."
- ✓ Filter section visible (symbol, from/to date inputs, Apply/Reset buttons)
- ✓ All 8 records displayed in table
- ✓ Pagination shows: "Page 1 of 1" (all fit in default size)
- ✓ Total P&L: **+89.0056** 🟢
- ✓ Total Records: 8

**Verify:**
```
Navigate: Sidebar → "P&L History"
Expected:
  - Page header: "Your Realised P&L"
  - Filter inputs visible (not in card mode)
  - Total: +89.0056 USD 🟢
  - 8 rows in table
  - Pagination: "Page 1 of 1"
```

---

### TEST 3: Color Coding (Profit/Loss/Break-Even)
**Action:**
1. In standalone page, look at P&L column values
2. Verify colors match value signs

**Expected Behavior:**
- ✓ Row 1 (INFY.NS, +100): Green text **🟢 +100.00**
- ✓ Row 2 (TCS.NS, -500): Red text **🔴 -500.00**
- ✓ Row 3 (RELIANCE.NS, 0): Gray text **⚪ 0.00**
- ✓ Row 4 (HDFCBANK.NS, +1110): Green text **🟢 +1,110.00** (formatted with comma)
- ✓ Row 5 (WIPRO.NS, -675): Red text **🔴 -675.00**

**Verify:**
```
Table → P&L Column
Expected colors:
  ✓ Positive values: Green background
  ✓ Negative values: Red background
  ✓ Zero: Neutral color
  ✓ Formatting: Comma separator for thousands (1,110.00 not 1110.00)
```

---

### TEST 4: Currency Formatting (4 Decimals)
**Action:**
1. Look at Sell Price, Avg Cost, and Realised P&L columns
2. Verify decimal precision

**Expected Behavior:**
- ✓ All currency values show exactly 4 decimals
- ✓ Row 6 (MARUTI): Shows 10150.2500 (avg), 10145.7500 (cost), 13.5000 (P&L)
- ✓ Row 8 (SUNPHARMA): Shows 752.3456 (sell), 748.1234 (cost), 29.4556 (P&L)
- ✓ Row 1 (INFY): Shows 2510.0000 (even if original is 2510.00)

**Verify:**
```
Table → Hover over precision fields
Expected: Always 4 decimals shown (e.g., 2500.0000, not 2500 or 2500.00)
```

---

### TEST 5: Date/Time Formatting
**Action:**
1. Look at Date column
2. Verify dates are human-readable

**Expected Behavior:**
- ✓ Dates formatted: "Oct 6, 10:05 AM" (short date + time)
- ✓ Times show in 12-hour format with AM/PM
- ✓ All 8 rows have different dates (Oct 6, Oct 5, Oct 4, Oct 3, Oct 2, Oct 1, Sep 30, Sep 29)
- ✓ Sorted by most recent first (Oct 6 at top)

**Verify:**
```
Table → Date column (first column)
Expected format: "Oct 6, 10:05 AM" (not "2026-10-06T10:05:00Z")
Expected order: Newest to oldest (Oct 6 → Sep 29)
```

---

### TEST 6: Pagination - Page Size Limits
**Action:**
1. Change page size from 20 to 5
2. Observe pagination controls

**Expected Behavior:**
- ✓ First page shows 5 records (INFY, TCS, RELIANCE, HDFCBANK, WIPRO)
- ✓ Pagination shows: "Page 1 of 2" (8 records ÷ 5 per page = ceil(1.6) = 2 pages)
- ✓ Total Records: 8 (unchanged)
- ✓ Total P&L: Still +89.0056 (sum of ALL, not just page)
- ✓ Next button enabled, Previous button disabled

**Verify:**
```
Change size to 5:
Expected:
  - First page: 5 rows
  - "Page 1 of 2"
  - Total P&L: +89.0056 (all records)
  - Next button: Enabled
  - Previous button: Disabled
```

---

### TEST 7: Pagination - Navigation
**Action:**
1. Keep page size at 5
2. Click "Next" button
3. Then click "Previous" button

**Expected Behavior:**
- ✓ Page 1: Shows rows 1-5 (INFY, TCS, RELIANCE, HDFCBANK, WIPRO)
- ✓ Click Next → Page 2: Shows rows 6-8 (MARUTI, INFY, SUNPHARMA)
- ✓ Page info: "Page 2 of 2"
- ✓ Next button disabled, Previous enabled
- ✓ Click Previous → Back to Page 1
- ✓ Pagination controls update correctly

**Verify:**
```
Set size=5, then:
  Page 1 → Next → Page 2 (shows different rows)
  Page 2 → Previous → Page 1 (back to original)
```

---

### TEST 8: Pagination - Direct Page Selection
**Action:**
1. Page size = 5
2. Click on page number "2" button directly

**Expected Behavior:**
- ✓ Jumps to Page 2 (rows 6-8)
- ✓ Page info updates: "Page 2 of 2"
- ✓ All rows change (no persistence from Page 1)
- ✓ Previous button enabled

**Verify:**
```
Page buttons visible (max 5 buttons shown)
Click page "2" button → Page 2 loads instantly
```

---

### TEST 9: Symbol Filter
**Action:**
1. Enter "INFY" in symbol filter
2. Click "Apply"
3. Verify filtered results

**Expected Behavior:**
- ✓ Loading spinner shows briefly (600ms delay simulates network)
- ✓ Results filtered to 2 records (rows 1 & 7, both INFY.NS)
- ✓ Table shows:
  - Row 1: INFY.NS, qty 10, P&L +100
  - Row 7: INFY.NS, qty 8, P&L +80
- ✓ Total Count: 2
- ✓ Total P&L: +180 (100 + 80, sum of filtered)
- ✓ Pagination: "Page 1 of 1"

**Verify:**
```
Filter symbol: "INFY" → Apply
Expected:
  - 2 records visible
  - Total: +180.00 USD 🟢
  - All rows show INFY.NS
```

---

### TEST 10: Symbol Filter - Partial Match
**Action:**
1. Clear filter
2. Enter "REL" in symbol filter
3. Click "Apply"

**Expected Behavior:**
- ✓ Filter matches "RELIANCE.NS" (partial match works)
- ✓ 1 record shown (RELIANCE, qty 2, P&L 0)
- ✓ Total Count: 1
- ✓ Total P&L: 0.00
- ✓ Break-even transaction displays in neutral color

**Verify:**
```
Filter symbol: "REL" → Apply
Expected:
  - 1 record (RELIANCE.NS)
  - P&L: 0.00 (neutral color)
  - Partial match works
```

---

### TEST 11: Date Range Filter
**Action:**
1. Clear filters (click "Reset")
2. Set "From" date to Oct 3
3. Click "Apply"

**Expected Behavior:**
- ✓ Loading spinner shows
- ✓ Filtered to 4 records from Oct 3 onwards:
  - Oct 6: INFY.NS (+100)
  - Oct 5: TCS.NS (-500)
  - Oct 4: RELIANCE.NS (0)
  - Oct 3: HDFCBANK.NS (+1110)
- ✓ Records from Oct 2, Oct 1, Sep 30, Sep 29 excluded
- ✓ Total Count: 4
- ✓ Total P&L: +710 (100 - 500 + 0 + 1110)
- ✓ Pagination: "Page 1 of 1"

**Verify:**
```
From: Oct 3 → Apply
Expected:
  - 4 records visible
  - Latest: Oct 6, Oldest: Oct 3
  - Total: +710.00 USD 🟢
```

---

### TEST 12: Date Range Filter - To Date
**Action:**
1. Keep "From" = Oct 3
2. Add "To" date = Oct 5
3. Click "Apply"

**Expected Behavior:**
- ✓ Filtered to 3 records (Oct 3-5 inclusive):
  - Oct 5: TCS.NS (-500)
  - Oct 4: RELIANCE.NS (0)
  - Oct 3: HDFCBANK.NS (+1110)
- ✓ Oct 6 (after To date) excluded
- ✓ Total Count: 3
- ✓ Total P&L: +610 (-500 + 0 + 1110)

**Verify:**
```
From: Oct 3, To: Oct 5 → Apply
Expected:
  - 3 records visible
  - Oct 6 excluded (after To date)
  - Total: +610.00 USD 🟢
```

---

### TEST 13: Reset Filters
**Action:**
1. Have filters active (e.g., symbol "INFY")
2. Click "Reset" button
3. Verify all records return

**Expected Behavior:**
- ✓ All filters cleared (inputs empty)
- ✓ All 8 records displayed
- ✓ Total Count: 8
- ✓ Total P&L: +89.0056
- ✓ Page reset to 1
- ✓ No error messages

**Verify:**
```
After filters active → Click "Reset"
Expected:
  - Inputs cleared
  - 8 records shown again
  - Total: +89.0056 USD 🟢
```

---

### TEST 14: Empty State
**Action:**
1. Apply filter "NOTEXIST"
2. Click "Apply"

**Expected Behavior:**
- ✓ Loading spinner shows briefly
- ✓ Table disappears
- ✓ Message displays: "No realised P&L records found"
- ✓ Subtitle: "Try adjusting your filters or check back after placing SELL orders"
- ✓ Total Count: 0
- ✓ Total P&L: 0.00
- ✓ Pagination hidden (no pages)

**Verify:**
```
Filter: "NOTEXIST" → Apply
Expected:
  - Empty state message shown
  - Table hidden
  - Total: 0.00 USD ⚪
```

---

### TEST 15: Loading State
**Action:**
1. Apply any filter
2. Quickly look for loading indicator while data loads

**Expected Behavior:**
- ✓ Loading spinner appears (600ms simulated delay)
- ✓ Spinner shows in place of table
- ✓ Filter section disabled (buttons grayed out)
- ✓ After 600ms, spinner disappears and data shows
- ✓ No lag or freezing

**Verify:**
```
Apply any filter → watch for spinner
Expected: Spinner visible for ~600ms, then data appears
```

---

### TEST 16: Error Handling
**Action:**
1. (Only if switching to real API) Break network connection
2. Try to load data

**Expected Behavior:**
- ✓ Loading spinner shows
- ✓ After timeout, error message appears: "Failed to load P&L history. Please try again."
- ✓ Table clears
- ✓ Retry possible by applying filters again

**Note:** Mock service doesn't fail (always succeeds after 600ms delay). To test this, switch to real API and disable backend.

---

### TEST 17: Responsive Design - Mobile (480px)
**Action:**
1. Resize browser to 480px width (DevTools mobile view)
2. Or use mobile device

**Expected Behavior:**
- ✓ Layout adapts to single column
- ✓ Table converts to stacked cards (one record per card)
- ✓ Headers hidden or minimized
- ✓ Currency values still show with 4 decimals
- ✓ All buttons clickable
- ✓ Filter inputs stack vertically
- ✓ No horizontal scrolling needed

**Verify:**
```
DevTools: Toggle device toolbar → iPhone SE (480px)
Expected: Responsive layout, no overflow
```

---

### TEST 18: Responsive Design - Tablet (768px)
**Action:**
1. Resize to 768px width
2. Verify intermediate layout

**Expected Behavior:**
- ✓ Table visible with columns
- ✓ Font sizes reasonable
- ✓ All columns fit or have scroll (if needed)
- ✓ Pagination buttons visible
- ✓ No content cut off

**Verify:**
```
DevTools: iPad view (768px)
Expected: All content visible without excessive scrolling
```

---

### TEST 19: Responsive Design - Desktop (1920px)
**Action:**
1. Full-screen desktop (1920px width)
2. Verify layout uses space efficiently

**Expected Behavior:**
- ✓ Table has comfortable spacing
- ✓ All columns visible without scrolling
- ✓ Summary cards aligned nicely
- ✓ Pagination spread across bottom
- ✓ Professional appearance

**Verify:**
```
Full desktop view (1920px)
Expected: Spacious layout, professional appearance
```

---

### TEST 20: Navigation to Standalone Page
**Action:**
1. In dashboard card mode, click "View all" link
2. Verify navigation

**Expected Behavior:**
- ✓ Navigates to `/realised-pnl` route
- ✓ Page URL changes
- ✓ Page title changes to "Your Realised P&L"
- ✓ All 8 records shown (or filtered if filters were active)
- ✓ Filters visible

**Verify:**
```
Dashboard card → Click "View all"
Expected:
  - URL: /realised-pnl
  - Page title: "Your Realised P&L"
  - Filters visible
```

---

## Browser DevTools Debugging

### Check Console
```javascript
// Open Browser DevTools (F12) → Console tab

// You'll see:
[RealisedPnlComponent] Loaded in mock mode
[RealisedPnlComponent] Records loaded: 8 total, page 0 of 1
[RealisedPnlComponent] Filter applied: symbol=INFY
```

### Check Network Tab
```
Network tab → 
  No API calls (since using mock service)
  If switching to real API, you'll see:
    GET /api/v1/portfolio/42/pnl/realised?symbol=INFY&page=0&size=20
    Status: 200 OK
    Response: { records: [...], summary: {...} }
```

### Check Elements Inspector
```
Inspect Table Row →
  <tr class="pnl-row">
    <td>Oct 6, 10:05 AM</td>
    <td>INFY.NS</td>
    <td class="value">10</td>
    <td class="value positive">2510.0000</td>
    <td class="value positive">2500.0000</td>
    <td class="value positive">+100.0000</td>  ← Green background
  </tr>

Negative example:
    <td class="value negative">-500.0000</td>  ← Red background
```

---

## Switching to Real API

When ready to test with real backend:

### Step 1: Enable Real API
Edit `src/environments/environment.ts`:
```typescript
useMockPnlData: false  // Disable mock data
```

### Step 2: Rebuild
```bash
ng serve
```

### Step 3: Backend Requirements
Ensure backend services are running:
- ✅ executor-service (Port 8080)
- ✅ order-service (Port 8081)
- ✅ PostgreSQL (Pnl table created)
- ✅ Kafka topics (orders, trade-events, market-data)

### Step 4: Verify API Connection
- Create test orders via Postman or UI
- Check `/api/v1/portfolio/{accountId}/pnl/realised` endpoint
- Verify JWT token is sent and validated

---

## Common Issues & Troubleshooting

| Issue | Cause | Solution |
|-------|-------|----------|
| Component doesn't load | Environment flag missing | Add `useMockPnlData: true` to environment.ts |
| No data shown (blank table) | Mock service not injected | Verify MockRealisedPnlService in component imports |
| Wrong symbols displayed | Mock data hardcoded | Edit `realised-pnl.mock.ts` to change test data |
| Dates show as ISO format | Date pipe not applied | Check template uses `\| date:'short'` pipe |
| Currency shows no decimals | Formatting issue | Verify formatCurrency() method is called |
| Pagination doesn't work | Page calculation error | Check pageNumbers getter logic |
| Filters don't apply | Service not using filter params | Verify getRealisedPnlHistory() accepts symbol/from/to |
| Color coding missing | CSS not loaded | Ensure realised-pnl.css is linked in component |

---

## Test Coverage Summary

| Test Category | Count | Status |
|---------------|-------|--------|
| **Display** | 7 | ✓ Load, Colors, Formatting, Dates |
| **Pagination** | 4 | ✓ Limits, Navigation, Direct selection |
| **Filtering** | 5 | ✓ Symbol, Date range, Partial match, Reset |
| **Responsive** | 3 | ✓ Mobile, Tablet, Desktop |
| **Navigation** | 1 | ✓ Route to standalone |
| **Error Handling** | 1 | ✓ Error states |
| **Total** | **21 Test Cases** | **Ready** |

---

## Next Steps

1. ✅ **Start Frontend:** `ng serve`
2. ✅ **Navigate to P&L:** Dashboard or `/realised-pnl`
3. ✅ **Run Test Cases:** Follow tests 1-21 above
4. ✅ **Document Issues:** Note any visual/functional problems
5. ✅ **Fix & Rebuild:** Make code changes, rebuild
6. ⏳ **Switch to Real API:** When ready, set `useMockPnlData: false`
7. ⏳ **Integration Testing:** Test with real backend and database

---

## Mock Data Management

### To Change Test Data
Edit `src/app/features/trade/realised-pnl/realised-pnl.mock.ts`:

```typescript
// Add new transaction:
{
  pnlId: 9,
  orderId: 'aaaaaaaa-bbbb-cccc-dddd-eeeeeeee0009',
  symbol: 'YOUR_SYMBOL',
  quantity: 10,
  sellPrice: 1000.00,
  averageCost: 950.00,
  realisedPnl: 500.00,  // (1000 - 950) * 10
  realisedAt: '2026-10-07T12:00:00Z'
}
```

### To Change Page Size Default
Edit component:
```typescript
pageSize = signal<number>(50);  // Change from 20
```

### To Change Simulated Latency
Edit mock service:
```typescript
return of(response).pipe(delay(1000));  // Change from 600ms
```

---

**All mock infrastructure is ready! Start with TEST 1 and work through the checklist.** 🚀
