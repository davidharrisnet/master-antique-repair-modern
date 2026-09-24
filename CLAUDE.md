# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this repository is

Phase 2 of the MasterAntiqueRepair modernization: the legacy ASP.NET Web Forms repair-shop application rebuilt as a
Spring Boot 4.1.1 (Java 21) backend with an Angular front end. The data migration (in the companion repository
`claude_modernization`) produced the same database twice, so there is one backend per database:

| Folder | Database | Role | Instructions (read first) | Prompt |
|---|---|---|---|---|
| `backend/postgresql/` | PostgreSQL 16, container `mar-postgres` | **The Phase 2 backend** (PostgreSQL decided 2026-09-23) | `backend/postgresql/CLAUDE.md` | `Run backend-postgresql` |
| `backend/oracle/` | Oracle AI Database 26ai Free, container `mar-oracle` | Proof of concept for the Oracle the brief names | `backend/oracle/CLAUDE.md` | `Run backend-oracle` |
| `frontend/` | | The Angular app, not started | | |

Both backends are so far only the **model** layer (JPA entities for the migrated tables, repositories, `LoginService`
with the forced password change on first login, 6 unit tests); no controller (REST API) and no Spring Security yet.

## Routing

- **Before running, changing or answering questions about a backend, read that backend's `CLAUDE.md` and follow it.**
  It holds everything specific to its database: the run prompt step by step, the delivered database, settings, commands,
  layout, how the code works, gotchas, and how to rebuild it.
- **`Run backend-postgresql`** → `backend/postgresql/CLAUDE.md`, section "Run backend-postgresql".
- **`Run backend-oracle`** → `backend/oracle/CLAUDE.md`, section "Run backend-oracle".
- Both run on a temporary copy of their database, built with `claude_modernization`'s import tool, and never change the
  delivered container.

## Rules for the whole repository

- **The backends are independent.** Each must build and run with every other folder removed: never copy from, refer to
  or depend on the other backend. A change needed in both is made twice, each in its own database's terms.
- **Git is read-only for Claude** (a hook enforces it): never add, commit, push, rm, mv or reset; the user commits.
- **No secrets in source.** The database password comes only from the `MAR_DB_PASSWORD` environment variable; never
  write it into a file, a command line or the output.
- **The schema belongs to the migration.** Keep `spring.jpa.hibernate.ddl-auto=validate`; if validation fails, fix the
  entity, never the setting or the database.
- **The `demo` profile changes real data** (it sets a user's password): only ever against a temporary copy.
- Build output (`build/`, `.gradle/`, the VS Code Java extension's `bin/`) is gitignored; the Gradle wrapper jar is kept.

## Where the plans and the databases live

Everything that planned and produced this code, and the databases themselves, is in the companion repository
**`~/dev/claude_work/claude_modernization`** (reading it may need a one-time folder permission):

| What | Where (in `claude_modernization`) |
|---|---|
| Project-wide guidance | `CLAUDE.md` |
| How each backend was built and how to rebuild it | `docs/phase2/model/postgresql/CLAUDE.md`, `docs/phase2/model/oracle/CLAUDE.md` |
| The databases: import tools that build `mar-postgres` / `mar-oracle` (`Run import-postgresql`, `Run import-oracle`) | `tools/phase1/dbmigrate/import-postgresql/`, `tools/phase1/dbmigrate/import-oracle/` (each with its `CLAUDE.md`) |
| How to connect to each database (logins, network routes, Spring Boot) | `docs/phase1/dbmigrate/import-postgresql/PostgreSQLDatabaseGuide.html`, `docs/phase1/dbmigrate/import-oracle/OracleDatabaseGuide.html` |

## Next steps (from README)

REST API with server-side validation and Spring Security around `LoginService`; a real `IdentityCheck`; lockout
(`access_failed_count`, `lockout_end_date_utc`) and rate limiting; audit logging in the legacy `audit_logs` format;
the Angular front end in `frontend/`. These are designed once for Phase 2 on PostgreSQL; an Oracle variant follows only
if the Oracle path is taken further.
