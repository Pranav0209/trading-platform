# Smart Rules Frontend

This folder contains the Angular frontend for the Smart Rules feature. The page lets a signed-in customer build automated rule chains that are stored in the trade backend and monitored after they are saved.

## Files in this folder

- `smart-rules.ts`: standalone Angular component with all builder logic, validation, API calls, and rule description formatting.
- `smart-rules.html`: template for the rule builder, nested sub-rule editor, and saved rule list.
- `smart-rules.css`: styling for the builder and saved rule cards.
- `smart-rules.spec.ts`: minimal component creation test.

## Route and access

- Route: `/smart-rules`
- Registered in `src/app/app.routes.ts`
- Protected by `customerGuard`, so only authenticated customer users can access it.
- The feature is also linked from the main layout navigation as `Smart Rules`.

## What the page does

When the component loads, it performs two startup actions:

1. Loads existing smart rules for the current session account.
2. Loads tradable instruments so the symbol field can offer autocomplete suggestions.

The page is split into two main panels:

- `Create a rule chain`: the builder used to compose a root rule and optional child rules.
- `Armed rule chains`: the list of saved rules currently returned by the backend.

## Rule builder behavior

The builder starts with one default outer rule draft.

Default draft values:

- Action: `BUY`
- Quantity mode: `FIXED`
- Quantity: `1`
- Metric type: `PRICE`
- Operator: `LESS_THAN`
- Symbol: empty
- Enabled: `true`
- No children

### Supported actions

- `BUY`
- `SELL`

### Supported quantity modes

- `FIXED`
- `HALF`
- `ALL`

Current UI rule:

- `BUY` rules only allow `FIXED` quantity.
- `SELL` rules allow `FIXED`, `HALF`, or `ALL`.

If quantity mode is `FIXED`, the user must enter a quantity greater than zero.

### Supported condition types exposed in the UI

- `PRICE`
- `AVERAGE_COST`
- `MOVING_AVERAGE`
- `PERCENT_CHANGE`
- `MOVING_AVERAGE_CROSSOVER`

The shared model also defines `BOLLINGER_BAND` and `STOP_LOSS`, but this component does not currently expose them in the UI.

### Condition-type restrictions

- `PRICE` is available only for `BUY` rules.
- `AVERAGE_COST` is available only for `SELL` rules.
- `MOVING_AVERAGE`, `PERCENT_CHANGE`, and `MOVING_AVERAGE_CROSSOVER` are available for both actions.

When the action changes, the component recalculates the allowed metric types. If the current metric becomes invalid, it is replaced with the first valid option.

### Operators and special condition handling

For most rule types, the UI exposes one of these operators:

- `GREATER_THAN`
- `LESS_THAN`
- `EQUAL_TO`

Special cases:

- `MOVING_AVERAGE_CROSSOVER` uses only `CROSS_ABOVE` or `CROSS_BELOW`.
- `PERCENT_CHANGE` does not show an operator dropdown. Instead, it shows a direction selector:
  - `INCREASE`
  - `DECREASE`

When the request is sent:

- `PERCENT_CHANGE` with `INCREASE` becomes operator `GREATER_THAN`.
- `PERCENT_CHANGE` with `DECREASE` becomes operator `LESS_THAN`.
- A decrease target is sent to the backend as a negative percentage value.

### Required numeric inputs by condition type

- `PRICE`: requires target value.
- `AVERAGE_COST`: requires target value.
- `MOVING_AVERAGE`: requires target value and lookback period.
- `PERCENT_CHANGE`: requires target percentage.
- `MOVING_AVERAGE_CROSSOVER`: requires lookback period and secondary lookback period, but not target value.

Current defaults applied by the component:

- `MOVING_AVERAGE` sets lookback to `20` if empty.
- `MOVING_AVERAGE_CROSSOVER` sets lookback to `20` and secondary lookback to `50` if empty.
- `PERCENT_CHANGE` defaults direction to `INCREASE` and target percentage to `1` if missing.

Although the data model has a `standardDeviationMultiplier` field, the UI does not currently use it because `needsStdDeviation()` always returns `false`.

