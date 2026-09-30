# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this repository is

Phase 2 of the MasterAntiqueRepair modernization: the legacy ASP.NET Web Forms repair-shop application rebuilt as a
Spring Boot 4.1.1 (Java 21) service with an Angular front end, on the database the data migration (in the companion
repository `claude_modernization`) produced in **Oracle AI Database 26ai Free**:

| Folder | Database | Instructions (read first) | Prompts |
|---|---|---|---|
| `model/oracle/` | Oracle AI Database 26ai Free, container `mar-oracle` | `model/oracle/CLAUDE.md` | `Run model-oracle`, `Run import-controller` |
| `frontend/` | | The Angular app, not started | |

`model/oracle/` holds the model layer (JPA entities for the migrated tables with their domain rules, repositories,
`LoginService` with the forced password change on first login) and the controller layer (a REST API with Swagger UI
for the non-security actions of the controller contract); no Spring Security yet.

## Routing

- **Before running, changing or answering questions about the model, read `model/oracle/CLAUDE.md` and follow it.**
  It holds everything specific to the database: the run prompts step by step, the delivered database, settings,
  commands, layout, how the code works, gotchas, and how to rebuild it.
- **`Run model-oracle`** → `model/oracle/CLAUDE.md`, section "Run model-oracle" (on a temporary copy of the database,
  built with `claude_modernization`'s import tool; never changes the delivered container).
- **`Run import-controller`** → `model/oracle/CLAUDE.md`, section "Run import-controller" (a smoke test through the API
  on the delivered `mar-oracle`).
- **Starting the API** for a person: `model/oracle/scripts/run-api.sh`.

## Rules for the whole repository

- **`model/oracle/` is self-contained.** It must build and run with every other folder of the repository removed.
- **Git is read-only for Claude** (a hook enforces it): never add, commit, push, rm, mv or reset; the user commits.
- **No secrets in source.** The database password comes only from the `MAR_DB_PASSWORD` environment variable; never
  write it into a repository file, a command line or the output. The one deliberate, local-only exception is the
  password file `~/.config/mar/oracle.env` (outside every git repository, mode 600, random values made on first use
  by `model/oracle/scripts/db-logins.sh`, read by `scripts/run-api.sh`); never print or `cat` it.
- **The schema belongs to the migration.** Keep `spring.jpa.hibernate.ddl-auto=validate`; if validation fails, fix the
  entity, never the setting or the database.
- **The `demo` profile changes real data** (it sets a user's password): only ever against a temporary copy.
- Build output (`build/`, `.gradle/`, the VS Code Java extension's `bin/`) is gitignored; the Gradle wrapper jar is kept.

## Where the plans and the database live

Everything that planned and produced this code, and the database itself, is in the companion repository
**`~/dev/claude_work/claude_modernization`** (reading it may need a one-time folder permission):

| What | Where (in `claude_modernization`) |
|---|---|
| Project-wide guidance | `CLAUDE.md` |
| How the model was built and how to rebuild it | `docs/phase2/model/oracle/CLAUDE.md` |
| How the controller layer was built; the controller contract | `docs/phase2/controller/oracle/CLAUDE.md`, `docs/phase2/controller/CONTROLLER.md` |
| The database: the import tool that builds `mar-oracle` (`Run import-oracle`) | `tools/phase2/dbmigrate/import-oracle/` (with its `CLAUDE.md`) |
| How to connect to the database (logins, network routes, Spring Boot) | `docs/phase2/dbmigrate/import-oracle/OracleDatabaseGuide.html` |

## Next steps (from README)

Spring Security around `LoginService` (the security actions of the controller contract); a real `IdentityCheck`;
lockout (`access_failed_count`, `lockout_end_date_utc`) and rate limiting; the Angular front end in `frontend/`.
