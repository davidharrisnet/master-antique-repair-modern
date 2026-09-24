# Sources of OracleDatabaseGuide.html

`backend/oracle/OracleDatabaseGuide.html` is generated from this folder. **Every code block in the guide is a file of
this backend** (`src/`, `db/mar-roles.sql`, the Gradle files) or of this folder, copied in when the page is built, so the
page cannot drift from the code. To change the guide: change the code or the template, then rebuild the page. It is an
adapted copy of the import-oracle database guide in the companion repository `claude_modernization`; nothing here reads
from that repository.

| File | What it is |
|---|---|
| `guide.tpl.html` | The page: text, layout, styles, and markers where code goes |
| `build-guide.py` | Builds the page from the template (Python 3, standard library only) |
| `queries.sql` | The sample queries shown in the guide (section 2) |
| `JdbcSmokeTest.java` | The plain-JDBC connection test (section 5); not part of the Gradle build |
| `source-metadata.json` | The migration record; the table and column reference is built from it |

## Rebuild the page

```
cd backend/oracle
python3 docs/guide/build-guide.py
```

Rebuild it after changing any file the guide shows (an entity, `LoginService`, `application.properties`,
`db/mar-roles.sql`, ...). Template markers: `<!--CODE lang ... CODE-->` (copyable block), `<!--OUT[:label] ... OUT-->`
(output, not copyable), `<!--FILE path|label-->` (a file, path relative to `backend/oracle/`), `<!--SCHEMA-->` (table
reference from `source-metadata.json`). The script stops if a marker is left unexpanded. The output is deterministic:
rebuilding without changes gives the same bytes. `--artifact <path>` also writes the unwrapped copy for publishing as
an Artifact.

The output blocks (`<!--OUT-->`) are typed into the template, not generated: if a change alters what the backend prints,
run `Run backend-oracle` (see `../../CLAUDE.md`) and update them.
