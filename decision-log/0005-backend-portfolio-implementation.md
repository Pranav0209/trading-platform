# 0005 Backend Portfolio Implementation supersedes Frontend Calculation

| Field | Value |
|---|---|
| Status | accepted (supersedes 0004) |
| Date | 2026-10-07 |
| Decided by | the whole team |

## Context

In decision `0004-frontend-portfolio-calculation.md`, the team previously decided to calculate the Portfolio P&L dynamically on the frontend using the `market-data` SSE stream. However, the `portfolio-pnl.md` sprint requirement explicitly mandates that the portfolio valuation must be a backend module serving specific REST API endpoints (`/api/v1/portfolio/...`). This is necessary because the backend must be the authoritative source of truth for financial statements, and the frontend should not be responsible for securely handling partial pricing failures, batching to respect the strict Fauxnance 2000 request quota, and performing server-side access control.

## Options considered

| Option | For | Against |
|---|---|---|
| Retain Frontend Calculation (0004) | Extremely fast, zero network overhead. Uses existing SSE stream. | Violates the explicit `portfolio-pnl.md` contract. Not scalable to other clients (e.g., mobile apps). Cannot securely and globally manage the Fauxnance rate limit. |
| Implement Backend Endpoints | Fulfills the strict contract. Centralizes business logic. Secures the Fauxnance API key and rate limit across all users. Provides a definitive system of record. | Higher backend complexity. Requires writing new services and clients to batch Fauxnance requests and calculate P&L. |

## Decision

We will implement the missing backend endpoints as specified by `contracts/portfolio-api.yaml`. We will build a `PortfolioService` and `FauxnanceQuoteClient` to batch quote requests to Fauxnance (max 25 symbols per call) and calculate cost basis, market value, and unrealised P&L on the server. The frontend will consume these endpoints for the formal Portfolio statement, while continuing to use the SSE stream for the live trading widgets (like the Order Ticket). 

## Consequences

Decision `0004-frontend-portfolio-calculation.md` is officially superseded. We must build the backend endpoints to fulfill the contract. The UI will be updated to fetch data from these new endpoints for the Portfolio view, ensuring it properly handles and displays stale or partial pricing markers as dictated by the specification. This brings us back into compliance with the sprint requirements.