## Symbol selection and autocomplete

The root rule symbol field supports autocomplete based on tradable instruments from the generated `InstrumentsService`.

Behavior:

- The dropdown opens when the root symbol input receives focus.
- Typing filters by symbol or display name, case-insensitively.
- Selecting an entry copies the instrument symbol into the draft.
- Clearing the input restores the full tradable list.

Current limitations:

- Only the root rule shows the autocomplete dropdown.
- Child rules cannot edit their symbol directly.
- Child symbols always follow the parent symbol.

## Nested sub-rules

The feature supports nested child rules under a root rule.

What happens when a child rule is added:

- The child action is set to the opposite of the parent action.
  - Parent `BUY` creates child `SELL`
  - Parent `SELL` creates child `BUY`
- The child symbol starts with the same symbol as the parent.
- The child quantity mode defaults to `ALL`.

The parent-child synchronization logic also enforces these rules:

- If the parent action changes, all descendants are updated to the opposite action.
- If the parent symbol changes, all descendants inherit the same symbol.
- Child rules are recursively validated before save.

The UI message describes sub-rules as follow-up exits that are armed only after the outer rule has been placed.

## Validation rules before saving

The component blocks save and shows a warning toast when any of these checks fail:

- Symbol is required.
- Fixed quantity must be greater than zero.
- `BUY` rules must use `FIXED` quantity.
- `BUY` rules cannot use `AVERAGE_COST`.
- `SELL` rules cannot use `PRICE`.
- Positive target value is required whenever the selected condition type needs one.
- Lookback period must be at least `2` when required.
- Secondary lookback period must be at least `2` when required.

If there is no account in the current session, saving is blocked and an error toast is shown.

## Save flow

Clicking `Save rule` does the following:

1. Reads the active session account ID.
2. Validates the full rule tree.
3. Converts the draft into a `CreateSmartRuleRequest` payload.
4. Sends a `POST` request through `SmartRuleService`.
5. Prepends the returned rule to the local saved list.
6. Resets the builder to a fresh default draft.
7. Shows a success toast: `Smart rule saved and armed for background execution.`

The request is sent to:

- `POST /api/v1/accounts/{accountId}/smart-rules`

Request shaping details:

- Symbols are uppercased before submission.
- `FIXED` quantity is sent as a number.
- Non-fixed quantity omits the `quantity` field.
- Child rules are serialized recursively.
- Percent decrease values are sent as negative target values.

## Load flow

On page load, existing rules are fetched from:

- `GET /api/v1/accounts/{accountId}/smart-rules`

The rules panel then:

- shows a loading state while the request is in flight,
- shows an empty state if no rules exist,
- shows saved rule cards when rules are returned.

If loading fails, the component shows an error toast using the backend message when available.

## Saved rule list behavior

Saved rules are rendered as a tree.

For each rule, the UI shows:

- whether it is a root or child rule,
- a human-readable sentence describing the rule,
- whether the rule is enabled or paused,
- `lastTriggeredAt` if present,
- `activatedAt` if present.

Description examples are generated dynamically from the rule data, for example:

- `BUY 1 of INFY.NS when price is less than 100`
- `SELL all of INFY.NS when average price is greater than 120`
- `BUY 1 of INFY.NS when the 20-day average crosses above the 50-day average`
- `SELL all of INFY.NS when daily percentage change decreases by 2%`

For child rules, the text explicitly says the quantity is taken from the parent rule context.

## Delete flow

Only root rules can be deleted from the UI.

Clicking `Delete` on a root rule:

1. Marks that rule as the current deleting item.
2. Sends `DELETE /api/v1/accounts/{accountId}/smart-rules/{ruleId}`.
3. Removes the rule from the local list if successful.
4. Shows an informational toast: `Smart rule removed`.

If delete fails, the component restores the button state and shows an error toast.

Deleting a root rule removes its entire displayed tree from the page because child rules are nested under that root in the returned data.

## Services and models used by this feature

- `SmartRuleService`
  - `getRules(accountId)`
  - `createRule(accountId, payload)`
  - `deleteRule(accountId, ruleId)`
