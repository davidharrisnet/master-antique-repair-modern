# backend/oracle: the Spring Boot backend on the migrated Oracle database

A Spring Boot 4.1.1 (Java 21) service that connects to the MasterAntiqueRepair database migrated to **Oracle AI Database 26ai Free**, maps its tables with JPA, and carries the first-login password change every migrated user must make. It is the Oracle proof of concept of the Phase 2 model layer. It is self-contained: it builds and runs on its own, with nothing from any other folder of this repository.

**Status:** model layer only (entities, repositories, sign-in logic, unit tests). No REST API, web security or front end yet. How it was built and how to rebuild it: `claude_modernization/docs/phase2/model/oracle/CLAUDE.md`.

## Run it with Claude Code

Open this repository (or `claude_modernization`) with Claude Code and type:

```
Run backend-oracle
```

Claude Code follows `claude_modernization/docs/phase2/model/oracle/CLAUDE.md`: it creates the database logins and a local network route, runs the connection check, then the first-login password change on a test copy.

## The database

The migrated Oracle database from `claude_modernization` (the `import-oracle` tool, `Run import-oracle` there): container `mar-oracle`, pluggable database (service) `FREEPDB1`, schema `masterantique`, 8 tables, 155 rows. By design the container publishes no port and the schema has no password, so before the backend can connect you need, once:

1. an application login `mar_app` with a password you choose, and
2. a network route to the database (a container address, a shared Docker network, or a localhost-only proxy).

Both are explained step by step, with every command tested, in `claude_modernization/docs/phase1/dbmigrate/import-oracle/OracleDatabaseGuide.html` (sections 3 and 4). The defaults here assume the localhost proxy on port 1522.

## Configuration

All connection settings come from environment variables; no password is ever written in the repository.

| Variable | Default | Meaning |
|---|---|---|
| `MAR_DB_PASSWORD` | (none, required) | Password of the `mar_app` login |
| `MAR_DB_HOST` | `localhost` | Database host (`mar-oracle` when the backend runs in Docker on the shared network) |
| `MAR_DB_PORT` | `1522` | Database port (`1521` for a container address or the shared network) |
| `MAR_DB_USER` | `mar_app` | Database login |
| `MAR_DB_SERVICE` | `FREEPDB1` | Oracle service name |

The login does not own the tables, so every pooled connection runs `ALTER SESSION SET CURRENT_SCHEMA = MASTERANTIQUE`. Hibernate runs with `ddl-auto=validate`: it checks every entity against the migrated schema at start-up and never changes the schema. If validation fails, fix the entity, not the setting.

## Build, test, run

Needs Java 21. The Gradle wrapper downloads Gradle itself.

```
cd backend/oracle
./gradlew build                      # compile and run the unit tests (no database)
./gradlew test                       # unit tests only

read -rsp 'mar_app password: ' MAR_DB_PASSWORD; echo; export MAR_DB_PASSWORD
./gradlew bootRun                    # or: java -jar build/libs/backend-oracle-0.0.1-SNAPSHOT.jar
```

Start-up logs the connection and the data:

```
Connected: MAR_APP @ FREEPDB1, schema MASTERANTIQUE, Oracle 23.26.3.0.0
users=12 tickets=24 comments=26 audit_logs=78
customers=8 employees=3 managers=1, must reset password=12
tickets SUBMITTED=8 INPROGRESS=8 COMPLETED=8
```

## Layout

```
backend/oracle/
  build.gradle.kts, settings.gradle.kts    Spring Boot 4.1.1, Java 21; ojdbc11 driver (23.26.3.0.0 from the Boot BOM)
  gradlew, gradle/wrapper/                 Gradle 9.4.1 wrapper
  src/main/resources/application.properties        connection from environment variables
  src/main/resources/application-demo.properties   quiet output for the demo profile
  src/main/java/com/masterantique/backend/
    BackendApplication.java   entry point
    DatabaseCheck.java        logs what it is connected to at start-up
    model/                    JPA entities: AppUser (users), Ticket, TicketState, Comment, AuditLog
    repo/                     Spring Data repositories
    login/                    LoginService, LoginResult, IdentityCheck, RejectingIdentityCheck
    demo/                     DEMO ONLY, active with the "demo" profile: DemoIdentityCheck, FirstLoginDemo
  src/test/java/.../login/LoginServiceTest.java    6 unit tests (no database)
```

Oracle-specific points in the code: the three `CLOB` columns of `users` are `@Lob`; `BOOLEAN`, `NUMBER(10)`, `TIMESTAMP(3)` and identity columns map to `boolean`, `Integer`, `LocalDateTime` and `GenerationType.IDENTITY`; the username lookup is `(case when u.deletedAt is null then lower(u.name) end) = lower(:name)`, the exact expression of the function-based index `ix_users_name_active`, so Oracle uses the index and ignores case and soft-deleted users.

## First login of migrated users

The migration carried no passwords over: every migrated user has no password hash and `must_reset_password = TRUE`. `LoginService.login` therefore answers `MUST_CHANGE_PASSWORD` for them, and `changePassword` sets the first password (bcrypt hash, new security stamp, flag cleared) after an `IdentityCheck` confirms who the person is.

- Without a profile, `RejectingIdentityCheck` is used: **no password can be changed** until a real identity check exists.
- The `demo` profile swaps in `DemoIdentityCheck` (accepts `DEMO-1234`) and a console walk-through. It changes the stored password of the user you name, so use it only against a test copy of the database:

```
java -jar build/libs/backend-oracle-0.0.1-SNAPSHOT.jar --spring.profiles.active=demo \
  --demo.username=Customer1 --demo.password=anything \
  --demo.one-time-code=DEMO-1234 --demo.new-password=Walnut-Armoire-1887
```

```
1. Sign in as 'Customer1' -> MUST_CHANGE_PASSWORD
   You must change your password before you continue.
2. Password changed.
3. Sign in again with the new password -> OK
4. Sign in with a wrong password      -> INVALID
```

## Limits

- Oracle 23ai or later (`BOOLEAN` columns).
- The database limits a text value to 4,000 bytes (`MAX_STRING_SIZE = STANDARD`), even in a 2,000-character column: server-side validation must check bytes, not only characters.
- Timestamps have no time zone; whether the legacy system stored UTC or local time is unknown, so none is converted.
