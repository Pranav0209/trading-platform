# E2E Tests - Trading UI

End-to-end tests using Playwright that validate the two core user journeys:

1. **Sign-in Journey** - Authentication flow with guard redirects and credential validation
2. **Place Order Journey** - Order placement with validation and status handling

## Setup

### Prerequisites

- Ensure the full stack is running:
  - Angular trading UI on `http://localhost:4200`
  - Auth service on `http://localhost:3000`
  - Trade service on `http://localhost:3002`

### Environment Configuration

Copy `.env.example` to `.env` and fill in the values:

```bash
cp .env.example .env
```

Required environment variables:

- `TRADING_UI_URL` - URL of the trading UI (default: http://localhost:4200)
- `TEST_USERNAME` - Valid test user username
- `TEST_PASSWORD` - Valid test user password
- `TEST_ACCOUNT_ID` - Numeric account ID for the test user

## Running Tests

### Run all tests

```bash
npm run e2e
```

### Run specific test file

```bash
npx playwright test e2e/sign-in.spec.ts
```

### Run in debug mode

```bash
npm run e2e:debug
```

### Run in UI mode (interactive)

```bash
npm run e2e:ui
```

### View test report

```bash
npm run e2e:report
```

## Test Isolation

Each test is **completely isolated**:

- ✅ No shared state between tests
- ✅ Fresh browser context for each test
- ✅ Cookies and storage cleared before each test
- ✅ No module-level token caching
- ✅ Each order placement is independent

This ensures tests can run in any order and one test's failure doesn't cascade.

## Sign-In Journey Tests

Located in `e2e/sign-in.spec.ts`

**Test Cases:**

1. **Guard Redirect** - Unauthenticated access to `/dashboard` redirects to `/sign-in`
2. **Refused Sign-In** - Invalid credentials show trader-facing error message
3. **Successful Sign-In** - Valid credentials redirect to dashboard
4. **ReturnTo Parameter** - Sign-in can redirect to a requested path

**Covers:**

- ✅ Route guards blocking unauthenticated access
- ✅ Error handling with error code mapping
- ✅ Successful authentication flow
- ✅ Post-login navigation

## Place Order Journey Tests

Located in `e2e/place-order.spec.ts`

**Test Cases:**

1. **Form Validation** - Invalid input is rejected before submission
2. **Placed Order Response** - Valid order placement returns NEW, FILLED, or REJECTED
3. **Rejection Handling** - Invalid orders show trader-facing error messages
4. **Isolation** - Each order placement uses fresh authentication

**Covers:**

- ✅ Client-side form validation
- ✅ Order placement with error handling
- ✅ Server status acceptance (NEW/FILLED/REJECTED)
- ✅ Test isolation and fresh sessions

### Order Status Acceptance

The test accepts **any valid response**:

- ✅ `NEW` - Order queued for processing (Sprint 7+ behavior)
- ✅ `FILLED` - Order immediately filled (Sprint 6 behavior)
- ✅ `REJECTED` - Order validation failed

**Note:** A test asserting only `FILLED` would break when the Trade Executor switches on (Sprint 7+).

## Test Data Selectors

Tests rely on these data-testid attributes:

### Sign-In Page

- `sign-in-form` - Main form element
- `submit-button` - Submit button
- `error-message` - Error message display

### Dashboard

- `dashboard` - Main dashboard container
- `nav-order-ticket` - Navigation link to order ticket

### Order Ticket

- `order-ticket` - Main order ticket container
- `submit-button` - Submit order button
- `response-message` - Order response message

## Debugging Failed Tests

1. **Enable trace/screenshot on failure:**
   - Traces and screenshots automatically saved on failure
   - View with: `npm run e2e:report`

2. **Run in UI mode:**
   - `npm run e2e:ui`
   - Step through tests interactively

3. **Check environment variables:**
   - Ensure `.env` file is correctly configured
   - Verify services are running on expected URLs

4. **Check test isolation:**
   - Verify cookies are cleared between tests
   - Check for module-level state caching

## CI/CD Integration

The tests can be integrated into CI pipelines:

```bash
# Install dependencies
npm install

# Start services (depends on your setup)
npm run start &

# Run tests
npm run e2e

# Exit code 0 = all passed, non-zero = failures
```

## Limitations & Future Work

- Tests require services to be running (no stubbing)
- Two journeys implemented (sign-in and place-order)
- A third journey covering the order blotter is possible if time permits
- Tests use real data; test account must be provisioned on all services
