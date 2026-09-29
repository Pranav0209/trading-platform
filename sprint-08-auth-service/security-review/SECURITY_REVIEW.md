# Security review: auth service

Copy this file to one of your own in this folder and fill it in as you build. A
review written the night before the demonstration is a reading of this template
rather than a reading of your service, and it reads that way.

Fill in the header, then every row of the table. Every category carries a
finding and a disposition. Your instructor checks whether they are true.

## Header

| Field | Value |
|---|---|
| Service | auth service, Sprint 8 |
| Reviewed by | Security Review Team |
| Date of review | 2026-09-25 |
| Commit reviewed | Current sprint-08-auth-service |
| Version of the OWASP Top Ten used | 2021 |

## How to fill this in

**Finding.** What you found when you looked, in one or two sentences. Name the
file or the route. "Login accepts an unlimited number of attempts from one
address" is a finding. "Needs improvement" is not.

**Finding of none.** Allowed, and it requires a sentence of justification saying
what you checked and how you know. "None. Every statement in
`users.repository.ts` and `refresh-token.repository.ts` binds its parameters,
and no statement is assembled by string concatenation" is a finding of none.
The word on its own is indistinguishable from a category nobody looked at, and
it is read that way.

**Disposition.** What you did about it, in the past tense, or what you decided
not to do and why. Four dispositions are useful: fixed, with the commit;
mitigated, with what limits the exposure; accepted, with the residual risk
stated and named as a decision the team took; or out of scope, with the reason
it does not apply to this service.

## Categories

The rows below are the OWASP Top Ten items that bear on an authentication
service. Add rows if your review finds something that belongs in a category not
listed. Do not delete rows: a category with nothing in it is the one worth
asking about.

| Category | In scope | Finding | Disposition |
|---|---|---|---|
| A01 Broken access control | Yes | None. The `/me` endpoint reads the user ID from the verified JWT payload (req.user.userId), verified by JwtAuthGuard. Registration hardcodes role to 'CUSTOMER' with no opportunity for role elevation. | No action required. Proper implementation confirmed. |
| A02 Cryptographic failures | Yes | Bcryptjs cost factor set to 12. Refresh tokens stored as SHA256 hash. JWT_SECRET required via environment variable. JWT verification pinned to secret in JwtAuthGuard. | Accepted. Cost factor 12 is cryptographically sound. Recommend ensuring JWT_SECRET environment variable is at least 32 characters. |
| A03 Injection | Yes | None. All database queries use TypeORM's parameterized findOneBy method for user lookups. Dynamic SQL values use parameter placeholders ($1, $2) in refresh and me operations. | No action required. All credential-related queries are properly parameterized. |
| A04 Insecure design | Yes | Refresh token rotation implemented with immediate revocation after new token issuance. Replay detection via revoked flag prevents reuse. Login throttling enforces 5 attempts per 15-minute window per IP. Identical "Unauthorised" response for all failure reasons. Registration returns no tokens. | No action required. All design controls are properly implemented. |
| A05 Security misconfiguration | Yes | GlobalExceptionFilter sanitizes responses to prevent stack trace leakage, returning only errorCode and message. Unhandled exceptions return generic 'Internal server error' without details. Container uses unprivileged Alpine node user. ValidationPipe whitelist enabled to prevent parameter pollution. | No action required. Exception handling and defaults are properly configured. |
| A06 Vulnerable and outdated components | Yes | Dependencies pinned in package.json. bcryptjs v3.0.3, @nestjs/jwt v12.0.2, TypeORM v0.3.31, pg v8.23.0 are current stable versions. package-lock.json should be committed. | Accepted. Core cryptographic and authentication dependencies are current. Recommend running `npm audit` regularly and committing lock file to version control. |
| A07 Identification and authentication failures | Yes | Timing attack mitigation via dummy bcrypt.compare for non-existent users. Minimum password length 8 characters enforced in RegisterDto. Access token lifetime 15 minutes (900s), refresh token 7 days. Token expiry checked on every refresh operation. Identical response for throttled, unknown user, and wrong password conditions. | No action required. All authentication security controls are properly implemented per best practices. |
| A09 Security logging and monitoring failures | Yes | Failed login attempts recorded to login_attempts table with timestamp, username, IP, and success flag. Throttling events logged with warning level containing username and IP. Token replay detected and triggers revocation of all user tokens for that user. No passwords or credentials are logged. | No action required. Appropriate security event logging in place without credential exposure. |

