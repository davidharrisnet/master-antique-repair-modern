# backend/oracle — instructions for Claude Code

This folder is the Oracle backend: a Spring Boot 4.1.1 (Java 21) service on the MasterAntiqueRepair database migrated to
**Oracle AI Database 26ai Free**. It is the Oracle proof of concept of the Phase 2 model layer: JPA entities for the
migrated tables, repositories, and `LoginService` with the forced password change every migrated user meets on first
login. No REST API, web security or front end yet. It runs when the user says **`Run backend-oracle`** (below). The
human description is `README.md` in this folder; how it was built is recorded in
`~/dev/claude_work/claude_modernization/docs/phase2/model/oracle/CLAUDE.md`.

## Rules

1. **Self-contained.** Never copy from, refer to or depend on `backend/postgresql/` (or any PostgreSQL folder); this folder
   must build and run with every other folder of the repository removed.
2. **Never test on the delivered database** `mar-oracle`. `Run backend-oracle` works on a temporary copy
   (`mar-oracle-demo`) and removes it afterwards; `mar-oracle` is only read, by the final `ingest.sh verify`.
3. **The `demo` profile changes real data** (it sets a user's password): only ever against a temporary copy.
4. **No secrets.** Passwords never go into a file, a command line, the process list or the output. The application reads
   its password only from `MAR_DB_PASSWORD`. Test passwords are generated in the shell, used, and forgotten.
5. **The schema belongs to the migration.** Keep `spring.jpa.hibernate.ddl-auto=validate`; if validation fails, fix the
   entity, never the setting or the database.
6. **Git is read-only for Claude** (a hook enforces it): never add, commit, push, rm, mv or reset; the user commits.

## Run backend-oracle

A demonstration, end to end, on a temporary copy of the migrated database. Run it from the repository root
(`~/dev/claude_work/master-antique-repair-claude`). Report each step's result, and stop at the first failure (after
cleaning up, step 8). Each Bash call starts a fresh shell, so every block sets what it needs
(`MOD=~/dev/claude_work/claude_modernization`; the settings file is always `/tmp/mar-oracle-demo.conf`, which holds no secret).

