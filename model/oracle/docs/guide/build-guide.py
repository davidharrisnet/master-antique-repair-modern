#!/usr/bin/python3
"""Build model/oracle/OracleDatabaseGuide.html from docs/guide/guide.tpl.html.

Every code block in the guide is a file of this model, so the page cannot drift from the code. Markers in the
template:
  <!--CODE lang ... CODE-->        a copyable code block (lang is its label)
  <!--OUT[:label] ... OUT-->       an output block, no copy button (label defaults to "Output")
  <!--FILE path|label-->           the contents of a file, path relative to model/oracle/, HTML-escaped, copyable
  <!--SCHEMA-->                    the table/column reference, from source-metadata.json in this folder

Usage (from model/oracle/):  python3 docs/guide/build-guide.py [--artifact PATH]
  --artifact PATH   also write the page without the <!DOCTYPE>/<html> wrapper, for publishing as an Artifact
Standard library only. Deterministic: the same inputs always give the same bytes.
"""
import argparse
import html
import json
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
PROJECT = os.path.normpath(os.path.join(HERE, "..", ".."))
TEMPLATE = os.path.join(HERE, "guide.tpl.html")
METADATA = os.path.join(HERE, "source-metadata.json")
OUTPUT = os.path.join(PROJECT, "OracleDatabaseGuide.html")


def block(label, body, copy=True):
    cls = "code" if copy else "code out"
    btn = '<button type="button">Copy</button>' if copy else ""
    return (f'<div class="{cls}"><div class="code-head"><span>{html.escape(label)}</span>{btn}</div>'
            f'<pre><code>{html.escape(body.rstrip(chr(10)))}</code></pre></div>')


def read(path):
    with open(os.path.join(PROJECT, path), encoding="utf-8") as f:
        return f.read()


def schema_table():
    with open(METADATA, encoding="utf-8") as f:
        meta = json.load(f)
    rows = []
    for t in meta["tables"]:
        fks = {c: fk["refTable"] for fk in t["foreignKeys"] for c in fk["columns"]}
        for i, c in enumerate(t["columns"]):
            keys = []
            if c["targetName"] in t["primaryKey"]:
                keys.append("PK")
            if c["identity"]:
                keys.append("id")
            if c["targetName"] in fks:
                keys.append("FK &rarr; " + html.escape(fks[c["targetName"]]))
            if c["synthetic"]:
                keys.append("added by migration")
            name = (f'<td rowspan="{len(t["columns"])}"><code>{html.escape(t["targetName"])}</code>'
                    f'<br><small>{t["rowCount"]} rows</small></td>') if i == 0 else ""
            default = f' default {html.escape(c["default"])}' if c["default"] is not None else ""
            rows.append(f'<tr>{name}<td><code>{html.escape(c["targetName"])}</code></td>'
                        f'<td>{html.escape(c["targetType"])}{default}</td>'
                        f'<td>{"yes" if c["nullable"] else "no"}</td><td>{", ".join(keys)}</td></tr>')
    return ('<div class="table-wrap"><table><thead><tr><th scope="col">Table</th><th scope="col">Column</th>'
            '<th scope="col">Type</th><th scope="col">Null</th><th scope="col">Key</th></tr></thead><tbody>'
            + "".join(rows) + "</tbody></table></div>")


def build():
    with open(TEMPLATE, encoding="utf-8") as f:
        page = f.read()
    page = re.sub(r"<!--CODE (\w+)\n(.*?)\nCODE-->", lambda m: block(m.group(1), m.group(2)), page, flags=re.S)
    page = re.sub(r"<!--OUT(?::([^\n]*))?\n(.*?)\nOUT-->",
                  lambda m: block(m.group(1) or "Output", m.group(2), copy=False), page, flags=re.S)
    page = re.sub(r"<!--FILE ([^|]+)\|([^>]+?)-->", lambda m: block(m.group(2), read(m.group(1))), page)
    page = page.replace("<!--SCHEMA-->", schema_table())
    left = re.findall(r"<!--(CODE|OUT|FILE|SCHEMA)", page)
    if left:
        sys.exit(f"unexpanded markers left in the template: {sorted(set(left))}")
    return page


def main():
    parser = argparse.ArgumentParser(description="Build OracleDatabaseGuide.html from guide.tpl.html")
    parser.add_argument("--artifact", metavar="PATH", help="also write the unwrapped copy for publishing")
    args = parser.parse_args()
    page = build()
    with open(OUTPUT, "w", encoding="utf-8", newline="\n") as f:
        f.write('<!DOCTYPE html>\n<html lang="en">\n' + page + "</html>\n")
    print(f"wrote {os.path.relpath(OUTPUT, PROJECT)}")
    if args.artifact:
        with open(args.artifact, "w", encoding="utf-8", newline="\n") as f:
            f.write(page)
        print(f"wrote {args.artifact}")


if __name__ == "__main__":
    main()