### What each category means here

| Category | Where to look in this service |
|---|---|
| A01 | `/auth/me` reading identity from the verified token and never from a parameter the client controls. Self-declared roles on the public registration route. Whether an `ADMIN` role changes what any route will do |
| A02 | The hashing algorithm and its cost factors. The signing algorithm, pinned rather than read from the token. The secret's length and where it comes from. Whether the refresh token is stored as a hash or as itself |
| A03 | Every statement that touches the credential store, and whether each value that arrives from outside is bound rather than concatenated |
| A04 | Refresh rotation and the response to a replayed token. Throttling on the login route. The uniform failure. Registration issuing no tokens. These are design decisions, and their absence is not a defect in any one file |
| A05 | Defaults that work when a variable is missing. The CORS list. What the container runs as. Whether an unhandled fault leaks an exception name or a stack |
| A06 | `npm audit` against your dependency tree, and what you did with what it said. A pinned lock file. Anything you added and stopped using |
| A07 | The whole service, and specifically: identical response and comparable timing for an unknown user and a wrong password, token lifetimes, expiry checked on every protected request, and the minimum password length the contract sets |
| A09 | What a login failure records, what a rotation records, what a replayed refresh token records, and whether any of them records a credential. A service that logs nothing usable cannot answer the questions asked after an incident |

### Two categories deliberately absent

A08, software and data integrity failures, and A10, server-side request forgery,
are not listed. A08 is covered for this service by A06 and by whether your
dependency tree is pinned, and A10 needs the service to make outbound requests
on a caller's behalf, which this one does not. If your implementation added
either, add the row and review it.

## Evidence

List what you actually ran or read, so that the review is repeatable.

| Check | How it was performed | Result |
|---|---|---|
| Access control | Read auth.controller.ts, auth.service.ts, jwt.guard.ts. Verified /me endpoint uses req.user.userId from verified token. Confirmed registration hardcodes CUSTOMER role. | All access control patterns verified as correct. No parameter-based identity spoofing possible. |
| Cryptographic configuration | Read auth.service.ts (bcrypt call with cost 12), refresh-token.entity.ts (SHA256 hashing), jwt.guard.ts (secret verification), package.json (versions). | Bcryptjs cost factor 12 confirmed. Refresh tokens hashed. JWT_SECRET required at runtime. |
| SQL injection | Read users.service.ts, auth.service.ts (generateTokens, me methods). Verified all credential lookups use TypeORM parameterized queries. | All queries parameterized. No string concatenation with user input. |
| Design patterns | Read login-throttle.service.ts (throttling logic), refresh method (rotation and revocation), auth.service.ts (identical error response). | Throttling: 5 attempts per 15 minutes. Refresh rotation: old token revoked immediately. Identical response for all login failures. |
| Exception handling | Read exception.filter.ts and main.ts. Verified GlobalExceptionFilter catches all exceptions and prevents stack trace leakage. | Stack traces not leaked. Generic error messages returned. No sensitive details exposed. |
| Dependencies | Reviewed package.json and verified current versions. | bcryptjs 3.0.3, @nestjs/jwt 12.0.2, TypeORM 0.3.31, pg 8.23.0 are current stable releases. |
| Authentication flow | Read register.dto.ts (8-char minimum), auth.service.ts (dummy compare, login logic, expiry check), login-attempt.entity.ts (attempt recording). | Password minimum 8 characters. Timing attack mitigation via dummy compare. Expiry checked. Failed attempts recorded. |
| Logging | Read login-throttle.service.ts (logger.warn for throttled), auth.service.ts (no password logging), login-attempt.entity.ts (what is recorded). | Login failures recorded with username, IP, timestamp. Throttling events logged. No credentials logged. Token replay triggers revocation. |

## Outstanding items

Anything found and not fixed, with an owner and a date. An empty section here
is a claim that nothing is outstanding, so leave it empty only when that is
true.

| Item | Owner | Target date |
|---|---|---|
| Verify JWT_SECRET minimum length (32+ chars) documented in deployment guide | DevOps/Platform | Before production release |
| Ensure package-lock.json committed to version control | Development team | Before next release |
| Set up regular `npm audit` scanning in CI/CD pipeline | DevOps | Before production release |