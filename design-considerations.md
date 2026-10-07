# Design Considerations: Customer Preferences

## Core Storage
We already have the core Customer Preference stored in `auth.users` via the `notification_channel` column:
`notification_channel VARCHAR(20) NOT NULL DEFAULT 'EMAIL' CHECK(notification_channel IN ('EMAIL','PUSH'))`

**Decision**: Do not create a separate preferences table just to store the notification channel. That would duplicate data unnecessarily. The Preferences module is essentially responsible for exposing and updating this existing preference safely.

## Suggested API
- `GET /api/preferences`
- `PUT /api/preferences`

The customer-facing API should not use `GET /api/preferences/{accountId}`. Instead, every read and write is scoped to the account in the verified token.

### Why this is better
The backend should essentially do:
1. Extract account/user ID from JWT
2. Load from `auth.users`
3. Read/update `notification_channel`

**Security Rule**: Never trust an account ID supplied by the Angular application to decide whose preference to modify.

## Channel Definitions
- **EMAIL**: Send notification through email delivery
- **PUSH**: Show notification inside the Angular trading application (in-app notification, rather than actual mobile push)

## Architectural Boundaries
Because `auth.users` belongs to the Auth service/schema, the Preferences module should not directly modify arbitrary auth data without an agreed ownership boundary. If Auth already owns `auth.users`, the cleanest design is for Preferences to expose the preference through the existing service/interface rather than having another module freely write that table.

Notification flow:
1. Trade Event
2. Notifications module consumes it
3. GET customer preference
4. Route to EMAIL (Email delivery) or PUSH (In-app notification) based on result
