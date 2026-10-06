# Frontend Testing Checklist - Mock Data Ready

## ✅ Build Status
**Status:** BUILD SUCCESS ✓  
**Time:** 5.04 seconds  
**Output:** `dist/trading-ui/`  
**Warnings:** Bundle size only (non-blocking)

---

## Quick Start

### 1. Start Development Server
```bash
cd Frontend/frontend-application
ng serve
```

Expected output:
```
✔ Compiled successfully
✔ watching for file changes...
```

Navigate to: `http://localhost:4200`

### 2. Login to Dashboard
- Username: Any (mock auth allows all)
- Password: Any
- Expected: Dashboard loads with navigation sidebar

### 3. View P&L Component (2 Ways)

**Option A: Dashboard Widget**
- Navigate: Home/Dashboard
- Expected: Card showing "Recent Realised P&L" with data

**Option B: Standalone Page**
- Click sidebar: "P&L History"
- URL: `http://localhost:4200/realised-pnl`
- Expected: Full page with filters and table

---

## What to Check - Summary

### ✓ CHECK 1: Initial Load (Most Important)
**Navigate to:** Dashboard or `/realised-pnl`

**Expected to see:**
```
┌─────────────────────────────────────────┐
│     REALISED P&L HISTORY                │
├─────────────────────────────────────────┤
│ Total P&L: +89.01 USD  🟢 (GREEN)       │
│ Total Records: 8                        │
├─────────────────────────────────────────┤
│ Date        │ Symbol    │ Qty │ P&L    │
│ Oct 6, 10am │ INFY.NS   │ 10  │+100.00 │
│ Oct 5, 2pm  │ TCS.NS    │ 5   │-500.00 │
│ Oct 4, 9am  │ RELIANCE  │ 2   │  0.00  │
│ (5 more rows...                        │
└─────────────────────────────────────────┘
```

**Verify:**
- ✅ 8 data rows visible
- ✅ Total P&L = +89.01 USD (green color)
- ✅ No errors, no blank data
- ✅ Dates formatted human-readable (Oct 6, not 2026-10-06T...)
- ✅ Currency shows 4 decimals (100.0000, not 100)

---

### ✓ CHECK 2: Color Coding
**Look at P&L Column:**

| Row | P&L Value | Expected Color |
|-----|-----------|-----------------|
| 1 | +100 | 🟢 GREEN (profit) |
| 2 | -500 | 🔴 RED (loss) |
| 3 | 0 | ⚪ NEUTRAL (break-even) |
| 4 | +1110 | 🟢 GREEN with comma (1,110.00) |
| 5 | -675 | 🔴 RED |

**Verify:**
- ✅ Positive P&L = Green background/text
- ✅ Negative P&L = Red background/text
- ✅ Zero P&L = Neutral/gray
- ✅ Large numbers formatted with commas (1,110 not 1110)

---

### ✓ CHECK 3: Precision (Decimals)
**Look at numeric columns:**

Expected to see **exactly 4 decimals** everywhere:
```
Sell Price: 2510.0000    (not 2510 or 2510.00)
Avg Cost:   2500.0000    (not 2500)
Quantity:   10           (whole numbers OK)
P&L:        100.0000     (not 100)
```

**Test high-precision row (Row 8):**
- Symbol: SUNPHARMA.NS
- Sell Price: 752.3456
- Avg Cost: 748.1234
- P&L: 29.4556

**Verify:**
- ✅ All 4 decimals visible
- ✅ No rounding to 2 decimals
- ✅ Scientific notation NOT used

---

### ✓ CHECK 4: Pagination (Page Size = 5)
**Action:** In standalone mode, change page size dropdown to "5"

**Expected:**
- Page 1 shows: 5 rows (INFY, TCS, RELIANCE, HDFCBANK, WIPRO)
- Page info: "Page 1 of 2"
- Total Records: 8 (unchanged)
- Total P&L: +89.01 (still sum of ALL, not just page)
- Next button: Enabled
- Previous button: Disabled

