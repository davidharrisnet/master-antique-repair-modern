# master-antique-repair-claude

Phase 2 of the MasterAntiqueRepair modernization: the legacy ASP.NET Web Forms repair-shop application
(`master-antique-repair`) rebuilt as a Spring Boot REST API with an Angular front end. The migration planning,
data-migration tooling and reports live in the companion repository `claude_modernization`.

**Status:** the Spring Boot back end is started. It connects to the migrated PostgreSQL database, maps its
tables with JPA, and contains the sign-in logic for the first login of migrated users. There is no REST API,
web security or front end yet. How it was built and how to run the demonstration:
`claude_modernization/docs/phase2/model/MODEL_PLAN.md`.

## Layout

```
backend/     Spring Boot 4.1.1 service (Java 21, Gradle Kotlin DSL)
frontend/    Angular app (not started)
```

```
backend/src/main/java/com/masterantique/backend/
  BackendApplication.java   entry point
  DatabaseCheck.java        logs what it is connected to at start-up
  model/                    JPA entities: AppUser (users), Ticket, TicketState, Comment, AuditLog
  repo/                     Spring Data repositories
  login/                    LoginService, LoginResult, IdentityCheck, RejectingIdentityCheck
  demo/                     DEMO ONLY, active with the "demo" profile: DemoIdentityCheck, FirstLoginDemo
backend/src/test/java/...   LoginServiceTest (unit tests, no database)
```

## The database

The back end uses the PostgreSQL database produced by the data migration (iteration 5 in `claude_modernization`):
container `mar-postgres`, database `masterantique`, 8 tables, 155 rows, identifiers in snake_case. By design the
container publishes no port and has no known owner password, so before the back end can connect you need, once:

1. an application login `mar_app` with a password you choose, and
2. a network route to the database (a container address, a shared Docker network, or a localhost-only proxy).

Both are explained step by step, with every command tested, in
`claude_modernization/docs/dbmigrate/iteration5/PostgreSQLDatabaseGuide.html`. The defaults here assume the
localhost proxy on port 5433.

## Configuration

All connection settings come from environment variables; no password is ever written in the repository.

| Variable | Default | Meaning |
|---|---|---|
| `MAR_DB_PASSWORD` | (none, required) | Password of the database login |
| `MAR_DB_HOST` | `localhost` | Database host (`mar-postgres` when the back end runs in Docker on the shared network) |
| `MAR_DB_PORT` | `5433` | Database port (`5432` for a container address or the shared network) |
| `MAR_DB_USER` | `mar_app` | Database login |
| `MAR_DB_NAME` | `masterantique` | Database name |

Hibernate runs with `ddl-auto=validate`: it checks every entity against the migrated schema at start-up and never
changes the schema. If validation fails, fix the entity, not the setting.

## Build, test, run

Needs Java 21. The Gradle wrapper downloads Gradle itself.

```
cd backend
./gradlew build                      # compile and run the unit tests
./gradlew test                       # unit tests only

read -rsp 'mar_app password: ' MAR_DB_PASSWORD; echo; export MAR_DB_PASSWORD
./gradlew bootRun                    # or: java -jar build/libs/backend-0.0.1-SNAPSHOT.jar
```

Start-up logs the connection and the data, for example:

```
Connected: mar_app @ masterantique, PostgreSQL 16.1 (Debian 16.1-1.pgdg120+1)
users=12 tickets=24 comments=26 audit_logs=78
customers=8 employees=3 managers=1, must reset password=12
tickets SUBMITTED=8 INPROGRESS=8 COMPLETED=8
```

## First login of migrated users

The migration carried no passwords over: every migrated user has no password hash and `must_reset_password = true`.
`LoginService.login` therefore answers `MUST_CHANGE_PASSWORD` for them, and `changePassword` sets the first password
(bcrypt hash, new security stamp, flag cleared) after an `IdentityCheck` confirms who the person is.

- Without a profile, `RejectingIdentityCheck` is used: **no password can be changed** until a real identity check
  exists (a single-use, expiring code issued by a manager, or a reset link sent to a verified email address).
- The `demo` profile swaps in `DemoIdentityCheck` (accepts `DEMO-1234`) and a console walk-through. It changes the
  stored password of the user you name, so use it only against a test copy of the database:

```
java -jar build/libs/backend-0.0.1-SNAPSHOT.jar --spring.profiles.active=demo \
  --demo.username=Customer1 --demo.password=anything \
  --demo.one-time-code=DEMO-1234 --demo.new-password=Walnut-Armoire-1887
```

## Next steps

- REST API (Spring Web) with server-side validation, and Spring Security around `LoginService`.
- A real `IdentityCheck`, lockout on repeated failures (`access_failed_count`, `lockout_end_date_utc`), rate limiting.
- Workflow audit logging with the same format as the legacy `audit_logs` (ids and timestamps, never comment text or
  passwords), including password changes.
- The Angular front end in `frontend/`.
- The database is PostgreSQL (decided 2026-09-23; Oracle, named in the original brief, is not pursued).

## License

Apache License 2.0, see `LICENSE`.
