# Blind cash and session cookies

Use a server-derived expected cash total and retain the HTTP session cookie in desktop clients.

## What Happened
The existing schema already had `turno` fields but no service/API flow, and the JavaFX client used a stateless `HttpClient`. This follow-up added a pessimistic open-turn lookup, close calculation, and a `CookieManager`-backed client.

## Takeaway
Existing PostgreSQL volumes require explicit additive migration execution; `docker-entrypoint-initdb.d` is only for empty volumes. Keep seed users inactive until an administrator rotates credentials.

## History
- 2026-10-31 (sistema-veterinaria/follow-up): initial
