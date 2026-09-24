# backend/oracle: the Spring Boot backend on the migrated Oracle database

This folder is the Oracle version of the MasterAntiqueRepair backend: a Spring Boot 4.1.1 (Java 21) service that reads the repair shop's database after it was migrated from SQL Server to **Oracle AI Database 26ai Free**. It is a proof of concept: the Phase 2 application runs on PostgreSQL, and this shows the same backend working on the Oracle database the exercise brief names. So far it is the *model* layer only: it knows the tables (users, tickets, comments, audit log), can read and update them, and contains the rule every migrated user meets first: **on their first sign-in they must choose a new password**. There is no web API or front end yet.

It is self-contained: it builds and runs with nothing from any other folder of this repository.

## The idea in one paragraph

The old application kept its data in SQL Server. A migration tool (in the companion repository `claude_modernization`) copied every table into an Oracle database that runs inside a **Docker container** called `mar-oracle`, and checked that all 155 rows arrived unchanged. On the way it **removed every password**, so no old password hash is carried over. This backend connects to that database, proves the connection works, and shows what a migrated user experiences: they can't sign in until they have set a new password.

## Running the demonstration

With Claude Code, open this repository and type:

```
Run backend-oracle
```

Claude follows `CLAUDE.md` in this folder and reports each step. Everything happens on a **temporary copy** of the database that is thrown away at the end, so the real database is never changed. Here is what each step does, in plain terms.

