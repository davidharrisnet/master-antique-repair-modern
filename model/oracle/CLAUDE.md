# model/oracle — instructions for Claude Code

This folder is the Oracle model: a Spring Boot 4.1.1 (Java 21) service on the MasterAntiqueRepair database migrated to
**Oracle AI Database 26ai Free**. It is the Phase 2 model layer: JPA entities for the
migrated tables (with the domain rules the controller layer builds on: ticket workflow, comment and description text
rules, user rename and soft delete, legacy audit codes), repositories, `LoginService` with the forced password
change every migrated user meets on first login, and the **controller layer**: a REST API with Swagger UI that offers
exactly the non-security actions of the controller contract
(`~/dev/claude_work/claude_modernization/docs/phase2/controller/CONTROLLER.md`, actions 6-29). No web security
(authentication) or front end yet. It runs when the user says **`Run model-oracle`** or **`Run import-controller`**
(below). The human description is `README.md` in this folder; how it was built is recorded in
`~/dev/claude_work/claude_modernization/docs/phase2/model/oracle/CLAUDE.md` (model) and
`~/dev/claude_work/claude_modernization/docs/phase2/controller/oracle/CLAUDE.md` (controller).

## Rules

1. **Self-contained.** This folder must build and run with every other folder of the repository removed.
2. **Never test on the delivered database** `mar-oracle`, with one exception. `Run model-oracle` works on a temporary
   copy (`mar-oracle-demo`) and removes it afterwards; `mar-oracle` is only read, by the final `ingest.sh verify`. The
   one exception is the `Run import-controller` smoke test: it starts only on a freshly verified `mar-oracle` and writes
   to it **through the API** (never by hand, never its schema); afterwards `/import-oracle all --recreate` restores it.
3. **The `demo` profile changes real data** (it sets a user's password): only ever against a temporary copy.
4. **No secrets.** Passwords never go into a repository file, a command line, the process list or the output. The
   application reads its password only from `MAR_DB_PASSWORD`. The one deliberate, local-only exception is the password
   file `~/.config/mar/oracle.env` (outside every git repository, mode 600, random values made on first use by
   `scripts/db-logins.sh`): the scripts read it, never print it, and never overwrite its passwords. Never `cat` it.
5. **The schema belongs to the migration.** Keep `spring.jpa.hibernate.ddl-auto=validate`; if validation fails, fix the
   entity, never the setting or the database.
6. **Git is read-only for Claude** (a hook enforces it): never add, commit, push, rm, mv or reset; the user commits.
7. **The contract decides the endpoints.** An endpoint exists only for an action numbered in `CONTROLLER.md` (while its
   `Status:` says `approved`), and its OpenAPI summary starts with that number ("Action 11: ..."); tests name it too.
   The security actions (1-5, the password fields of 16, 17, 20, 21, log off) belong to the security component.

## Run model-oracle

A demonstration, end to end, on a temporary copy of the migrated database. Run it from the repository root
(`~/dev/claude_work/master-antique-repair-claude`). Report each step's result, and stop at the first failure (after
cleaning up, step 8). Each Bash call starts a fresh shell, so every block sets what it needs
(`MOD=~/dev/claude_work/claude_modernization`; the settings file is always `/tmp/mar-oracle-demo.conf`, which holds no secret).

