# backend/postgresql — instructions for Claude Code

This folder is the PostgreSQL backend: a Spring Boot 4.1.1 (Java 21) service on the MasterAntiqueRepair database migrated
to **PostgreSQL 16**, the database Phase 2 is built on. So far only the **model** layer: JPA entities for the migrated
tables, repositories, and `LoginService` with the forced password change every migrated user meets on first login. No
controller (REST API), no Spring Security yet. It runs when the user says **`Run backend-postgresql`** (below). How it
was built is recorded in `~/dev/claude_work/claude_modernization/docs/phase2/model/postgresql/CLAUDE.md`.

## Rules

1. **Self-contained.** Never copy from, refer to or depend on `backend/oracle/`; this folder must build and run with every
   other folder of the repository removed.
2. **Never test on the delivered database** `mar-postgres`. `Run backend-postgresql` works on a temporary copy
   (`mar-postgres-demo`) and removes it afterwards; `mar-postgres` is only read, by the final `ingest.sh verify`.
3. **The `demo` profile changes real data** (it sets a user's password): only ever against a temporary copy.
4. **No secrets.** Passwords never go into a file, a command line, the process list or the output. The application reads
   its password only from `MAR_DB_PASSWORD`. Test passwords are generated in the shell, used, and forgotten.
5. **The schema belongs to the migration.** Keep `spring.jpa.hibernate.ddl-auto=validate`; if validation fails, fix the
   entity, never the setting or the database.
6. **Git is read-only for Claude** (a hook enforces it): never add, commit, push, rm, mv or reset; the user commits.

## Run backend-postgresql

A demonstration, end to end, on a temporary copy of the migrated database. Run it from the repository root
(`~/dev/claude_work/master-antique-repair-claude`). Report each step's result, and stop at the first failure (after
cleaning up, step 8). Each Bash call starts a fresh shell, so every block sets what it needs
(`MOD=~/dev/claude_work/claude_modernization`; the settings file is always `/tmp/mar-postgres-demo.conf`, which holds no secret).