- `Session`
  - supplies the current account ID
- `ToastService`
  - shows success, warning, info, and error feedback
- `InstrumentsService`
  - fetches available tradable instruments for autocomplete

Core model types used here:

- `SmartRule`
- `CreateSmartRuleRequest`
- `SmartRuleAction`
- `SmartRuleQuantityMode`
- `SmartRuleMetricType`
- `SmartRuleOperator`

## Backend architecture

The frontend builder is only one part of the Smart Rules feature. The backend logic is split across two services:

- `order-service`: owns the authenticated API, validates requests, normalizes data, and stores rule trees in the database.
- `executor-service`: listens to market-data ticks, evaluates enabled rules, and places internal market orders when a rule transitions from not matched to matched.

In practice, the lifecycle looks like this:

1. The frontend posts a rule tree to `order-service`.
2. `order-service` validates the request and saves the root rule plus its children into the `smart_rules` table.
3. `executor-service` consumes Kafka `market-data` messages.
4. When a quote arrives for a symbol, `executor-service` loads enabled rules for that symbol and evaluates them.
5. If a rule triggers, `executor-service` places an internal market order and activates any child rules.

## Order-service backend logic

### Controller

`SmartRuleController` exposes three authenticated endpoints:

- `GET /api/v1/accounts/{accountId}/smart-rules`
- `POST /api/v1/accounts/{accountId}/smart-rules`
- `DELETE /api/v1/accounts/{accountId}/smart-rules/{ruleId}`

Before calling the service layer, the controller checks that the authenticated user account in the JWT matches the path account ID. If they do not match, it throws a `ForbiddenException` with `ACC-403`.

### Main order-service classes involved

- `SmartRuleController`
  - REST entry point for list, create, and delete.
- `SmartRuleService`
  - service interface used by the controller.
- `SmartRuleServiceImpl`
  - implements validation, entity creation, persistence, and response mapping.
- `SmartRuleRepository`
  - loads root rules for an account and deletes rules by account ownership.
- `AccountRepository`
  - ensures the account exists before load or save.
- `InstrumentRepository`
  - ensures the symbol exists and is tradable; also provides display names when building responses.

### Request and response DTOs

`CreateSmartRuleRequest` contains:

- symbol
- action
- quantity mode
- optional quantity
- metric type
- operator
- optional target value
- optional lookback values
- optional standard deviation multiplier
- optional enabled flag
- optional recursive child rules

The DTO also uses bean validation:

- `symbol` must be present and non-blank.
- `action`, `quantityMode`, `metricType`, and `operator` must be non-null.
- child requests are recursively validated with `@Valid`.

`SmartRuleResponse` returns the persisted rule tree plus backend-managed execution fields:

- `activationReferencePrice`
- `activatedQuantity`
- `activatedAt`
- `lastTriggeredAt`
- recursive children

### Persistence model

Rules are stored as a self-referencing tree in the `smart_rules` table.

Important stored fields include:

- `parent_rule_id` for rule chaining
- `enabled` for whether the rule can be evaluated
- `condition_met` to remember whether the condition is already true
- `activation_reference_price` for child activation context and stop-loss evaluation
- `activated_quantity` for tracking the quantity available to a child rule
- `activated_at` for whether a child rule has been armed
- `last_triggered_at` for audit and UI display

The order-service entity config also enables:

- cascade persistence for children
- orphan removal for child cleanup when a tree changes or is deleted
- child ordering by ascending ID

### Create flow in order-service

When the frontend saves a rule, `SmartRuleServiceImpl.createRule()` does the following:

1. Loads the account by ID.
2. Normalizes the symbol to uppercase.
3. Verifies that the instrument exists and is tradable.
4. Validates the request against backend business rules.
5. Recursively converts the root and any child requests into `SmartRule` entities.
6. Applies backend defaults.
7. Saves the full tree using `SmartRuleRepository.save()`.
8. Maps the persisted entity tree into `SmartRuleResponse`.

Backend defaults applied during entity creation:

- `enabled` defaults to `true` if omitted.
- `createdAt` and `updatedAt` are set to `OffsetDateTime.now()`.
- `standardDeviationMultiplier` defaults to `2.0` for `BOLLINGER_BAND` rules.

