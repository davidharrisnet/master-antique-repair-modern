# master-antique-repair-modern

Phase 2 of the MasterAntiqueRepair modernization: the legacy ASP.NET Web Forms repair-shop application
(`master-antique-repair`) rebuilt as a Spring Boot REST API with an Angular front end. The migration planning,
data-migration tooling and reports live in the companion repository `claude_modernization`.

**Status:** the Spring Boot service in `model/oracle/` connects to the database migrated to **Oracle AI Database 26ai
Free**, maps its tables with JPA, contains the sign-in logic for the first login of migrated users, and offers a REST
API with a Swagger page for everything the old application let each kind of user do, except signing in. There is no
web security or front end yet. The details are in [model/oracle/README.md](model/oracle/README.md).

## Running the API

Needs Java 21, Docker and the migrated database (container `mar-oracle`; if it does not exist, open
`claude_modernization` and type `Run import-oracle`). Then:

```
cd model/oracle
scripts/run-api.sh            # until Ctrl+C; or `start` / `stop` for the background
```

and open `http://127.0.0.1:8080/swagger-ui.html`. No password to type: the script keeps the database logins' passwords
in a private file on your machine, `~/.config/mar/oracle.env`, outside every repository.

## Working with Claude Code

Open this repository in VS Code or a terminal with Claude Code and type `Run model-oracle` (a demonstration on a
temporary copy of the database: connection check and the first-login password change) or `Run import-controller`
(build, unit tests and a smoke test of the API against `mar-oracle`). The first time, VS Code may ask for permission
to read the `claude_modernization` folder; allow it. Guidance for Claude Code is in `CLAUDE.md`.

## Layout

```
model/oracle/   Spring Boot 4.1.1 service on Oracle AI Database 26ai Free (Java 21, Gradle Kotlin DSL)
frontend/       Angular app (not started)
```

## Next steps

- Spring Security around `LoginService` (sign-up, sign-in, password resets, sign-out).
- A real `IdentityCheck`, lockout on repeated failures (`access_failed_count`, `lockout_end_date_utc`), rate limiting.
- The Angular front end in `frontend/`.

## License

Apache License 2.0, see `LICENSE`.
