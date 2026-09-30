# model/oracle: the Spring Boot model on the migrated Oracle database

This folder is the Oracle version of the MasterAntiqueRepair model: a Spring Boot 4.1.1 (Java 21) service that reads the repair shop's database after it was migrated from SQL Server to **Oracle AI Database 26ai Free**. It is the Phase 2 application's model, on the Oracle database the exercise brief names. It has two layers so far. The *model* knows the tables (users, tickets, comments, audit log), can read and update them, and contains the rule every migrated user meets first: **on their first sign-in they must choose a new password**. The *controller* layer is a **REST API with a Swagger page** that offers exactly what the old application let each kind of user do (see "The web API" below). There is no sign-in (authentication) or front end yet.

It is self-contained: it builds and runs with nothing from any other folder of this repository.

## The idea in one paragraph

The old application kept its data in SQL Server. A migration tool (in the companion repository `claude_modernization`) copied every table into an Oracle database that runs inside a **Docker container** called `mar-oracle`, and checked that all 156 rows arrived unchanged. On the way it **removed every password**, so no old password hash is carried over. This model connects to that database, proves the connection works, and shows what a migrated user experiences: they can't sign in until they have set a new password.

## Running the demonstration

With Claude Code, open this repository and type:

```
Run model-oracle
```

Claude follows `CLAUDE.md` in this folder and reports each step. Everything happens on a **temporary copy** of the database that is thrown away at the end, so the real database is never changed. Here is what each step does, in plain terms.