1. **Prerequisites.** `java -version` is 21; `docker info` succeeds;
   `~/dev/claude_work/claude_modernization/tools/phase1/dbmigrate/import-oracle/ingest.sh` exists (the database is built by the companion repository's import-oracle tool). If one is missing, say so and stop.
   `docker ps -a` should list `mar-oracle` (the delivered database; if it is missing, say that `Run import-oracle` in
   `claude_modernization` builds it, but the demonstration below does not need it except for the final check).
2. **Build and unit tests** (no database):
   ```
   cd backend/oracle && ./gradlew build && ./gradlew test --rerun
   ```
   Expected: `BUILD SUCCESSFUL`, `build/test-results/test/*.xml` shows `tests="6" failures="0"`, and
   `build/libs/backend-oracle-0.0.1-SNAPSHOT.jar` exists. (`--rerun` runs the tests even when Gradle thinks they are up to date.)
3. **Temporary copy of the database** (about a minute; the image `gvenzl/oracle-free:23.26.3-faststart` is pulled once):
   ```
   MOD=~/dev/claude_work/claude_modernization; CONF=/tmp/mar-oracle-demo.conf
   sed -e 's/^CONTAINER=.*/CONTAINER=mar-oracle-demo/' \
       -e "s|^INPUT_DIR=.*|INPUT_DIR=$MOD/tools/phase1/dbmigrate/import-oracle/input|" \
       $MOD/tools/phase1/dbmigrate/import-oracle/ingest.conf.example > "$CONF"
   $MOD/tools/phase1/dbmigrate/import-oracle/ingest.sh load --recreate --config "$CONF"
   ```
   Expected: `LOAD COMPLETE: schema masterantique in pluggable database FREEPDB1, container mar-oracle-demo` (155 rows).
4. **A local route** (the database container publishes no port; a proxy opens one on 127.0.0.1 only):
   ```
   docker network create mar-net-demo
   docker network connect mar-net-demo mar-oracle-demo
   docker run -d --name mar-oracle-demo-proxy --network mar-net-demo -p 127.0.0.1:1523:1521 \
     alpine/socat tcp-listen:1521,fork,reuseaddr tcp-connect:mar-oracle-demo:1521
   ```
5. **Logins, connection check and first-login demo, in one shell** (one Bash call, so the throwaway passwords live only
   in that shell's variables; they are never printed or saved):
   ```
   cd backend/oracle
   APP_PW=$(head -c 48 /dev/urandom | base64 | tr -dc 'A-Za-z0-9' | head -c 24)
   RO_PW=$(head -c 48 /dev/urandom | base64 | tr -dc 'A-Za-z0-9' | head -c 24)
   { printf 'DEFINE app_pw = "%s"\nDEFINE ro_pw = "%s"\n' "$APP_PW" "$RO_PW"; cat db/mar-roles.sql; } |
     docker exec -i mar-oracle-demo sqlplus -S / as sysdba | grep -c 'User created'          # expect 2
   export MAR_DB_PORT=1523 MAR_DB_PASSWORD=$APP_PW
   J=build/libs/backend-oracle-0.0.1-SNAPSHOT.jar
   java -jar $J | grep -E 'Connected|users=|customers=|tickets '                               # connection check
   java -jar $J --spring.profiles.active=demo --demo.username=Customer1 --demo.password=anything \
     --demo.one-time-code=DEMO-1234 --demo.new-password=Walnut-Armoire-1887                    # first login
   java -jar $J --spring.profiles.active=demo --demo.username=customer2 --demo.password=x \
     --demo.one-time-code=WRONG --demo.new-password=Walnut-Armoire-1887 | grep '^2\.'          # wrong code refused
   unset APP_PW RO_PW MAR_DB_PASSWORD
   ```
6. **Expected output.** The connection check:
   ```
   Connected: MAR_APP @ FREEPDB1, schema MASTERANTIQUE, Oracle 23.26.3.0.0
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
   docker rm -f mar-oracle-demo-proxy
   docker rm -f -v mar-oracle-demo
   docker network rm mar-net-demo
   rm -f /tmp/mar-oracle-demo.conf
   ```
   Then prove the delivered database was not touched (only if `mar-oracle` exists):
   `~/dev/claude_work/claude_modernization/tools/phase1/dbmigrate/import-oracle/ingest.sh verify --out "$(mktemp)"` →
   `VERIFICATION PASSED - 86 of 86 checks`.
   Use `--out` to a temporary file so the tool's committed `verification-results.json` is not rewritten.

## Using the delivered database (for a person, not the demonstration)

To run the backend against `mar-oracle` itself, a person creates the logins once with passwords they choose, and a route:
```
cd backend/oracle
read -rsp 'New password for mar_app: ' APP_PW; echo
read -rsp 'New password for mar_readonly: ' RO_PW; echo
{ printf 'DEFINE app_pw = "%s"\nDEFINE ro_pw = "%s"\n' "$APP_PW" "$RO_PW"; cat db/mar-roles.sql; } |
  docker exec -i mar-oracle sqlplus -S / as sysdba
unset APP_PW RO_PW
docker network create mar-net; docker network connect mar-net mar-oracle
docker run -d --name mar-oracle-proxy --network mar-net -p 127.0.0.1:1522:1521 \
  alpine/socat tcp-listen:1521,fork,reuseaddr tcp-connect:mar-oracle:1521
read -rsp 'mar_app password: ' MAR_DB_PASSWORD; echo; export MAR_DB_PASSWORD
./gradlew bootRun
```
Claude does not do this itself (it cannot type the passwords, and rule 2). Running `mar-roles.sql` twice stops with
`ORA-01920` (the logins exist). A rebuild of `mar-oracle` (`ingest.sh load --recreate`) removes the logins and the network link.

## Settings

Environment variables with defaults in `src/main/resources/application.properties`: `MAR_DB_HOST` (`localhost`),
`MAR_DB_PORT` (`1522`, the localhost proxy; `1521` for a container address or a shared Docker network), `MAR_DB_USER`
(`mar_app`), `MAR_DB_SERVICE` (`FREEPDB1`), `MAR_DB_PASSWORD` (required). URL
`jdbc:oracle:thin:@//host:port/FREEPDB1`: a service, not a database. Read the password with `read -rsp`; never put it on a
command line or in a file.

## Commands

From `backend/oracle/` (Java 21; the wrapper downloads Gradle):
```
./gradlew build                                         # compile + unit tests (no database)
./gradlew test --tests '*LoginServiceTest'              # one test class
./gradlew test --tests '*LoginServiceTest.someMethod'   # one test method
./gradlew bootRun                                       # needs MAR_DB_PASSWORD and a route to the database
```

## Layout

```
build.gradle.kts, settings.gradle.kts   Spring Boot 4.1.1, Java 21, project backend-oracle; ojdbc11 (23.26.3.0.0, Boot BOM)
gradlew, gradle/wrapper/                Gradle 9.4.1 wrapper
db/mar-roles.sql                        creates the mar_app / mar_readonly logins (23ai schema privileges)
OracleDatabaseGuide.html                the guide for people, GENERATED: edit docs/guide/guide.tpl.html, then
                                        `python3 docs/guide/build-guide.py` (rebuild after changing any file it shows)
docs/guide/                             template, generator, queries.sql, JdbcSmokeTest.java, source-metadata.json
src/main/resources/application.properties        connection from environment variables; validate; schema switch
src/main/resources/application-demo.properties   quiet output for the demo profile
src/main/java/com/masterantique/backend/
  BackendApplication.java   entry point
  DatabaseCheck.java        @Profile("!demo") start-up runner: logs connection and counts
  model/                    AppUser (users), Ticket, TicketState, Comment, AuditLog
  repo/                     Spring Data repositories
  login/                    LoginService, LoginResult, IdentityCheck, RejectingIdentityCheck (@Profile("!demo"))
  demo/                     DEMO ONLY (@Profile("demo")): DemoIdentityCheck (accepts DEMO-1234), FirstLoginDemo
src/test/java/.../login/LoginServiceTest.java    6 unit tests, Mockito, no database
README.md                   for people
```

## How it works

- **Profiles decide what runs.** Without a profile: `DatabaseCheck` and `RejectingIdentityCheck` (every first-password
  change is refused, so knowing a username is never enough). With `demo`: `FirstLoginDemo` and `DemoIdentityCheck`.
  Exactly one `IdentityCheck` bean must exist; keep the `!demo` / `demo` pairs in step.
- **`LoginService` is the only business logic.** `login` → `OK`, `MUST_CHANGE_PASSWORD` (every migrated user: no hash,
  `must_reset_password` TRUE) or `INVALID` (same answer for unknown user and wrong password, with a dummy bcrypt compare).
  `changePassword`: identity check, policy (12+ characters, confirmation, must not contain the username), `{bcrypt}`
  hash, new UUID security stamp, flag cleared. Errors are `IllegalArgumentException` with user-facing messages; logs
  ids, never passwords.
- **Oracle-specific mapping** (each proven against the database):
  - the login `mar_app` does not own the tables: `spring.datasource.hikari.connection-init-sql=ALTER SESSION SET
    CURRENT_SCHEMA = MASTERANTIQUE` on every pooled connection (enough for Hibernate's validation too);
  - `password_hash`, `security_stamp`, `phone_number` are `CLOB`: `@Lob` (without it validation fails);
  - `BOOLEAN` → `boolean`, `NUMBER(10)` → `Integer`, `TIMESTAMP(3)` → `LocalDateTime` (no time zone), identity →
    `GenerationType.IDENTITY`; `tickets.state` is the enum ordinal 0/1/2 (never reorder `TicketState`);
  - sign-in lookup `(case when u.deletedAt is null then lower(u.name) end) = lower(:name)`: the exact expression of the
    function-based unique index `ix_users_name_active`, so Oracle uses it (a plain `lower(name) = ... and deleted_at is
    null` gives a full table scan); it ignores case and soft-deleted users. Keep this form for new sign-in queries;
  - `hibernate.jdbc.fetch_size=100` (the driver's default of 10 makes Hibernate warn).
- **Entities mirror the migrated schema exactly** (explicit `@Column` names and lengths so `validate` passes): `users`
  holds customers, employees and managers in one `AppUser` entity with `discriminator` as a plain string column (no JPA
  inheritance); soft delete via `deleted_at` (NULL = active); `@ManyToOne` is `LAZY`. `open-in-view` is off: load what
  you need inside `@Transactional` service methods.
- Every migrated user has `password_hash` NULL and `must_reset_password` TRUE, so all real logins currently go through
  `MUST_CHANGE_PASSWORD`.
- Unit tests mock `AppUserRepository` and `AppUser` with Mockito (no Spring context, no database). There are no
  integration tests; the running app's start-up (`DatabaseCheck`) is the schema check.

## Gotchas

- **SQL*Plus substitutes `&name`**: `mar-roles.sql` relies on it for the passwords; for other SQL with an `&`, run
  `SET DEFINE OFF` first.
- **SQL*Plus commits on `EXIT`** (unlike psql): `SET EXITCOMMIT OFF` or `ROLLBACK` before leaving an experiment.
- **Errors you may see:** `ORA-12541` no listener (route/proxy not running), `ORA-01017` wrong password or logins gone
  after a rebuild, `ORA-12514` wrong service (it is `FREEPDB1`), `ORA-00942 ... "MAR_APP"."USERS"` session not in the
  `MASTERANTIQUE` schema, `ORA-41900` writing as `mar_readonly`, `Schema validation: missing column [...]` entity out of
  step with the table.
- **Text is limited to 4,000 bytes per value** (`MAX_STRING_SIZE = STANDARD`), even in a `VARCHAR2(2000 CHAR)` column;
  validate lengths in bytes.
- The JVM line `Sharing is only supported for boot loader classes` during tests comes from Mockito's agent; harmless.

## Rebuild from scratch

The sources are the Oracle database guide's tested sample project,
`~/dev/claude_work/claude_modernization/tools/phase1/dbmigrate/import-oracle/guide/mar-db-client/` (package `com.masterantique.dbclient`, renamed to
`com.masterantique.backend`), plus the backend-only files (`BackendApplication`, `DatabaseCheck`,
`RejectingIdentityCheck`, the `demo` profile, `LoginServiceTest`, the Kotlin DSL build). The step-by-step recipe:
`~/dev/claude_work/claude_modernization/docs/phase2/model/oracle/CLAUDE.md`, section 5.
