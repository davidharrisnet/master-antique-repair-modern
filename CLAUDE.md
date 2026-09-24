# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this repository is

Phase 2 of the MasterAntiqueRepair modernization: the legacy ASP.NET Web Forms repair-shop application rebuilt as a
Spring Boot 4.1.1 (Java 21) backend with an Angular front end, on **PostgreSQL** (decided 2026-09-23). The data migration
also produced an Oracle database as a proof of concept, and it has its own backend.

- `backend/postgresql/`: the Spring Boot project (Gradle Kotlin DSL, package `com.masterantique.backend`). So far only the
  **model** layer: JPA entities for the migrated tables, repositories, and `LoginService` with the forced password
  change on first login. No controller (REST API), no Spring Security yet.
- `backend/oracle/`: the same model layer on the migrated Oracle database (Oracle AI Database 26ai Free), the Oracle proof
  of concept. **Self-contained:** it must build and run with every other folder removed; never copy from or refer to
  `backend/postgresql/` when working on it (and the reverse). See `backend/oracle/README.md`.
- `frontend/`: the Angular app, not started.

## Where the plans and the database live

Everything that planned and produced this code is in the companion repository
**`~/dev/claude_work/claude_modernization`** (reading it may need a one-time folder permission):

| What | Where (in `claude_modernization`) |
|---|---|
| How the backend was built, how to run and rebuild it | `docs/phase2/model/postgresql/MODEL_PLAN.md` |
| How `backend/oracle/` was built, how to run and rebuild it | `docs/phase2/model/oracle/CLAUDE.md` |
| How to connect to the Oracle database (logins, network routes, Spring Boot) | `docs/phase1/dbmigrate/import-oracle/OracleDatabaseGuide.html` |
| The Oracle database itself: import-oracle, which builds container `mar-oracle` | `docs/phase1/dbmigrate/import-oracle/README.md`, `tools/phase1/dbmigrate/import-oracle/CLAUDE.md` |
| How to connect to the database (logins, network routes, Spring Boot) | `docs/phase1/dbmigrate/iteration5/PostgreSQLDatabaseGuide.html` |
| The database itself: iteration 5, which builds container `mar-postgres` | `docs/phase1/dbmigrate/iteration5/README.md`, `tools/phase1/dbmigrate/iteration5/CLAUDE.md` |
| Project-wide guidance | `CLAUDE.md` |

## Rules

- **The prompt `Run backend-postgresql` runs the backend demonstration. When asked to run it or to rebuild the backend, first read
  `~/dev/claude_work/claude_modernization/docs/phase2/model/postgresql/MODEL_PLAN.md` and follow it** (section 2 to run the
  demonstration, section 5 to rebuild the code).
- **The backend needs iteration 5's database** (container `mar-postgres`, database `masterantique`). If
  `docker ps -a` shows no `mar-postgres`, say so and stop: the database is built from `claude_modernization`
  ("Repeat iteration 5." in a session there), not from this repository.
- **The prompt `Run backend-oracle` runs the Oracle backend demonstration. When asked to run it or to rebuild
  `backend/oracle/`, first read `~/dev/claude_work/claude_modernization/docs/phase2/model/oracle/CLAUDE.md` and follow it**
  (section 2 to run, section 5 to rebuild). It needs import-oracle's database (container `mar-oracle`); if
  `docker ps -a` shows no `mar-oracle`, say so and stop: it is built from `claude_modernization` (`Run import-oracle`).
  Build and test with `cd backend/oracle && ./gradlew build`.
- **Git is read-only for Claude** (a hook enforces it): never add, commit, push, rm, mv or reset; the user commits.
- **No secrets in source.** The database password comes only from the `MAR_DB_PASSWORD` environment variable; never
  write it into a file.
- **The schema belongs to the migration.** Keep `spring.jpa.hibernate.ddl-auto=validate`; if validation fails, fix the
  entity, never the setting or the database.
- **The `demo` profile changes real data** (it sets a user's password). Run it only against a test copy of the
  database, as `MODEL_PLAN.md` describes.
- Build and test with the wrapper: `cd backend/postgresql && ./gradlew build` (includes the unit tests, no database needed).

## Commands

All from `backend/postgresql/` (Java 21; the wrapper downloads Gradle):

```
./gradlew build                                         # compile + unit tests (no database)
./gradlew test --tests '*LoginServiceTest'              # one test class
./gradlew test --tests '*LoginServiceTest.someMethod'   # one test method
./gradlew bootRun                                       # needs MAR_DB_PASSWORD and a route to mar-postgres
```

Connection settings are environment variables with defaults in `application.properties`: `MAR_DB_HOST` (`localhost`),
`MAR_DB_PORT` (`5433`, the localhost proxy; `5432` for a container address or the shared Docker network),
`MAR_DB_USER` (`mar_app`), `MAR_DB_NAME` (`masterantique`), `MAR_DB_PASSWORD` (required, no default). Read the
password with `read -rsp`, never put it on a command line that is logged or into a file.

## Architecture

- **Profiles decide what runs at start-up.** Without a profile: `DatabaseCheck` (a `CommandLineRunner`) logs the
  connection and row counts, and `RejectingIdentityCheck` makes every first-password change fail. With `demo`:
  `FirstLoginDemo` (console walk-through, driven by `demo.*` properties) and `DemoIdentityCheck` (accepts
  `demo.issued-code`, default `DEMO-1234`) replace them. Keep the `@Profile("!demo")` / `@Profile("demo")` pairs
  in step; exactly one `IdentityCheck` bean must exist. Demo-only code stays in the `demo` package.
- **`LoginService` is the only business logic.** `login` returns `LoginResult` (`OK`, `MUST_CHANGE_PASSWORD`,
  `INVALID`, same answer for unknown user and wrong password, with a dummy bcrypt compare to equalise timing).
  `changePassword` requires `IdentityCheck.verify`, the password policy (`checkPolicy`: 12+ chars, confirm match,
  must not contain the username), then stores a `{bcrypt}` delegating-encoder hash and a new security stamp.
  Errors are `IllegalArgumentException` with user-facing messages. Log ids, never passwords or comment text.
- **Entities mirror the migrated schema exactly** (snake_case columns, explicit `@Column` names and lengths so
  `validate` passes). Conventions: `users` holds customers, employees and managers in one `AppUser` entity with
  `discriminator` as a plain string column (no JPA inheritance); soft delete via `deleted_at` (NULL = active);
  `TicketState` is stored `ORDINAL` (0/1/2, so never reorder the enum); `@ManyToOne` is `LAZY`; timestamps are
  `LocalDateTime` (columns are `TIMESTAMP` without time zone); ids are `IDENTITY`. `open-in-view` is off, so load
  what you need inside `@Transactional` service methods.
- **User lookup is case-insensitive** (`AppUserRepository.findActiveByName`, `lower(name)` on both sides to hit
  the partial index `ix_users_name_active`). Keep that form for new sign-in queries.
- Every migrated user has `password_hash` NULL and `must_reset_password` true, so all real logins currently go
  through `MUST_CHANGE_PASSWORD`.
- Unit tests mock `AppUserRepository` and `AppUser` with Mockito (no Spring context, no database). There are no
  integration tests; the running app's start-up (`DatabaseCheck`) is the schema check.

## Next steps (from README)

REST API with server-side validation and Spring Security around `LoginService`; a real `IdentityCheck`; lockout
(`access_failed_count`, `lockout_end_date_utc`) and rate limiting; audit logging in the legacy `audit_logs` format;
the Angular front end in `frontend/`.
