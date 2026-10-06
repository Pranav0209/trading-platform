# 0002 Use EmailJS REST API for sending notifications

| Field | Value |
|---|---|
| Status | accepted |
| Date | 2026-10-06 |
| Decided by | the whole team |

## Context

The Customer Notifications module needs to deliver alerts to the customer via the channel specified in their preferences (EMAIL). We needed a reliable way to send emails from the Java `order-service` without setting up a full SMTP server or managing complex credentials locally.

## Options considered

| Option | For | Against |
|---|---|---|
| Native JavaMail with a real SMTP server | Robust, standard enterprise solution; keeps all templating logic in-house. | High setup friction for a local dev environment; requires managing secure SMTP credentials. |
| EmailJS REST API | Simple HTTP POST using `RestTemplate`; completely stateless; no SMTP configuration required in the Java app. | Introduces a third-party dependency; subject to external rate limits; email templates must be managed outside our codebase in the EmailJS dashboard. |

## Decision

We chose to use the EmailJS REST API. It is exceptionally fast to integrate and keeps the Java backend lightweight. We pass a dynamic JSON payload containing the `subject` and `message` to a single EmailJS template, avoiding the need to hardcode multiple templates.

## Consequences

We rely entirely on the EmailJS platform for email delivery and template formatting. If EmailJS goes down or we hit rate limits, the `RestTemplate` call will fail. Since we have not yet implemented a dead-letter queue or retry mechanism in our Kafka listener, network failures to EmailJS will result in permanently lost customer notifications.
