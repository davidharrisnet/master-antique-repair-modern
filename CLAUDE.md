# CLAUDE.md

Guidance for Claude Code in this repository.

## What this repository is

Phase 2 of the MasterAntiqueRepair modernization: the legacy ASP.NET Web Forms repair-shop application rebuilt as a
Spring Boot 4.1.1 (Java 21) backend with an Angular front end, on **PostgreSQL** (not Oracle; decided 2026-09-23).

- `backend/`: the Spring Boot project (Gradle Kotlin DSL, package `com.masterantique.backend`). So far only the
  **model** layer: JPA entities for the migrated tables, repositories, and `LoginService` with the forced password
  change on first login. No controller (REST API), no Spring Security yet.
- `frontend/`: the Angular app, not started.

## Where the plans and the database live

Everything that planned and produced this code is in the companion repository
**`~/dev/claude_work/claude_modernization`** (reading it may need a one-time folder permission):

| What | Where (in `claude_modernization`) |
|---|---|
| How the backend was built, how to run and rebuild it | `docs/phase2/model/MODEL_PLAN.md` |
| How to connect to the database (logins, network routes, Spring Boot) | `docs/phase1/dbmigrate/iteration5/PostgreSQLDatabaseGuide.html` |
| The database itself: iteration 5, which builds container `mar-postgres` | `docs/phase1/dbmigrate/iteration5/README.md`, `tools/phase1/dbmigrate/iteration5/CLAUDE.md` |
| Project-wide guidance | `CLAUDE.md` |

## Rules

- **When asked to run the backend demonstration or rebuild the backend, first read
  `~/dev/claude_work/claude_modernization/docs/phase2/model/MODEL_PLAN.md` and follow it** (section 2 to run the
  demonstration, section 5 to rebuild the code).
- **The backend needs iteration 5's database** (container `mar-postgres`, database `masterantique`). If
  `docker ps -a` shows no `mar-postgres`, say so and stop: the database is built from `claude_modernization`
  ("Repeat iteration 5." in a session there), not from this repository.
- **Git is read-only for Claude** (a hook enforces it): never add, commit, push, rm, mv or reset; the user commits.
- **No secrets in source.** The database password comes only from the `MAR_DB_PASSWORD` environment variable; never
  write it into a file.
- **The schema belongs to the migration.** Keep `spring.jpa.hibernate.ddl-auto=validate`; if validation fails, fix the
  entity, never the setting or the database.
- **The `demo` profile changes real data** (it sets a user's password). Run it only against a test copy of the
  database, as `MODEL_PLAN.md` describes.
- Build and test with the wrapper: `cd backend && ./gradlew build` (includes the unit tests, no database needed).
