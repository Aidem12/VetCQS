# Modernization Plan: Iteraciones 3 y 4 — Inventario, Caja, Seguridad y CFDI simulado

**Project**: sistema-veterinaria  
**Planning branch**: `pruebas/iteracion-2` (preserved; no main/develop changes)  
**Assessment source**: `.github/modernize/assessment/reports/report-20261031214614/report.json`

## Technical Framework

- **Language**: Java 21
- **Framework**: Spring Boot 3.3.5 / Spring Data JPA
- **Build tool**: Maven
- **Database**: PostgreSQL 16 (Docker Compose; 14 existing tables)
- **Clients**: JavaFX 21 (`cliente-caja`, `cliente-consultorio`)
- **Assessment findings used**: Java 21 is already present; configuration contains
  local database credentials/URLs; Spring Boot port/configuration is not fully
  externalized. No runtime upgrade is planned.

## Overview

This plan incrementally delivers the confirmed Iteration 3 and Iteration 4 scope
without changing the existing cobro/payment visual design. It adds stock
movements and traceability, blind cash-register cuts, mock electronic invoicing,
basic employee authentication and administration, and security/configuration
foundations. Each phase has a database migration, server API work, client
integration, and focused verification before the next phase.

The CFDI feature is explicitly a simulation: it creates and tracks a local mock
invoice/response and never connects to a fiscal provider, sends a real CFDI, or
stores provider credentials. Hardware integration, fiscal certification,
multi-tenant identity, and production deployment are out of scope.

## Migration Impact Summary

| Application | Original Service | New capability | Authentication | Comments |
|---|---|---|---|---|
| servidor | Existing sales/catalog API | Inventory, cuts, users, mock CFDI | Local username/password + roles | Preserve existing endpoints |
| cliente-caja | Existing sale/cobro UI | Stock deduction, replenishment, blind cuts, CFDI | Session login/role checks | Preserve cobro visual design |
| cliente-consultorio | Existing clinical UI | Login and role-aware access | Session login/role checks | Keep clinical workflows intact |
| PostgreSQL | Existing 14-table schema | Additive Iteration 3/4 tables and indexes | N/A | Versioned, reversible migrations |

## Incremental Delivery Strategy

1. **Baseline and safety gate**: freeze current behavior, verify the three Maven
   modules, record API/UI regression cases, and work only on
   `pruebas/iteracion-2` or a new `feature/iteracion-3-4-*` branch.
2. **Iteration 3 inventory**: add products' stock state, immutable movement
   ledger, transactional sale deductions and replenishments, and traceability.
3. **Iteration 3 blind cuts**: add opening/count/expected/difference and payment
   method totals; expose the workflow to caja without redesigning cobro.
4. **Iteration 4 identity/personnel**: add employee records, password handling,
   roles, logical deactivation, login/session behavior, and administrative
   screens/endpoints.
5. **Iteration 4 mock CFDI**: add deterministic mock invoice generation and
   status/traceability screens; no fiscal provider integration.
6. **Security and hardening**: externalize secrets and URLs, prepare HTTPS,
   apply authorization and audit controls, and remediate assessment findings
   without changing the Java 21 baseline.
7. **Verification**: run server tests, client functional/regression checks,
   schema migration checks, and compilation for `servidor`, `cliente-caja`,
   and `cliente-consultorio`.

## Dependencies and Schema/API Boundaries

- Preserve existing sales, payments, catalog, clinical, and WebSocket contracts;
  add versioned additive schema changes and compatibility adapters where needed.
- Inventory depends on the existing product and sale model. A sale deduction and
  its movement ledger entry must commit atomically; insufficient stock must
  reject the whole operation.
- Blind cuts depend on persisted sales/payments and on a completed/open register
  session. The server calculates expected totals; the cashier only submits
  counted cash and cannot edit expected totals.
- Personnel/login depends on an employee table and role assignments. Deactivation
  must prevent new login while preserving historical references.
- Mock CFDI depends on a completed sale and authenticated operator, but not on an
  external fiscal service. Mock identifiers and payloads must be clearly marked
  non-fiscal.
- Database changes must include explicit primary/foreign keys, uniqueness,
  indexes for product/date/operator lookups, audit timestamps, and a rollback or
  documented recovery procedure. Do not rewrite the existing baseline scripts
  destructively.
- New API endpoints must validate input, enforce role authorization, return
  stable error responses, and avoid returning password hashes or secrets.

## Security and Configuration Constraints

- Hash passwords with a modern adaptive password hash; never log or persist
  plaintext passwords. Enforce least-privilege role checks on stock,
  replenishment, cuts, personnel, and invoice operations.
- Logical deactivation is the only employee deletion behavior; preserve operator
  IDs on sales, movements, cuts, and mock invoices.
- Move database password, JDBC URL, server URL, allowed origins, and HTTPS
  settings to environment/profile configuration. Keep `.env.ejemplo`
  documentation-only and never commit real credentials.
- Add HTTPS-ready configuration and secure-cookie/transport guidance, but do not
  claim production TLS until certificates and deployment are supplied.
- Review dependency CVEs after changes and keep Java 21 and Spring Boot 3.3.5
  unless a separately approved upgrade is created.

## Testing and Verification

- Baseline: capture existing sale, cobro, consultorio and health-check behavior.
- Server: unit and controller/service tests for stock atomicity, cut
  calculations, authorization, deactivation, and mock CFDI determinism.
- Database: apply migrations to an empty database and a copy of the current
  database; verify constraints, indexes, seed compatibility, and rollback/
  recovery notes.
- Clients: functional checks for login, role visibility, stock/cut workflows,
  CFDI simulation, and unchanged cobro layout/interaction.
- Build gate: `mvn test`/`mvn package` for `servidor`; compile/package checks for
  both JavaFX clients. Record failures as blockers rather than weakening tests.

## Explicit Limits and Non-Goals

- No real CFDI timbrado, PAC/fiscal provider, certificate, SAT submission, or
  production fiscal compliance claim.
- No payment gateway, card data storage, cash-drawer redesign, or replacement
  of the current cobro visual design.
- No automatic stock purchasing, warehouse optimization, lot/expiry accounting,
  multi-branch inventory, or offline conflict resolution.
- No SSO, MFA, public user registration, or identity-provider integration.
- No Azure resource provisioning, container/deployment work, or Java version
  upgrade in this plan.

## Open Questions & Questionnaire

- [x] Q: Should CFDI connect to a real provider? → A: No; use a simulated/mock
  implementation with no credentials.
- [x] Q: Should the existing cobro/payment design change? → A: No; preserve it
  across the system.
- [x] Q: What branch safety is required? → A: Preserve `pruebas/iteracion-2` or
  create a safe non-main feature branch; do not execute changes on main.
- [x] Q: Are integration/regression checks required? → A: Add baseline,
  regression, and functional verification where feasible, including compilation
  of all three modules.
