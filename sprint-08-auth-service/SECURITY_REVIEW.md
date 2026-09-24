# Sprint 8 security review notes

## JWT secret decision

Decision: rotate the previously published development JWT secret and stop reusing the old placeholder value.

Rationale:
- Sprint 8 introduces a real token issuer instead of a fixture, so the shared secret now protects active signed tokens.
- Reusing a known placeholder weakens local trust boundaries between services.
- Rotation is low-cost because all local services already read JWT_SECRET from environment configuration.

Action:
- Use a new 32+ character development secret in root .env before bringing the stack up.
- Keep only placeholder values in .env.example.

## Claim exposure reminder

JWT payload is base64-encoded, not encrypted. No sensitive personal data should be added to access-token claims.