1. **Prerequisites.** `java -version` is 21; `docker info` succeeds;
   `~/dev/claude_work/claude_modernization/tools/phase2/dbmigrate/import-oracle/ingest.sh` exists (the database is built by the companion repository's import-oracle tool). If one is missing, say so and stop.
   `docker ps -a` should list `mar-oracle` (the delivered database; if it is missing, say that `Run import-oracle` in
   `claude_modernization` builds it, but the demonstration below does not need it except for the final check).
2. **Build and unit tests** (no database):
   ```
   cd model/oracle && ./gradlew build && ./gradlew test --rerun
   ```
   Expected: `BUILD SUCCESSFUL`, `build/test-results/test/*.xml` (one file per test class) shows 107 tests in total,
   `failures="0"`: model 36 (`LoginServiceTest` 6, `TicketTest` 9, `TextRulesTest` 6, `AppUserTest` 5, `CommentTest` 4,
   `AuditLogTest` 4, `UserRoleTest` 2) and controller 71 (`TicketServiceTest` 22, `AccountServiceTest` 18,
   `CommentServiceTest` 7, `ActingUserTest` 4, `AuditLogServiceTest` 4, `MetricsServiceTest` 4, `SearchServiceTest` 4,
   `ApiExceptionHandlerTest` 8), and
   `build/libs/model-oracle-0.0.1-SNAPSHOT.jar` exists. (`--rerun` runs the tests even when Gradle thinks they are up to date.)
3. **Temporary copy of the database** (about a minute; the image `gvenzl/oracle-free:23.26.3-faststart` is pulled once):
   ```
   MOD=~/dev/claude_work/claude_modernization; CONF=/tmp/mar-oracle-demo.conf
   sed -e 's/^CONTAINER=.*/CONTAINER=mar-oracle-demo/' \
       -e "s|^INPUT_DIR=.*|INPUT_DIR=$MOD/tools/phase2/dbmigrate/import-oracle/input|" \
       $MOD/tools/phase2/dbmigrate/import-oracle/ingest.conf.example > "$CONF"
   $MOD/tools/phase2/dbmigrate/import-oracle/ingest.sh load --recreate --config "$CONF"
   ```
   Expected: `LOAD COMPLETE: schema masterantique in pluggable database FREEPDB1, container mar-oracle-demo` (156 rows).
4. **A local route** (the database container publishes no port; a proxy opens one on 127.0.0.1 only):
   ```
   docker network create mar-net-demo
   docker network connect mar-net-demo mar-oracle-demo
   docker run -d --name mar-oracle-demo-proxy --network mar-net-demo -p 127.0.0.1:1523:1521 \
     alpine/socat tcp-listen:1521,fork,reuseaddr tcp-connect:mar-oracle-demo:1521
   ```
5. **Logins, connection check and first-login demo, in one shell** (one Bash call; `db-logins.sh` creates the copy's
   logins with the passwords of the person's password file, which it only reads, and the password reaches Java through
   the environment, never printed):
   ```
   cd model/oracle
   scripts/db-logins.sh mar-oracle-demo                  # expect "2 login(s) created, 0 password(s) set"
   export MAR_DB_PORT=1523 MAR_DB_PASSWORD=$(sed -n 's/^MAR_DB_PASSWORD=//p' ~/.config/mar/oracle.env)
   J=build/libs/model-oracle-0.0.1-SNAPSHOT.jar
   java -jar $J --spring.main.web-application-type=none | grep -E 'Connected|users=|customers=|tickets '  # connection check
   java -jar $J --spring.profiles.active=demo --demo.username=Customer1 --demo.password=anything \
     --demo.one-time-code=DEMO-1234 --demo.new-password=Walnut-Armoire-1887                    # first login
   java -jar $J --spring.profiles.active=demo --demo.username=customer2 --demo.password=x \
     --demo.one-time-code=WRONG --demo.new-password=Walnut-Armoire-1887 | grep '^2\.'          # wrong code refused
   unset MAR_DB_PASSWORD
   ```
6. **Expected output.** The connection check:
   ```
   Connected: MAR_APP @ FREEPDB1, schema MASTERANTIQUE, Oracle 23.26.3.0.0
   users=12 tickets=24 comments=26 audit_logs=79
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
   `~/dev/claude_work/claude_modernization/tools/phase2/dbmigrate/import-oracle/ingest.sh verify --out "$(mktemp)"` →
   `VERIFICATION PASSED - 86 of 86 checks`.
   Use `--out` to a temporary file so the tool's committed `verification-results.json` is not rewritten.

## Run import-controller

Copied from section 6 of the plan `~/dev/claude_work/claude_modernization/docs/phase2/controller/oracle/CLAUDE.md`
(the plan and `CONTROLLER.md` win if this copy drifts). Linux only. It uses the **delivered** database `mar-oracle`
through its proxy `mar-oracle-proxy` on `127.0.0.1:1522` (not a copy), and its smoke test writes to it through the API.
`MOD=~/dev/claude_work/claude_modernization`; `APP` is this repository's root. Stop at the first failure and report;
`mar-oracle` and its proxy stay up afterwards.

1. **Prerequisites**: `java -version` 21, `docker info`, `$MOD/tools/phase2/dbmigrate/import-oracle/ingest.sh` exists,
   `mar-oracle` is running, and `$MOD/docs/phase2/controller/CONTROLLER.md` says `Status: approved`.
2. **Build and unit tests**: `cd $APP/model/oracle && ./gradlew build && ./gradlew test --rerun` -> BUILD SUCCESSFUL,
   107 tests (the counts in `Run model-oracle` step 2), 0 failures.
3. **Fresh database**: `$MOD/tools/phase2/dbmigrate/import-oracle/ingest.sh verify --out "$(mktemp)"` ->
   `VERIFICATION PASSED - 86 of 86`. If it fails (usually an earlier smoke test's rows), stop and ask the person to run
   `/import-oracle all --recreate`; never recreate it without asking.
4. **Logins, route and start**: `cd $APP/model/oracle && scripts/run-api.sh start`. It runs `scripts/db-logins.sh`
   (creates `mar_app`/`mar_readonly` in FREEPDB1 if missing, else sets them to the password file's passwords: the
   person's own, so nothing is overwritten), repairs the route if needed (`mar-net`, `mar-oracle` connected to it,
   `mar-oracle-proxy` on `127.0.0.1:1522`), starts the jar in the background (log `build/api.log`, pid
   `build/api.pid`) and waits until `/v3/api-docs` answers. Expect `run-api: API running`.
5. **Smoke test**: look up one active Manager, Employee and Customer id (`GET /api/employees`,
   `GET /api/customers` as the Manager; the Manager's id from the database); then the smoke test with `curl`, every
   request with the header `X-Acting-User-Id`:
   - `GET /swagger-ui.html` 200 (after redirect); `/v3/api-docs` has no path for roles, user roles, claims, logins,
     comment edit/delete or ticket delete;
   - Manager reads: `/api/tickets` 24, `/api/customers?includeDeleted=true` 8, `/api/employees?includeDeleted=true` 3,
     `/api/audit-logs` total 79, `/api/metrics` 200, `/api/search?text=a` 200, blank search 400;
   - role check: the Customer calling `/api/metrics` 403;
   - accounts (Manager): add a customer (no password fields in the JSON, must reset), rename it, soft delete it, add
     the same name again (allowed), add a duplicate active name 409;
   - workflow: Customer submits a ticket -> SUBMITTED; Customer comments on it -> 409 (not completed); Employee sees it
     in `/api/tickets/unassigned`, takes it -> INPROGRESS, takes again -> 409; completes it with a comment ->
     COMPLETED, completes again -> 409; Customer comments -> 201; Employee comments -> 201;
   - `/api/audit-logs` now 79 + the rows the smoke test wrote (CreateUser, EditUser, DeleteUser, CreateTicket,
     AssignTicket, CompleteTicket, AddComment), with those codes and no text;
   - invalid input: blank description 400, 2,001-character description 400, each with a ProblemDetail body.
   Stop the app: `scripts/run-api.sh stop`.
6. **Report** the numbers; say that `mar-oracle` now holds the smoke test's rows (verify will fail until
   `/import-oracle all --recreate`), and tell the person how to use Swagger themselves: `scripts/run-api.sh` (or
   `start` / `stop` for the background), then `http://127.0.0.1:8080/swagger-ui.html`; no password to type. The proxy
   stays; remove it with `docker rm -f mar-oracle-proxy`.

(The plan in `claude_modernization` still describes steps 4-5 with throwaway passwords; this copy deliberately uses the
scripts so a smoke test no longer overwrites the person's password.)

## Using the delivered database (for a person, not the demonstration)

One command, from `model/oracle/`, in any terminal and after any rebuild of `mar-oracle`:
```
scripts/run-api.sh            # foreground (./gradlew bootRun) until Ctrl+C
scripts/run-api.sh start      # or in the background: log build/api.log; then `stop` / `status`
```
Each start runs `scripts/db-logins.sh [container]`, which (1) creates `~/.config/mar/oracle.env` with random
`MAR_DB_PASSWORD` (mar_app) and `MAR_RO_PASSWORD` (mar_readonly) on first use, and reuses it after; (2) in the
container's FREEPDB1, creates a missing login with the matching lines of `db/mar-roles.sql` (a rebuild with
`ingest.sh load --recreate` removes both logins) and sets an existing one to the file's password with `ALTER USER ...
ACCOUNT UNLOCK`. The passwords reach SQL*Plus on standard input only (`SET VERIFY OFF`); its output is filtered to
counts and `ORA-`/`SP2-` codes, so no statement text is ever shown. `run-api.sh` then repairs the default route
(a rebuilt container is not on `mar-net`), puts `MAR_DB_PASSWORD` into its environment and starts the API. To change a
password, edit the file (letters and digits; no double quote) and run `scripts/db-logins.sh`. `mar-roles.sql` alone
stops with `ORA-01920` when a login exists; the script handles that.

## Settings

Environment variables with defaults in `src/main/resources/application.properties`: `MAR_DB_HOST` (`localhost`),
`MAR_DB_PORT` (`1522`, the localhost proxy; `1521` for a container address or a shared Docker network), `MAR_DB_USER`
(`mar_app`), `MAR_DB_SERVICE` (`FREEPDB1`), `MAR_DB_PASSWORD` (required). URL
`jdbc:oracle:thin:@//host:port/FREEPDB1`: a service, not a database. `scripts/run-api.sh` sets `MAR_DB_PASSWORD` from
the password file `~/.config/mar/oracle.env` (`MAR_ENV_FILE` overrides the path) and also reads `MAR_DB_CONTAINER`
(`mar-oracle`); never put a password on a command line or in a repository file. The API: `MAR_API_PORT` (`8080`); `server.address=127.0.0.1` (this machine only, because the
acting-user header is trusted); Swagger UI at `/swagger-ui.html`, the OpenAPI document at `/v3/api-docs`;
`spring.mvc.problemdetails.enabled=true`. The `demo` profile sets `spring.main.web-application-type=none` (a console run).

## Commands

From `model/oracle/` (Java 21; the wrapper downloads Gradle):
```
./gradlew build                                         # compile + unit tests (no database)
./gradlew test --tests '*LoginServiceTest'              # one test class
./gradlew test --tests '*LoginServiceTest.someMethod'   # one test method
./gradlew bootRun                                       # serves the API and http://127.0.0.1:8080/swagger-ui.html
                                                        # until stopped; without MAR_DB_PASSWORD it first runs
                                                        # scripts/db-logins.sh and reads the password file
                                                        # (build.gradle.kts); needs the route to the database
scripts/run-api.sh [start|stop|status]                  # logins + route + password file, then bootRun (or the jar
                                                        # in the background); the usual way to run the API
scripts/db-logins.sh [container]                        # only the logins: create if missing, else set the file's passwords
```

## Layout

```
build.gradle.kts, settings.gradle.kts   Spring Boot 4.1.1, Java 21, project model-oracle; ojdbc11 (23.26.3.0.0, Boot BOM);
                                        starter-webmvc, starter-validation, springdoc-openapi-starter-webmvc-ui 3.1.1
gradlew, gradle/wrapper/                Gradle 9.4.1 wrapper
db/mar-roles.sql                        creates the mar_app / mar_readonly logins (23ai schema privileges)
scripts/db-logins.sh                    makes the logins match ~/.config/mar/oracle.env (created on first use)
scripts/run-api.sh                      runs db-logins.sh, repairs the mar-oracle-proxy route, starts/stops the API
OracleDatabaseGuide.html                the guide for people, GENERATED: edit docs/guide/guide.tpl.html, then
                                        `python3 docs/guide/build-guide.py` (rebuild after changing any file it shows)
docs/guide/                             template, generator, queries.sql, JdbcSmokeTest.java, source-metadata.json
src/main/resources/application.properties        connection from environment variables; validate; schema switch
src/main/resources/application-demo.properties   quiet output for the demo profile
src/main/java/com/masterantique/
  ModelApplication.java   entry point
  DatabaseCheck.java        @Profile("!demo") start-up runner: logs connection and counts
  model/                    AppUser (users), Ticket, TicketState, Comment, AuditLog, AuditAction, AuditEntityKind,
                            Role, UserRole + UserRoleId (user_roles), UserKind, TextRules
  repo/                     Spring Data repositories: AppUser, Ticket, Comment, AuditLog, Role, UserRole
  login/                    LoginService, LoginResult, IdentityCheck, RejectingIdentityCheck (@Profile("!demo"))
  demo/                     DEMO ONLY (@Profile("demo")): DemoIdentityCheck (accepts DEMO-1234), FirstLoginDemo
  service/                  TicketService (6, 8-12, 24, 26), CommentService (7, 13), AccountService (15-23, 27, 28),
                            AuditLogService (14), MetricsService (25), SearchService (29); ActingUser + Actor (the
                            X-Acting-User-Id header), AuditWriter (package-private), Views (DTO mapping), ClockConfig,
                            NotFoundException (404), ForbiddenException (403)
  api/                      TicketController, CustomerController, EmployeeController, ManagerReportController (audit
                            log, metrics, search), ApiExceptionHandler (ProblemDetail), OpenApiConfig, ActingUserHeader
  api/dto/                  request/response records (Bean Validation on requests; no password or stamp fields)
src/test/java/.../login/LoginServiceTest.java    6 unit tests, Mockito, no database
src/test/java/.../model/                         30 unit tests of the entity rules (real entities, no database)
src/test/java/.../service/                       63 unit tests of the services, each named with its action number
                                                 (real entities, mocked repositories, fixed clock; TestData)
src/test/java/.../api/ApiExceptionHandlerTest    8 @WebMvcTest tests of the error mapping (services mocked)
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
- **Domain rules live in the entities** (the controller layer's services call them and map the exceptions:
  `IllegalArgumentException` = invalid input, 400; `IllegalStateException` = wrong state, 409):
  - `Ticket.submit` (active Customer, description by `TextRules`), `take` (active Employee; unassigned SUBMITTED only),
    `complete` (the assignee only; not twice: a deliberate change from the legacy re-complete); `isAssignedTo`,
    `isOwnedBy` compare ids;
  - `Comment.add`: COMPLETED tickets only, the ticket's customer or assignee only, text by `TextRules`;
    `Comment.validText` checks a completion comment before the ticket changes;
  - `TextRules.validText`: trimmed, required, at most 2,000 characters (code points), no control characters but
    CR/LF/TAB, at most 4,000 UTF-8 bytes;
  - `AppUser.create` (no password, must reset), `rename`, `softDelete` (not on a deleted user); username trimmed,
    required, at most 256 characters; uniqueness among active users is `existsActiveByName(AndIdNot)` plus the index;
  - `AuditLog.record(actor, AuditAction, AuditEntityKind, entityId, timestamp)`; `action` and `entity_type` are the
    ordinals of the legacy `ActionType` (0-11) and `EntityKind` (0-2): never reorder the enums. No text column exists.
- **Roles come from `user_roles` -> `roles.name`** (Customer/Employee/Manager, the same strings as the discriminator,
  `UserKind`); `UserRole` has plain id columns and `@IdClass(UserRoleId)`, joined to `Role` in JPQL
  (`findRoleNamesByUserId`).
- **Search and name ordering ignore case** (`...ContainingIgnoreCase`, `order by lower(u.name)`), as SQL Server's
  default collation did in the legacy app; Oracle compares case-sensitively otherwise. Spring Data escapes LIKE
  wildcards in `Containing` parameters.
- Every migrated user has `password_hash` NULL and `must_reset_password` TRUE, so all real logins currently go through
  `MUST_CHANGE_PASSWORD`.
- Unit tests mock `AppUserRepository` and `AppUser` with Mockito for `LoginService`, and build real entities (ids set
  with `ReflectionTestUtils`, `model/Fixtures`) for the entity rules; no Spring context, no database. There are no
  integration tests; the running app's start-up (`DatabaseCheck`) is the schema check.

- **Controller layer** (layering controller -> service -> repository): controllers only map HTTP to one service call;
  every service method is `@Transactional` and returns DTOs (open-in-view is off). Each service first calls
  `ActingUser.require(id, roles...)`: the header must name an active user (else 400) whose `user_roles` role allows the
  action (else 403). Services find tickets through the actor (`findByIdAndCustomer_Id` / `findByIdAndAssignee_Id`) so a
  ticket that is not yours is 404, then call the entity rule; `ApiExceptionHandler` maps `IllegalArgumentException` 400,
  `ForbiddenException` 403, `NotFoundException` 404, `IllegalStateException` and `DataIntegrityViolationException` 409,
  anything else 500 with no detail.
- **Audit rows** are written only by `AuditWriter`, in the same transaction as the change: CreateTicket (8),
  AssignTicket (11), CompleteTicket (12, and no AddComment for its comment), AddComment (7, 13), CreateUser / EditUser /
  DeleteUser (16-18, 20-22; only when the actor's discriminator is Manager, as the legacy `LogIfManager`). Never text.
- **Deliberate differences from the legacy pages**: explicit 404/409 where they silently did nothing; completing a
  COMPLETED ticket is 409; an invalid completion comment is 400 before anything changes; new users get no password.
- The services' `Clock` bean is the server's local time (as the legacy `DateTime.Now`); tests use a fixed clock.

## Gotchas

- **The web server keeps the process running.** A plain `java -jar` now serves the API until stopped; for a one-off
  console run (the connection check) pass `--spring.main.web-application-type=none`. The `demo` profile already does.
- Boot 4 uses **Jackson 3** (`tools.jackson`); the DTOs are plain records, `LocalDateTime` is written as ISO text.

- **SQL*Plus substitutes `&name`**: `mar-roles.sql` relies on it for the passwords; for other SQL with an `&`, run
  `SET DEFINE OFF` first.
- **SQL*Plus commits on `EXIT`**: `SET EXITCOMMIT OFF` or `ROLLBACK` before leaving an experiment.
- **Errors you may see:** `ORA-12541` no listener (route/proxy not running), `ORA-01017` wrong password or logins gone
  after a rebuild (`scripts/db-logins.sh` fixes both; `run-api.sh` runs it), `ORA-12514` wrong service (it is `FREEPDB1`), `ORA-00942 ... "MAR_APP"."USERS"` session not in the
  `MASTERANTIQUE` schema, `ORA-41900` writing as `mar_readonly`, `Schema validation: missing column [...]` entity out of
  step with the table.
- **Text is limited to 4,000 bytes per value** (`MAX_STRING_SIZE = STANDARD`), even in a `VARCHAR2(2000 CHAR)` column;
  validate lengths in bytes.
- The JVM line `Sharing is only supported for boot loader classes` during tests comes from Mockito's agent; harmless.

## Rebuild from scratch

The sources are the Oracle database guide's tested sample project,
`~/dev/claude_work/claude_modernization/tools/phase2/dbmigrate/import-oracle/guide/mar-db-client/` (package `com.masterantique.dbclient`, renamed to
`com.masterantique`), plus the files written for this project (`ModelApplication`, `DatabaseCheck`,
`RejectingIdentityCheck`, the `demo` profile, `LoginServiceTest`, the Kotlin DSL build). The step-by-step recipe:
`~/dev/claude_work/claude_modernization/docs/phase2/model/oracle/CLAUDE.md`, section 5.