1. **Prerequisites.** `java -version` is 21; `docker info` succeeds;
   `~/dev/claude_work/claude_modernization/tools/phase1/dbmigrate/import-postgresql/ingest.sh` exists (the database is built
   by the companion repository's import-postgresql tool). If one is missing, say so and stop. `docker ps -a` should list
   `mar-postgres` (the delivered database; if it is missing, say that `Run import-postgresql` in `claude_modernization`
   builds it, but the demonstration below does not need it except for the final check).
2. **Build and unit tests** (no database):
   ```
   cd backend/postgresql && ./gradlew build && ./gradlew test --rerun
   ```
   Expected: `BUILD SUCCESSFUL`, `build/test-results/test/*.xml` shows `tests="6" failures="0"`, and
   `build/libs/backend-0.0.1-SNAPSHOT.jar` exists. (`--rerun` runs the tests even when Gradle thinks they are up to date.)
3. **Temporary copy of the database** (a few seconds; the image `postgres:16.1` is pulled once):
   ```
   MOD=~/dev/claude_work/claude_modernization; CONF=/tmp/mar-postgres-demo.conf
   sed -e 's/^CONTAINER=.*/CONTAINER=mar-postgres-demo/' \
       -e "s|^INPUT_DIR=.*|INPUT_DIR=$MOD/tools/phase1/dbmigrate/import-postgresql/input|" \
       $MOD/tools/phase1/dbmigrate/import-postgresql/ingest.conf.example > "$CONF"
   $MOD/tools/phase1/dbmigrate/import-postgresql/ingest.sh load --recreate --config "$CONF"
   ```
   Expected: `LOAD COMPLETE: database masterantique in container mar-postgres-demo` (155 rows).
4. **A local route** (the database container publishes no port; a proxy opens one on 127.0.0.1 only):
   ```
   docker network create mar-net-demo
   docker network connect mar-net-demo mar-postgres-demo
   docker run -d --name mar-postgres-demo-proxy --network mar-net-demo -p 127.0.0.1:5434:5432 \
     alpine/socat tcp-listen:5432,fork,reuseaddr tcp-connect:mar-postgres-demo:5432
   ```
5. **Logins, connection check and first-login demo, in one shell** (one Bash call, so the throwaway passwords live only
   in that shell's variables; `docker exec -e APP_PW` passes the variable by name, never its value, and `mar-roles.sql`
   reads it with `\getenv`):
   ```
   cd backend/postgresql
   export APP_PW=$(head -c 48 /dev/urandom | base64 | tr -dc 'A-Za-z0-9' | head -c 24)
   export RO_PW=$(head -c 48 /dev/urandom | base64 | tr -dc 'A-Za-z0-9' | head -c 24)
   docker exec -i -e APP_PW -e RO_PW mar-postgres-demo \
     psql -X -q -U masterantique -d masterantique < db/mar-roles.sql && echo 'logins created'
   export MAR_DB_PORT=5434 MAR_DB_PASSWORD=$APP_PW
   J=build/libs/backend-0.0.1-SNAPSHOT.jar
   java -jar $J | grep -E 'Connected|users=|customers=|tickets '                               # connection check
   java -jar $J --spring.profiles.active=demo --demo.username=Customer1 --demo.password=anything \
     --demo.one-time-code=DEMO-1234 --demo.new-password=Walnut-Armoire-1887                    # first login
   java -jar $J --spring.profiles.active=demo --demo.username=customer2 --demo.password=x \
     --demo.one-time-code=WRONG --demo.new-password=Walnut-Armoire-1887 | grep '^2\.'          # wrong code refused
   unset APP_PW RO_PW MAR_DB_PASSWORD
   ```
6. **Expected output.** The connection check:
   ```
   Connected: mar_app @ masterantique, PostgreSQL 16.1 (Debian 16.1-1.pgdg120+1)
   users=12 tickets=24 comments=26 audit_logs=78
   customers=8 employees=3 managers=1, must reset password=12
   tickets SUBMITTED=8 INPROGRESS=8 COMPLETED=8
   ```
   (getting this far means Hibernate validated every entity against the live schema). The first login:
   ```
   1. Sign in as 'Customer1' -> MUST_CHANGE_PASSWORD
      You must change your password before you continue.
   2. Password changed.
   3. Sign in again with the new password -> OK
   4. Sign in with a wrong password      -> INVALID
   ```
   (plus a `DemoIdentityCheck` warning and a `LoginService` "Password changed for user id 5" log line). The wrong code:
   `2. Not changed: The one-time code is not valid.`
7. **Report** the numbers above to the user.
8. **Clean up, always** (also after a failure):
   ```
   docker rm -f mar-postgres-demo-proxy
   docker rm -f -v mar-postgres-demo
   docker network rm mar-net-demo
   rm -f /tmp/mar-postgres-demo.conf
   ```
   Then prove the delivered database was not touched (only if `mar-postgres` exists):
   `~/dev/claude_work/claude_modernization/tools/phase1/dbmigrate/import-postgresql/ingest.sh verify --out "$(mktemp)"` →
   `VERIFICATION PASSED - 80 of 80 checks`. Use `--out` to a temporary file so the tool's committed
   `verification-results.json` is not rewritten.

## Using the delivered database (for a person, not the demonstration)

To run the backend against `mar-postgres` itself, a person creates the logins once with passwords they choose, and a route:
```
cd backend/postgresql
read -rsp 'New password for mar_app: ' APP_PW; echo
read -rsp 'New password for mar_readonly: ' RO_PW; echo
export APP_PW RO_PW
docker exec -i -e APP_PW -e RO_PW mar-postgres psql -X -q -U masterantique -d masterantique < db/mar-roles.sql
unset APP_PW RO_PW
docker network create mar-net; docker network connect mar-net mar-postgres
docker run -d --name mar-postgres-proxy --network mar-net -p 127.0.0.1:5433:5432 \
  alpine/socat tcp-listen:5432,fork,reuseaddr tcp-connect:mar-postgres:5432
read -rsp 'mar_app password: ' MAR_DB_PASSWORD; echo; export MAR_DB_PASSWORD
./gradlew bootRun
```
Claude does not do this itself (it cannot type the passwords, and rule 2). Running `mar-roles.sql` twice stops with
`ERROR: role "mar_app" already exists`. A rebuild of `mar-postgres` (`ingest.sh load --recreate`) removes the logins and
the network link. Step-by-step explanations: `PostgreSQLDatabaseGuide.html` in this folder.

## Settings

Environment variables with defaults in `src/main/resources/application.properties`: `MAR_DB_HOST` (`localhost`),
`MAR_DB_PORT` (`5433`, the localhost proxy; `5432` for a container address or a shared Docker network), `MAR_DB_USER`
(`mar_app`), `MAR_DB_NAME` (`masterantique`), `MAR_DB_PASSWORD` (required, no default). URL
`jdbc:postgresql://host:port/masterantique`. Read the password with `read -rsp`; never put it on a command line or in a file.

## Commands

From `backend/postgresql/` (Java 21; the wrapper downloads Gradle):
```
./gradlew build                                         # compile + unit tests (no database)
./gradlew test --tests '*LoginServiceTest'              # one test class
./gradlew test --tests '*LoginServiceTest.someMethod'   # one test method
./gradlew bootRun                                       # needs MAR_DB_PASSWORD and a route to the database
```

## Layout

```
build.gradle.kts, settings.gradle.kts   Spring Boot 4.1.1, Java 21, project "backend"; PostgreSQL JDBC (Boot BOM)
gradlew, gradle/wrapper/                Gradle 9.4.1 wrapper
db/mar-roles.sql                        creates the mar_app / mar_readonly logins (passwords via \getenv)
PostgreSQLDatabaseGuide.html            the guide for people, GENERATED: edit docs/guide/guide.tpl.html, then
                                        `python3 docs/guide/build-guide.py` (rebuild after changing any file it shows)
docs/guide/                             template, generator, queries.sql, JdbcSmokeTest.java, source-metadata.json
src/main/resources/application.properties        connection from environment variables; validate
src/main/resources/application-demo.properties   quiet output for the demo profile
src/main/java/com/masterantique/backend/
  BackendApplication.java   entry point
  DatabaseCheck.java        @Profile("!demo") start-up runner: logs connection and counts
  model/                    AppUser (users), Ticket, TicketState, Comment, AuditLog
  repo/                     Spring Data repositories
  login/                    LoginService, LoginResult, IdentityCheck, RejectingIdentityCheck (@Profile("!demo"))
  demo/                     DEMO ONLY (@Profile("demo")): DemoIdentityCheck (accepts DEMO-1234), FirstLoginDemo
src/test/java/.../login/LoginServiceTest.java    6 unit tests, Mockito, no database
```

## How it works

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
  `LocalDateTime` (columns are `TIMESTAMP` without time zone); ids are `IDENTITY`; the `text` columns
  (`password_hash`, `security_stamp`, `phone_number`) use `columnDefinition = "text"`. `open-in-view` is off, so load
  what you need inside `@Transactional` service methods.
- **User lookup is case-insensitive** (`AppUserRepository.findActiveByName`, `lower(name)` on both sides and
  `deleted_at is null`, to hit the partial index `ix_users_name_active ON users (lower(name)) WHERE deleted_at IS NULL`).
  Keep that form for new sign-in queries.
- Every migrated user has `password_hash` NULL and `must_reset_password` true, so all real logins currently go
  through `MUST_CHANGE_PASSWORD`.
- Unit tests mock `AppUserRepository` and `AppUser` with Mockito (no Spring context, no database). There are no
  integration tests; the running app's start-up (`DatabaseCheck`) is the schema check.

## Gotchas

- **Errors you may see:** `Connection to localhost:5433 refused` (no route: is the proxy running?), `password
  authentication failed for user "mar_app"` (wrong password, or the logins are gone after a rebuild), `permission denied
  for table ...` (writing as `mar_readonly`), `Schema-validation: missing column [...]` (entity out of step with the table).
- PostgreSQL compares text case-sensitively, unlike the legacy SQL Server: always look usernames up with `lower(...)`.
- The JVM line `Sharing is only supported for boot loader classes` during tests comes from Mockito's agent; harmless.

## Rebuild from scratch

The sources are the PostgreSQL database guide's tested sample project,
`~/dev/claude_work/claude_modernization/tools/phase1/dbmigrate/import-postgresql/guide/mar-db-client/` (package
`com.masterantique.dbclient`, renamed to `com.masterantique.backend`), plus the backend-only files
(`BackendApplication`, `DatabaseCheck`, `RejectingIdentityCheck`, the `demo` profile, `LoginServiceTest`, the Kotlin DSL
build). The step-by-step recipe: `~/dev/claude_work/claude_modernization/docs/phase2/model/postgresql/CLAUDE.md`, section 5.
