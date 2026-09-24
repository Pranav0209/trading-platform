# Story 8 integration test checklist

This verifies configuration-only wiring between Auth service and Trade REST API.

## Preconditions

- Root .env exists and contains the same JWT_SECRET value used by both services.
- Root orchestration is running from infra/docker-compose.yml.
- Trade REST API is running with JWT_SECRET from environment.

## 1) Obtain token from Auth service

1. Register and/or log in through auth service on port 3000.
2. Capture accessToken from the login response.

Expected:
- HTTP 200 from login.
- accessToken present.

## 2) Call protected Trade API route with valid token

1. Call a protected route such as GET /api/v1/accounts/{id} with Authorization: Bearer <accessToken>.

Expected:
- Request is authenticated and does not return AUTH-401.

## 3) Confirm refusal with no token

1. Call the same protected route without Authorization header.

Expected:
- HTTP 401 / AUTH-401.

## 4) Confirm refusal with wrong signing key

1. Create a forged token signed with a different secret than JWT_SECRET.
2. Call the same protected route with that forged token.

Expected:
- HTTP 401 / AUTH-401.

## Review note

No Java source changes are required for this integration. Alignment is done through environment configuration only.