### Backend validation rules

The backend enforces the frontend rules and a few additional ones.

Shared rules enforced in both frontend and backend:

- buy rules must use fixed quantity
- average-cost rules are only for sell rules
- price rules are only for buy rules
- fixed quantity must be greater than zero
- non-fixed quantity modes must not include an explicit quantity
- child rules must use the same symbol as the parent
- child rules must use the opposite action from the parent
- indicator rules need lookback of at least 2
- moving-average crossover rules need a secondary lookback of at least 2
- moving-average crossover rules must use `CROSS_ABOVE` or `CROSS_BELOW`
- target value is required for every rule type except `BOLLINGER_BAND`
- non-percent targets must be greater than zero
- percent-change targets must be non-zero

Extra backend capabilities not currently exposed by the frontend builder:

- `BOLLINGER_BAND` rules must use `TOUCH_UPPER` or `TOUCH_LOWER`
- `STOP_LOSS` rules must be child rules
- `STOP_LOSS` rules must be sell rules

This means the backend supports a broader rule model than the current Angular UI exposes.

### Read and delete behavior in order-service

Read flow:

- `getRules(accountId)` first verifies that the account exists.
- It then loads only root rules for that account, ordered by newest first.
- Response mapping recursively includes each rule's children.
- Instrument display names are filled from `InstrumentRepository`.

Delete flow:

- `deleteRule(accountId, ruleId)` only deletes a rule if the rule belongs to the account.
- If the rule is missing, the backend throws `SmartRuleNotFoundException`.
- Because rules are stored as a parent-child tree, deleting a root rule removes the whole subtree.

## Executor-service backend logic

The executor is the part that turns a saved rule into an actual trade.

### Main executor-service classes involved

- `SmartRuleEvaluator`
  - Kafka listener and core rule evaluation engine.
- `SmartRuleIndicatorCalculator`
  - computes SMA values and Bollinger bands from candle data.
- `SmartRuleRepository`
  - loads enabled rules for a symbol, including children and account data.
- `PositionRepository`
  - loads positions used for average-cost comparisons and sell-quantity resolution.
- `FauxnanceCandleClient`
  - fetches candle history for indicator-based rules.
- `InternalOrderClient`
  - places internal market orders back into the order platform.

### Kafka trigger and symbol-based evaluation

`SmartRuleEvaluator` listens to Kafka topic `market-data` with group ID `smart-rule-service`.

For each incoming quote message, it:

1. Parses JSON with `ObjectMapper`.
2. Reads `symbol`, `price`, and optional `previousClose`.
3. Loads enabled rules for that symbol.
4. Bulk-loads positions for all accounts that have matching rules for that symbol.
5. Evaluates each rule independently.

### Executor caching behavior

The evaluator uses two in-memory caches to reduce load:

- Rule cache: 2-second TTL per symbol.
- Candle cache: 60-second TTL per symbol.

Important consequence:

- newly created or changed rules can take up to about 2 seconds before evaluation reflects the latest state.

The candle cache is refreshed asynchronously. If cached candle data exists, it can still be used while a background refresh is started.

### Which rules are eligible to run

The executor only evaluates a rule if:

- `enabled` is true, and
- either it is a root rule, or it is a child rule that has already been armed.

A child rule is considered armed only when:

- `activatedAt` is not null, and
- `activatedQuantity` is not null and greater than zero.

This is why child rules do not run immediately after being saved. They only become active after their parent triggers.

### Condition evaluation logic

The executor supports these metric types:

- `PRICE`
  - compares current quote price against the target.
- `AVERAGE_COST`
  - compares the account position average cost against the target.
- `PERCENT_CHANGE`
  - calculates percentage move from `previousClose` and compares it to the stored target.
- `MOVING_AVERAGE`
  - calculates SMA for the configured lookback and compares it.
- `MOVING_AVERAGE_CROSSOVER`
  - compares current and previous fast/slow SMAs to detect a crossover event.
- `BOLLINGER_BAND`
  - triggers when current price touches the configured upper or lower band.
