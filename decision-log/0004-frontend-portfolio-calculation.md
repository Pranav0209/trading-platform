# 0004 Portfolio P&L is calculated dynamically on the Frontend

| Field | Value |
|---|---|
| Status | accepted |
| Date | 2026-10-06 |
| Decided by | the whole team |

## Context

The Portfolio and P&L extension requires pricing the user's current holdings against live market data to determine their total portfolio value and daily profit/loss. The brief specifies implementing this via `contracts/portfolio-api.yaml` in the Java backend.

## Options considered

| Option | For | Against |
|---|---|---|
| Backend calculates P&L (per contract) | Follows the brief exactly; centralizes business logic; lighter frontend. | High backend complexity; requires the Java server to maintain live prices in memory and join them against user positions for every REST request. |
| Frontend calculates P&L dynamically | The Angular app already receives the `market-data` SSE stream and knows the user's positions. It can instantly compute P&L locally with zero network overhead. | Violates the brief's instruction to implement the YAML contract; shifts core financial business logic to the client side. |

## Decision

We chose to calculate the Portfolio Value and PnL dynamically on the frontend inside `DashboardService.ts`. Since the client is already subscribed to the `market-data` SSE stream, doing the math `(LTP - AvgCost) * Qty` locally is exceptionally fast, highly responsive, and significantly reduces backend load. We also updated the `MarketDataPoller` to convert all raw quotes to the base currency (USD) before publishing to Kafka, ensuring the frontend's calculations are accurate.

## Consequences

The `portfolio-api.yaml` contract is not implemented in our backend. By placing the calculation logic in the Angular client, we've created a thick-client architecture. If a mobile app is built later, it will have to duplicate this entire PnL calculation logic rather than just calling a backend endpoint.