1. **Check the tools are there.** Java 21 to build and run the code, Docker to run the database, and the companion repository, which has the tool that builds the database.
2. **Build the code and run the unit tests.** Gradle compiles the project and runs 6 tests of the sign-in rules. They need no database: the database is replaced by stand-ins (Mockito mocks), so they test only the logic.
3. **Create a temporary copy of the database.** The migration tool starts a fresh Oracle container, `mar-oracle-demo`, and loads the same 155 rows into it (about a minute). Working on a copy means the demonstration can change a password without touching the delivered database.
4. **Open a door to the database.** A database in a Docker container can't be reached from outside unless a port is opened. On purpose, the migration creates the container with no open port. So a tiny second container (a `socat` proxy) forwards one port, `1523`, on this machine only (`127.0.0.1`), to Oracle's port `1521` inside.
5. **Create a login for the application.** The tables belong to a schema called `masterantique` that nobody can log in as, and the database administrator is never used by the application. Instead, the script `db/mar-roles.sql` creates two logins: `mar_app` (can read and write) and `mar_readonly` (can only read). Each gets access to every table of `masterantique` in one statement (Oracle 23ai's "schema privileges"). For the demonstration their passwords are random, used once and never shown or saved.
6. **Connect from Spring Boot and check.** The backend starts, connects as `mar_app`, and lists what it finds:
   ```
   Connected: MAR_APP @ FREEPDB1, schema MASTERANTIQUE, Oracle 23.26.3.0.0
   users=12 tickets=24 comments=26 audit_logs=78
   customers=8 employees=3 managers=1, must reset password=12
   tickets SUBMITTED=8 INPROGRESS=8 COMPLETED=8
   ```
   Starting at all is itself a check: before it runs, Hibernate compares every Java entity with the real tables and refuses to start if anything disagrees.
7. **Walk through a first sign-in.** In demonstration mode the backend acts out a migrated customer signing in for the first time and changing their password (see "Signing in and changing the password" below).
8. **Clean up.** The temporary database, the proxy and the network are removed. Finally the migration tool re-checks the real database (86 of 86 checks) to prove nothing touched it.

## How Spring Boot connects to the database in Docker

Three things have to line up: **where** the database is, **who** connects, and **which password** they use. Oracle adds a fourth: **which schema** the tables are in.

- **Where.** The address is a JDBC URL: `jdbc:oracle:thin:@//HOST:PORT/FREEPDB1`. `FREEPDB1` is the name of the Oracle *service* (the pluggable database inside the container), not a database name. Because the container has no open port, you pick a route:
  - *localhost proxy* (the default here): the `socat` container publishes a port on `127.0.0.1`, so the URL is `jdbc:oracle:thin:@//localhost:1522/FREEPDB1`. This works for an app or IDE running on your machine, including Docker Desktop. Port 1522 leaves 1521 free for a locally installed Oracle.
  - *container address*: on Linux, Docker gives the container an address such as `172.17.0.6`, reachable on port `1521`.
  - *shared Docker network*: if the backend itself runs in a container on the same Docker network, it reaches the database by name: `mar-oracle:1521`.
- **Who.** The login `mar_app`, created by `db/mar-roles.sql`.
- **Which password.** Only from the environment variable `MAR_DB_PASSWORD`. It is never written in a file.
- **Which schema.** `mar_app` does not own the tables; they belong to `MASTERANTIQUE`. So every connection Spring Boot opens first runs `ALTER SESSION SET CURRENT_SCHEMA = MASTERANTIQUE` (the `connection-init-sql` setting), and the code can then say `users` instead of `masterantique.users`.

`src/main/resources/application.properties` builds the connection from environment variables, with defaults for the localhost proxy:

| Variable | Default | Meaning |
|---|---|---|
| `MAR_DB_HOST` | `localhost` | Where the database is |
| `MAR_DB_PORT` | `1522` | The proxy's port (`1521` for a container address or shared network) |
| `MAR_DB_SERVICE` | `FREEPDB1` | The Oracle service |
| `MAR_DB_USER` | `mar_app` | The login |
| `MAR_DB_PASSWORD` | (none, required) | The login's password |

Spring Boot then does the rest. The Oracle JDBC driver (`ojdbc11`, pure Java, no Oracle client to install) talks to the database. **HikariCP** keeps a small pool of open connections (5). **Hibernate** maps each table to a Java class and, with `ddl-auto=validate`, only *checks* the tables; it never creates or changes them, because the schema belongs to the migration.

**Doing it yourself against the real database** (once), from this folder:

```
read -rsp 'New password for mar_app: ' APP_PW; echo
read -rsp 'New password for mar_readonly: ' RO_PW; echo
{ printf 'DEFINE app_pw = "%s"\nDEFINE ro_pw = "%s"\n' "$APP_PW" "$RO_PW"; cat db/mar-roles.sql; } |
  docker exec -i mar-oracle sqlplus -S / as sysdba
unset APP_PW RO_PW

docker network create mar-net
docker network connect mar-net mar-oracle
docker run -d --name mar-oracle-proxy --network mar-net -p 127.0.0.1:1522:1521 \
  alpine/socat tcp-listen:1521,fork,reuseaddr tcp-connect:mar-oracle:1521

read -rsp 'mar_app password: ' MAR_DB_PASSWORD; echo; export MAR_DB_PASSWORD
./gradlew bootRun
```

`read -rsp` asks for the password without showing it, so it never lands in your shell history. The passwords reach Oracle's SQL*Plus tool through its input, never on a command line. A password may use any character except a double quote. The database guide in this folder, `OracleDatabaseGuide.html`, explains each command.

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
java -jar build/libs/backend-oracle-0.0.1-SNAPSHOT.jar --spring.profiles.active=demo \
  --demo.username=Customer1 --demo.password=anything \
  --demo.one-time-code=DEMO-1234 --demo.new-password=Walnut-Armoire-1887
```

## Build and test

Java 21 is needed; the Gradle wrapper downloads Gradle itself.

```
./gradlew build     # compile and run the 6 unit tests (no database needed)
./gradlew test      # unit tests only
```

## What is where

```
build.gradle.kts, settings.gradle.kts   the build (Spring Boot 4.1.1, Java 21, Oracle driver ojdbc11)
db/mar-roles.sql                        creates the mar_app and mar_readonly logins
OracleDatabaseGuide.html                the guide: database, logins, routes, this code (built from docs/guide/)
docs/guide/                             the guide's template and generator
src/main/resources/                     connection settings; quieter output for the demo
src/main/java/com/masterantique/backend/
  DatabaseCheck.java                    prints what it connected to at start-up
  model/                                one Java class per table
  repo/                                 queries (including the case-insensitive username lookup)
  login/                                the sign-in and password-change rules
  demo/                                 the demonstration only (off unless the "demo" profile is on)
src/test/                               the 6 unit tests
CLAUDE.md                               the same steps, written as instructions for Claude Code
```

## Limits

- Model layer only: no web API, no Spring Security, no front end yet.
- The identity check is a placeholder: real password changes are refused until a proper one exists.
- Oracle 23ai or later (the database uses Oracle's `BOOLEAN` type).
- This database limits a text value to 4,000 bytes, even in a 2,000-character column; long text with accented or other multi-byte characters can hit that limit first.
- Times are stored without a time zone, as the old system stored them; whether that was UTC or local time is unknown.
- How this was built and how to rebuild it: `claude_modernization/docs/phase2/model/oracle/CLAUDE.md`.