**Verify:**
- ✅ First page shows exactly 5 rows
- ✅ Pagination displays "Page 1 of 2"
- ✅ Next/Previous buttons work
- ✅ Total P&L still shows all records (+89.01)

---

### ✓ CHECK 5: Date/Time Format
**Look at Date column (first column):**

Expected format: `Oct 6, 10:05 AM` (not `2026-10-06T10:05:00Z`)

**Row order (newest to oldest):**
```
Oct 6, 10:05 AM  ← Most recent
Oct 5, 2:30 PM
Oct 4, 9:15 AM
Oct 3, 11:45 AM
Oct 2, 3:20 PM
Oct 1, 10:00 AM
Sep 30, 1:25 PM
Sep 29, 9:50 AM  ← Oldest
```

**Verify:**
- ✅ Dates use "MMM D, H:MM AM/PM" format
- ✅ Sorted newest first
- ✅ All 8 dates different and chronological

---

### ✓ CHECK 6: Symbol Filter
**Action (Standalone Page Only):**
1. In "Symbol" input, type: `INFY`
2. Click "Apply" button
3. Observe results

**Expected:**
- Loading spinner shows briefly
- Results filtered to 2 rows (both INFY.NS):
  - Row 1: INFY.NS, qty 10, P&L +100
  - Row 7: INFY.NS, qty 8, P&L +80
- Total Records: 2
- Total P&L: +180 (100 + 80)
- Pagination: "Page 1 of 1"

**Verify:**
- ✅ Only INFY rows shown
- ✅ Total updated to +180
- ✅ Loading state works (visible for ~600ms)

---

### ✓ CHECK 7: Reset Filters
**Action:**
1. Keep filters active (e.g., INFY symbol)
2. Click "Reset" button

**Expected:**
- Filter inputs clear
- All 8 records return
- Total: +89.01 USD
- Total Records: 8
- No error messages

**Verify:**
- ✅ Filters cleared
- ✅ Full data set restored
- ✅ Page reset to 1

---

### ✓ CHECK 8: Date Range Filter
**Action (Standalone Page):**
1. In "From" date, select: Oct 3
2. Click "Apply"

**Expected:**
- 4 records from Oct 3 onwards (Oct 3-6)
- Records from Oct 2 and earlier excluded
- Total Count: 4
- Total P&L: +710

**Then:**
3. Add "To" date: Oct 5
4. Click "Apply"

**Expected:**
- 3 records (Oct 3-5 inclusive)
- Oct 6 now excluded
- Total Count: 3
- Total P&L: +610

**Verify:**
- ✅ "From" date filters correctly
- ✅ "To" date filters correctly
- ✅ Date range inclusive (includes both start and end dates)
- ✅ Totals update appropriately

---

### ✓ CHECK 9: Empty Results
**Action:**
1. In Symbol field, type: `NOTEXIST`
2. Click "Apply"

**Expected:**
- Table disappears
- Message shows: "No realised P&L records found"
- Message shows: "Try adjusting your filters..."
- Total Count: 0
- Total P&L: 0.00
- Pagination hidden

**Verify:**
- ✅ Empty state message displays
- ✅ Table not shown
- ✅ No error (graceful empty state)

---

### ✓ CHECK 10: Mobile Responsive
**Action:** DevTools → Toggle device toolbar → iPhone SE (375px width)

**Expected:**
- Layout changes to mobile view
- No horizontal scroll
- Table converts to stacked cards or narrow layout
- All content still readable
- Buttons clickable
- Filters stack vertically

**Verify:**
- ✅ Mobile layout responsive
- ✅ No overflow
- ✅ Content accessible

---

## Expected Mock Data Summary

