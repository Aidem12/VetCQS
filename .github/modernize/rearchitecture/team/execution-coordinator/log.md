## [follow-up] Blind cash and session hardening
- Added server-derived blind cash closing with pessimistic open-turn locking and sale association.
- Existing PostgreSQL volume needed additive migration; init scripts are not replayed.
- Seed users are inactive with non-password markers, preventing accidental login.
- Caja now retains JSESSIONID cookies without changing the cobro visual layout.
- Learnings consumed: (none)