- `STOP_LOSS`
  - calculates a stop price from the stored activation reference price and target percentage.

The helper `SmartRuleIndicatorCalculator` provides:

- `calculateSma()`
- `calculatePreviousSma()`
- `calculateBollingerBands()`

### Edge-triggered behavior

Rules do not fire on every matching tick. The executor uses the stored `condition_met` flag as a latch.

Trigger rule:

- if the condition is currently true and was previously false, the rule fires once
- if the condition stays true across later ticks, it does not keep firing repeatedly
- if the condition later becomes false, the latch is cleared, allowing a future true transition to fire again

This false-to-true transition rule is one of the most important parts of the backend behavior.

### Quantity resolution when a rule triggers

When a rule fires, the executor determines how many shares to trade.

For `BUY` rules:

- the quantity is the stored fixed quantity

For `SELL` rules:

- it first prefers the rule's `activatedQuantity` if the rule was armed by a parent
- otherwise it falls back to the current position quantity
- then it applies quantity mode:
  - `ALL`: sell everything available
  - `HALF`: sell half, with a minimum of 1 share
  - `FIXED`: sell only if the requested fixed quantity is available

If the computed quantity is zero or negative, the trigger is skipped and no order is placed.

### What happens on trigger

When a rule triggers successfully, the executor:

1. Places an internal market order asynchronously using `InternalOrderClient`.
2. Sets `lastTriggeredAt` on the rule.
3. If the triggered rule is a child sell rule, reduces its `activatedQuantity` by the quantity just sold.
4. If that child sell rule has exhausted its activated quantity, clears:
   - `activatedQuantity`
   - `activatedAt`
   - `activationReferencePrice`
   - `conditionMet`
5. Arms all direct child rules by setting:
   - `activatedAt = now`
   - `activationReferencePrice = currentPrice`
   - `activatedQuantity = executed quantity`
   - `conditionMet = false`
6. Updates `updatedAt` and saves the mutated rule state.

The internal order call is sent to:

- `POST /internal/orders`

The request includes:

- user ID
- account ID
- symbol
- side
- quantity
- idempotency key of the form `smart-rule-{ruleId}-{uuid}`

### Market data and candle dependencies

Not every rule needs historical candle data.

The executor fetches candles only when at least one matched rule for the symbol uses:

- `MOVING_AVERAGE`
- `MOVING_AVERAGE_CROSSOVER`
- `BOLLINGER_BAND`

`FauxnanceCandleClient` calls `/candles/{symbol}` and retries transient failures up to 5 times with exponential backoff.

## Frontend and backend capability mismatch

The current frontend intentionally exposes only a subset of the backend smart-rule engine.

Backend supports but frontend does not currently expose:

- `BOLLINGER_BAND`
- `STOP_LOSS`
- standard deviation input for Bollinger rules
- direct pause/resume management
- editing existing rules

That means a future frontend enhancement could unlock more backend behavior without major order-service or executor-service redesign.

## Current limitations and gaps

This README reflects the current implementation, including several visible gaps:

- No edit flow for existing saved rules.
- No pause/resume toggle in the UI, even though saved rules display enabled state.
- No inline field-level validation messages; validation feedback is toast-based.
- No child-level delete API action; children are removed only before saving or indirectly when the root rule is deleted.
- `BOLLINGER_BAND`, `STOP_LOSS`, and standard deviation controls exist in the model but are not currently supported by the builder UI.
- The unit test file is currently only a basic component creation test and does not cover validation, payload transformation, nesting, or error handling.
- The current test file appears outdated relative to the live service method names used by the component.

## Related implementation files outside this folder

- `src/app/core/trade/services/smart-rule.service.ts`
- `src/app/core/trade/models/smart-rule.models.ts`
- `src/app/app.routes.ts`
- `e2e/smart-rules.spec.ts`

## Summary

In its current form, Smart Rules is a customer-only frontend for building and submitting automated trading rule trees. It supports root rules and nested follow-up rules, validates rule combinations in the browser, uppercases and normalizes payloads before submission, fetches existing saved rules from the backend, and allows deletion of saved root rules.