1. **Check the tools are there.** Java 21 to build and run the code, Docker to run the database, and the companion repository, which has the tool that builds the database.
2. **Build the code and run the unit tests.** Gradle compiles the project and runs 107 tests: 6 of the sign-in rules, 30 of the repair-shop rules (the ticket workflow, the comment and description text rules, renaming and deleting users, the audit codes), and 71 of the web API (63 of its services, each named after the action it tests, and 8 of its error answers). They need no database: the database is replaced by stand-ins (Mockito mocks) or by objects built in memory, so they test only the logic.
3. **Create a temporary copy of the database.** The migration tool starts a fresh Oracle container, `mar-oracle-demo`, and loads the same 156 rows into it (about a minute). Working on a copy means the demonstration can change a password without touching the delivered database.
4. **Open a door to the database.** A database in a Docker container can't be reached from outside unless a port is opened. On purpose, the migration creates the container with no open port. So a tiny second container (a `socat` proxy) forwards one port, `1523`, on this machine only (`127.0.0.1`), to Oracle's port `1521` inside.
5. **Create a login for the application.** The tables belong to a schema called `masterantique` that nobody can log in as, and the database administrator is never used by the application. Instead, the script `db/mar-roles.sql` creates two logins: `mar_app` (can read and write) and `mar_readonly` (can only read). Each gets access to every table of `masterantique` in one statement (Oracle 23ai's "schema privileges"). Their passwords come from your local password file (see "Running the API" below); on the temporary copy they are never shown.
6. **Connect from Spring Boot and check.** The model starts, connects as `mar_app`, and lists what it finds:
   ```
   Connected: MAR_APP @ FREEPDB1, schema MASTERANTIQUE, Oracle 23.26.3.0.0
   users=12 tickets=24 comments=26 audit_logs=79
   customers=8 employees=3 managers=1, must reset password=12
   tickets SUBMITTED=8 INPROGRESS=8 COMPLETED=8
   ```
   Starting at all is itself a check: before it runs, Hibernate compares every Java entity with the real tables and refuses to start if anything disagrees.
7. **Walk through a first sign-in.** In demonstration mode the model acts out a migrated customer signing in for the first time and changing their password (see "Signing in and changing the password" below).
8. **Clean up.** The temporary database, the proxy and the network are removed. Finally the migration tool re-checks the real database (86 of 86 checks) to prove nothing touched it.

## How Spring Boot connects to the database in Docker

Three things have to line up: **where** the database is, **who** connects, and **which password** they use. Oracle adds a fourth: **which schema** the tables are in.

- **Where.** The address is a JDBC URL: `jdbc:oracle:thin:@//HOST:PORT/FREEPDB1`. `FREEPDB1` is the name of the Oracle *service* (the pluggable database inside the container), not a database name. Because the container has no open port, you pick a route:
  - *localhost proxy* (the default here): the `socat` container publishes a port on `127.0.0.1`, so the URL is `jdbc:oracle:thin:@//localhost:1522/FREEPDB1`. This works for an app or IDE running on your machine, including Docker Desktop. Port 1522 leaves 1521 free for a locally installed Oracle.
  - *container address*: on Linux, Docker gives the container an address such as `172.17.0.6`, reachable on port `1521`.
  - *shared Docker network*: if the model itself runs in a container on the same Docker network, it reaches the database by name: `mar-oracle:1521`.
- **Who.** The login `mar_app`, created by `db/mar-roles.sql`.
- **Which password.** The application reads it only from the environment variable `MAR_DB_PASSWORD`. The start script fills that variable from a private file on your machine, outside every repository (see below).
- **Which schema.** `mar_app` does not own the tables; they belong to `MASTERANTIQUE`. So every connection Spring Boot opens first runs `ALTER SESSION SET CURRENT_SCHEMA = MASTERANTIQUE` (the `connection-init-sql` setting), and the code can then say `users` instead of `masterantique.users`.

`src/main/resources/application.properties` builds the connection from environment variables, with defaults for the localhost proxy:

| Variable | Default | Meaning |
|---|---|---|
| `MAR_DB_HOST` | `localhost` | Where the database is |
| `MAR_DB_PORT` | `1522` | The proxy's port (`1521` for a container address or shared network) |
| `MAR_DB_SERVICE` | `FREEPDB1` | The Oracle service |
| `MAR_DB_USER` | `mar_app` | The login |
| `MAR_DB_PASSWORD` | (none, required) | The login's password (`scripts/run-api.sh` sets it from `~/.config/mar/oracle.env`) |

Spring Boot then does the rest. The Oracle JDBC driver (`ojdbc11`, pure Java, no Oracle client to install) talks to the database. **HikariCP** keeps a small pool of open connections (5). **Hibernate** maps each table to a Java class and, with `ddl-auto=validate`, only *checks* the tables; it never creates or changes them, because the schema belongs to the migration.

## Running the API

One command, from this folder, in any terminal, also after the database was rebuilt:

```
scripts/run-api.sh            # runs until Ctrl+C
scripts/run-api.sh start      # or in the background (log in build/api.log); later: scripts/run-api.sh stop
```

Then open `http://127.0.0.1:8080/swagger-ui.html`. There is no password to type. What the script does:

1. **The password file.** The first time, `scripts/db-logins.sh` creates `~/.config/mar/oracle.env`, readable only by you (mode 600) and outside every git repository, with two random passwords: `MAR_DB_PASSWORD` for `mar_app` and `MAR_RO_PASSWORD` for `mar_readonly`. After that it reuses them. This is the one place a password is kept on disk, on purpose and only on your machine; the scripts never print it or put it on a command line.
2. **The logins.** Inside the `mar-oracle` container (or another container named as the argument, `scripts/db-logins.sh mar-oracle-demo`), in the pluggable database `FREEPDB1`, a login that is missing is created with `db/mar-roles.sql` (a rebuilt database has none), and one that exists is given the file's password again (and unlocked). The passwords reach Oracle's SQL*Plus tool through its input only.
3. **The route.** If needed, it recreates the path from `localhost:1522` to the database: the Docker network `mar-net`, the database container connected to it (a rebuilt container is not), and the proxy container `mar-oracle-proxy`.
4. **The API.** It puts `mar_app`'s password into `MAR_DB_PASSWORD` for the application only and starts it (`./gradlew bootRun`, or the built jar with `start`).

A plain `./gradlew bootRun` works too: when `MAR_DB_PASSWORD` is not set, it runs `scripts/db-logins.sh` first and takes the password from the file (it does not repair the route; `scripts/run-api.sh` does). To use your own password instead, edit the file (letters and digits, no double quote) and run `scripts/db-logins.sh`. The database guide in this folder, `OracleDatabaseGuide.html`, explains the logins and routes in detail.

## Signing in and changing the password

Every migrated user arrives with **no password** and a flag, `must_reset_password`, set to TRUE. So the first sign-in always ends in "you must change your password". The rules live in `LoginService`.

1. **The user signs in.** The username is looked up ignoring upper and lower case (`Customer1` finds `customer1`), and deleted users are skipped. On Oracle the lookup is written in exactly the same form as the database's unique index on usernames, `CASE WHEN deleted_at IS NULL THEN LOWER(name) END`; otherwise Oracle would search the whole table. Because the account has no password yet, the answer is `MUST_CHANGE_PASSWORD`, whatever password was typed. An unknown username, or later a wrong password, gets the same answer, `INVALID`, so nobody can find out which usernames exist.
2. **The user proves who they are.** With no old password to check, something else must prove the person owns the account: in a real system, a one-time code issued by a manager, or a link sent to a verified email address. That check is a pluggable piece (`IdentityCheck`). In normal running it **refuses every change** (`RejectingIdentityCheck`), so an account can never be taken over by someone who only knows its username. Only in the demonstration does a stand-in accept the fixed code `DEMO-1234`.
3. **The user chooses a new password.** They type it twice. It must be at least 12 characters long and must not contain the username.
4. **The password is stored safely.** It is never stored itself. A one-way **bcrypt** hash is stored instead (it starts `{bcrypt}$2a$10$…`), together with a new security stamp. The "must reset" flag is cleared and failed attempts are reset. The log records that a password was changed for user id 5, never the password.
5. **The user signs in normally.** With the new password the answer is `OK`; with a wrong one, `INVALID`.

What the demonstration prints:

```
1. Sign in as 'Customer1' -> MUST_CHANGE_PASSWORD
   You must change your password before you continue.
2. Password changed.
3. Sign in again with the new password -> OK
4. Sign in with a wrong password      -> INVALID
```

A wrong one-time code gives `2. Not changed: The one-time code is not valid.`

To try it yourself, **only on a test copy** (it really changes that user's password):

```
./gradlew bootJar
java -jar build/libs/model-oracle-0.0.1-SNAPSHOT.jar --spring.profiles.active=demo \
  --demo.username=Customer1 --demo.password=anything \
  --demo.one-time-code=DEMO-1234 --demo.new-password=Walnut-Armoire-1887
```

## The repair-shop rules in the model

The model classes also carry the rules of the old application, ready for the web API to use:

- **Tickets** follow the old workflow: a customer submits one (SUBMITTED), an employee takes an unassigned one (INPROGRESS), and that employee completes it (COMPLETED). Taking a ticket someone already took, completing someone else's ticket, or completing it twice is refused. There is no editing, deleting, reassigning or reopening.
- **Comments** can only be added, only to completed tickets, and only by the ticket's customer or its employee.
- **Text** (descriptions and comments) is trimmed, required, at most 2,000 characters, and may contain line breaks and tabs but no other control characters. It must also fit Oracle's 4,000-byte limit, which accented or other multi-byte text can reach first.
- **Users** can be added (with no password: they must set one on first sign-in), renamed, and soft-deleted (the row stays, the username becomes free).
- **Roles** come from the `roles` and `user_roles` tables, as in the old application.
- **The audit log** keeps the old numeric codes (for example 3 = a ticket was taken) and holds only ids and times, never comment text or descriptions.

## The web API

The API offers only the actions the old application allowed, numbered as in the controller contract (`claude_modernization/docs/phase2/controller/CONTROLLER.md`); not a create-read-update-delete screen for every table. Every Swagger summary starts with its action number. Signing up, signing in, password resets and signing out (actions 1-5, and the password fields of the account forms) belong to the security component, which does not exist yet.

| Action | Who | Endpoint |
|---|---|---|
| 6 | Customer | `GET /api/customers/me/tickets` (my tickets, newest first, with comments) |
| 7, 13 | Customer, Employee | `POST /api/tickets/{id}/comments` (only on my / my assigned ticket, only when COMPLETED) |
| 8 | Customer | `POST /api/tickets` (submit a repair) |
| 9, 24 | Employee, Manager | `GET /api/tickets/unassigned` (the manager also sees each customer) |
| 10 | Employee | `GET /api/employees/me/tickets` |
| 11 | Employee | `POST /api/tickets/{id}/take` ("Assign to Me") |
| 12 | Employee | `POST /api/tickets/{id}/complete` (optional comment) |
| 14 | Manager | `GET /api/audit-logs?entityId=&page=&size=` (size 10 or 20) |
| 15, 19 | Manager | `GET /api/employees`, `GET /api/customers` (active, by username) |
| 16, 20 | Manager | `POST /api/employees`, `POST /api/customers` (username only; the new user must set a password) |
| 17, 21 | Manager | `PUT /api/employees/{id}`, `PUT /api/customers/{id}` (rename) |
| 18, 22 | Manager | `DELETE /api/employees/{id}`, `DELETE /api/customers/{id}` (soft delete) |
| 23, 28 | Manager | `GET /api/employees/{id}`; picker `GET /api/employees?includeDeleted=true` |
| 25 | Manager | `GET /api/metrics` (the 7-day summary) |
| 26 | Manager | `GET /api/tickets`, `GET /api/tickets/{id}` |
| 27 | Manager | `GET /api/customers/{id}`; picker `GET /api/customers?includeDeleted=true` |
| 29 | Manager | `GET /api/search?text=` (comments and ticket descriptions) |

**Who is asking.** Until sign-in exists, every request names its user in the header `X-Acting-User-Id`. The user must exist and not be deleted (otherwise 400), and their role, read from the `user_roles` table as the old application did, must allow the action (otherwise 403). Because anyone could put any id in that header, the server only listens on this machine (`127.0.0.1`): it is a local demonstration, not something to deploy.

**Answers.** Data is JSON. Errors follow the ProblemDetail standard (RFC 9457): 400 invalid input (for example a blank or over-long description), 403 wrong role, 404 a ticket or user that does not exist or is not yours, 409 a step that does not fit the ticket's state (taking a ticket someone already took, completing it twice, commenting before it is completed) or a username already in use. Where the old pages silently did nothing, the API says why. Nothing in a request or answer carries a password or security stamp.

**The audit log** gets a row for every change, with the old numeric codes and the acting user, and never with comment text or descriptions: 2 a ticket submitted, 3 taken, 4 completed, 5 a comment added, 0 / 10 / 11 an account created / renamed / deleted by the manager. A completion comment gets no separate comment row, as before.

**Two deliberate improvements** over the old pages: completing a ticket that is already completed is refused (the old page completed it again and moved the date), and an invalid completion comment is refused before anything changes (the old page crashed after completing the ticket).

**Trying it yourself** against the delivered database: `scripts/run-api.sh` (see "Running the API" above), then open `http://127.0.0.1:8080/swagger-ui.html` (the port can be changed with `MAR_API_PORT`). Changes made through Swagger are real changes to that database; `/import-oracle all --recreate` in `claude_modernization` rebuilds it. With Claude Code, `Run import-controller` builds and tests the code and runs a scripted smoke test against the delivered database (see `CLAUDE.md`).

## Build and test

Java 21 is needed; the Gradle wrapper downloads Gradle itself.

```
./gradlew build     # compile and run the 107 unit tests (no database needed)
./gradlew test      # unit tests only
```

## What is where

```
build.gradle.kts, settings.gradle.kts   the build (Spring Boot 4.1.1, Java 21, Oracle driver ojdbc11)
db/mar-roles.sql                        creates the mar_app and mar_readonly logins
scripts/run-api.sh                      starts the API (logins, route, password file); start / stop / status
scripts/db-logins.sh                    creates the logins or gives them the password file's passwords
OracleDatabaseGuide.html                the guide: database, logins, routes, this code (built from docs/guide/)
docs/guide/                             the guide's template and generator
src/main/resources/                     connection settings; quieter output for the demo
src/main/java/com/masterantique/
  DatabaseCheck.java                    prints what it connected to at start-up
  model/                                one Java class per table used, the audit codes, and the repair-shop rules
  repo/                                 queries (the case-insensitive username lookup, the orderings and filters the API needs)
  login/                                the sign-in and password-change rules
  service/                              the web API's actions: who may do what, audit rows, answers
  api/                                  the web API's endpoints, error answers and Swagger description; api/dto/ the request and answer shapes
  demo/                                 the demonstration only (off unless the "demo" profile is on)
src/test/                               the 107 unit tests
CLAUDE.md                               the same steps, written as instructions for Claude Code
```

## Limits

- No sign-in yet: the web API trusts the `X-Acting-User-Id` header and therefore listens on this machine only. No Spring Security, no front end yet.
- The identity check is a placeholder: real password changes are refused until a proper one exists.
- Oracle 23ai or later (the database uses Oracle's `BOOLEAN` type).
- This database limits a text value to 4,000 bytes, even in a 2,000-character column; long text with accented or other multi-byte characters can hit that limit first.
- Times are stored without a time zone, as the old system stored them; whether that was UTC or local time is unknown.
- How this was built and how to rebuild it: `claude_modernization/docs/phase2/model/oracle/CLAUDE.md`.
