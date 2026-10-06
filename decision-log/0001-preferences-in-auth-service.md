# 0001 Preferences are managed in Auth Service sharing the User table

| Field | Value |
|---|---|
| Status | accepted |
| Date | 2026-10-06 |
| Decided by | the whole team |

## Context

We need a place to store customer preferences (like notification channel and email address). The extension brief suggests placing this module inside the Trade REST API (`order-service`). However, our `auth-service` (Node.js) already manages the `auth.users` table, which holds the customer's email address as part of their identity.

## Options considered

| Option | For | Against |
|---|---|---|
| Build in Trade API with a separate `preferences` table | Follows the brief strictly; keeps domains cleanly separated using Java interfaces. | Duplicates the email address (creating a risk of desync), or requires complex synchronous HTTP calls between Java and Node.js. |
| Build in Auth Service, expanding the `users` table | Single source of truth for identity and contact data; much faster to build. | Tightly couples the Trade API (Java) to the Auth database schema, violating the "no module reaching into another's tables" constraint. |

## Decision

We chose to build the preferences logic inside the `auth-service` and share the `auth.users` table with the Trade API. The `order-service`'s `NotificationListener` reads the `users` table directly via JPA to fetch the email and channel. Identity and notification contact details are so closely related that separating them artificially for the sake of the brief felt like an anti-pattern. 

## Consequences

This choice deviates from the sprint brief's architectural constraint of modular boundaries inside the monolith. A schema change to the `users` table in `auth-service` might immediately break the `NotificationListener` in `order-service`. We accepted this tight database coupling in exchange for avoiding duplicated email records across two services.