| # | Symbol | Qty | Sell Price | Avg Cost | **P&L** | Date |
|---|--------|-----|-----------|----------|---------|------|
| 1 | INFY.NS | 10 | 2510 | 2500 | **+100** 🟢 | Oct 6 |
| 2 | TCS.NS | 5 | 3900 | 4000 | **-500** 🔴 | Oct 5 |
| 3 | RELIANCE.NS | 2 | 2750 | 2750 | **0** ⚪ | Oct 4 |
| 4 | HDFCBANK.NS | 20 | 1805.50 | 1750 | **+1110** 🟢 | Oct 3 |
| 5 | WIPRO.NS | 15 | 380 | 425 | **-675** 🔴 | Oct 2 |
| 6 | MARUTI.NS | 3 | 10150.25 | 10145.75 | **+13.50** 🟢 | Oct 1 |
| 7 | INFY.NS | 8 | 2520 | 2510 | **+80** 🟢 | Sep 30 |
| 8 | SUNPHARMA.NS | 7 | 752.3456 | 748.1234 | **+29.4556** 🟢 | Sep 29 |
| **TOTAL** | - | 80 | - | - | **+89.0056** 🟢 | - |

---

## Mode Switching

### Current Mode: MOCK DATA ✅
```typescript
// src/environments/environment.ts
useMockPnlData: true  // ← Enabled for testing
```

### To Switch to Real API (Later)
```typescript
// src/environments/environment.ts
useMockPnlData: false  // ← Switch when ready
```

**Requirements for real API:**
- ✅ Backend services running (executor, order)
- ✅ PostgreSQL with pnl table
- ✅ Kafka topics configured
- ✅ JWT tokens valid
- ✅ CORS/network accessible

---

## Troubleshooting

| Problem | Solution |
|---------|----------|
| Blank page | Ensure `environment.ts` has `useMockPnlData: true` |
| No data rows | Verify MockRealisedPnlService is injected |
| Console errors | Check DevTools F12 → Console for TypeScript errors |
| Wrong dates | Verify mock data dates are ISO format (e.g., `2026-10-06T10:05:00Z`) |
| Decimals wrong | Check formatCurrency() is being called in template |
| Colors missing | Ensure `realised-pnl.css` is linked in component styleUrls |
| Pagination broken | Verify page calculation: `ceil(totalCount / pageSize)` |

---

## Browser DevTools Console Output

**Expected console logs when component loads:**

```javascript
[RealisedPnlComponent] Using mock P&L data for testing
[RealisedPnlComponent] Records loaded: 8 total
[RealisedPnlComponent] Total P&L: 89.0056
[RealisedPnlComponent] Page 0 of 1 (20 per page)
```

---

## Quick Validation Checklist

Copy-paste and check off as you verify:

```
DASHBOARD / INITIAL LOAD:
☐ 8 data rows visible
☐ Total P&L: +89.01 USD (green)
☐ Total Records: 8
☐ Dates formatted: "Oct 6, 10:05 AM"
☐ No error messages

COLOR CODING:
☐ +100 is green (INFY row)
☐ -500 is red (TCS row)
☐ 0 is neutral (RELIANCE row)
☐ Formatting with commas (1,110 not 1110)

DECIMAL PRECISION:
☐ All currency: 4 decimals (2500.0000)
☐ High precision: 752.3456 visible
☐ No rounding to 2 decimals

PAGINATION:
☐ Set size to 5 → Shows "Page 1 of 2"
☐ Click Next → Page 2 visible
☐ Total P&L still +89.01 (all records)

FILTERS (Standalone page):
☐ Filter "INFY" → 2 rows, +180 total
☐ Filter "Oct 3 to Oct 5" → 3 rows, +610 total
☐ Reset → All 8 rows return
☐ Filter "NOTEXIST" → Empty state message

RESPONSIVE:
☐ Mobile (375px) → Stacked layout
☐ Tablet (768px) → Table visible
☐ Desktop (1920px) → Spacious layout

NAVIGATION:
☐ Sidebar link → "/realised-pnl" works
☐ URL changes correctly
☐ Page reloads with data
```

---

## Next Steps After Testing

1. ✅ **Verify all 10 checks pass**
2. ✅ **Document any UI issues or missing features**
3. ⏳ **Backend integration testing** (when services available)
4. ⏳ **Real API testing** (set `useMockPnlData: false`)
5. ⏳ **End-to-end with Kafka topics** (full system test)

---

**Ready to test! Start with Check 1: Initial Load** 🚀
