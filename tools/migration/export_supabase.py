#!/usr/bin/env python3
"""Read-only Supabase export + integrity report (migration tasks P11-01 / P1-11).

Why a script instead of a one-off: P11 requires *repeatable* exports with the same
shaping so a later run can be diffed against this one. Nothing here writes to Supabase
or Firebase; every statement is a SELECT executed through the Supabase MCP gateway.

Output (gitignored — it contains personal data):
    migration-export/<date>/<table>.json     one file per table, redacted
    migration-export/<date>/summary.json     row counts + timings
    migration-export/<date>/INTEGRITY.md     orphan/identity/counter findings

Redaction: `profiles.fcm_token` is replaced with "«redacted»" before it ever touches
disk. Device tokens are live push credentials; a migration export is not a safe place
to accumulate them.
"""

from __future__ import annotations

import json
import os
import re
import subprocess
import sys
from datetime import datetime, timezone
from pathlib import Path

CLI = "accio-mcp-cli"
PAGE = 500
MAX_ROWS = 20000
REDACT = {("profiles", "fcm_token")}
HERE = Path(__file__).resolve().parent
REPO = HERE.parent.parent


def run_sql(sql: str):
    """Executes one SELECT via the MCP CLI and returns parsed rows (or raises)."""
    payload = Path("/tmp/_mig_query.json")
    payload.write_text(json.dumps({"query": sql}))
    proc = subprocess.run(
        [CLI, "call", "execute_sql", "--json-file", str(payload)],
        capture_output=True, text=True,
    )
    if proc.returncode != 0:
        raise RuntimeError(f"cli failed: {proc.stderr[:400]}")
    raw = proc.stdout.strip()
    try:
        obj = json.loads(raw)
    except json.JSONDecodeError as exc:
        raise RuntimeError(f"non-json output: {raw[:300]}") from exc
    if "error" in obj:
        raise RuntimeError(str(obj["error"])[:400])
    text = obj.get("result", "")
    # The gateway wraps rows as "<prose>\n\n<untrusted-data-UUID>\n[ ... ]\n</...-UUID>".
    # Do NOT unescape here: `text` is already decoded once, and the array inside is valid
    # JSON with its own escapes (call_signals.payload carries literal \\r\\n). Slice out the
    # JSON value and let the parser do the only decoding pass.
    open_idx = text.find("[")
    close_idx = text.rfind("]")
    if open_idx == -1 or close_idx <= open_idx:
        return []
    return json.loads(text[open_idx:close_idx + 1], strict=False)


def table_list():
    rows = run_sql(
        "select table_name from information_schema.tables "
        "where table_schema='public' and table_type='BASE TABLE' order by table_name"
    )
    return [r["table_name"] for r in rows]


def export_table(name: str, out_dir: Path):
    count_rows = run_sql(f"select count(*) as n from public.{name}")
    total = int(count_rows[0]["n"]) if count_rows else 0
    if total > MAX_ROWS:
        raise RuntimeError(f"{name}: {total} rows exceeds MAX_ROWS={MAX_ROWS}; paging required")

    rows, offset = [], 0
    while offset < total:
        part = run_sql(f"select * from public.{name} limit {PAGE} offset {offset}")
        if not part:
            break
        rows.extend(part)
        offset += PAGE

    for row in rows:
        for (tbl, col) in REDACT:
            if tbl == name and col in row and row[col]:
                row[col] = "«redacted»"

    (out_dir / f"{name}.json").write_text(json.dumps(rows, indent=1, default=str))
    return total, len(rows)


def integrity(out_dir: Path, counts: dict):
    def q(sql):
        try:
            return run_sql(sql)
        except RuntimeError as exc:
            return [{"error": str(exc)[:200]}]

    checks = {
        "profiles_total": q("select count(*) as n from public.profiles"),
        "profiles_without_firebase_uid": q(
            "select id, username from public.profiles "
            "where firebase_uid is null or firebase_uid = '' order by username"
        ),
        "orphan_posts": q(
            "select count(*) as n from public.posts p "
            "left join public.profiles pr on pr.id = p.user_id where pr.id is null"
        ),
        "orphan_comments": q(
            "select count(*) as n from public.comments c "
            "left join public.posts p on p.id = c.post_id where p.id is null"
        ),
        "orphan_likes": q(
            "select count(*) as n from public.likes l "
            "left join public.posts p on p.id = l.post_id where p.id is null"
        ),
        "orphan_follows": q(
            "select count(*) as n from public.follows f "
            "left join public.profiles pr on pr.id = f.follower_id where pr.id is null"
        ),
        "orphan_messages": q(
            "select count(*) as n from public.messages m "
            "left join public.conversations c on c.id = m.conversation_id where c.id is null"
        ),
        "counter_mismatch_posts_likes": q(
            "select count(*) as n from public.posts p "
            "where p.likes_count <> (select count(*) from public.likes l where l.post_id = p.id)"
        ),
        "counter_mismatch_profile_followers": q(
            "select count(*) as n from public.profiles pr "
            "where pr.followers_count <> (select count(*) from public.follows f where f.following_id = pr.id)"
        ),
        "media_rows": q(
            "select bucket_id, count(*) as files from storage.objects group by 1 order by 1"
        ),
    }

    lines = ["# Supabase export — integrity report", "",
             f"Generated {datetime.now(timezone.utc).isoformat(timespec='seconds')}", ""]
    lines.append("## Row counts")
    lines.append("")
    lines.append("| table | rows exported |")
    lines.append("|---|---|")
    for name in sorted(counts):
        lines.append(f"| {name} | {counts[name]} |")

    lines += ["", "## Identity", ""]
    total = checks["profiles_total"][0].get("n") if checks["profiles_total"] else "?"
    orphans = checks["profiles_without_firebase_uid"]
    lines.append(f"profiles: **{total}** · without `firebase_uid`: **{len(orphans)}**")
    if orphans:
        lines += ["", "| id | username |", "|---|---|"]
        for row in orphans:
            if "id" in row:
                lines.append(f"| {row['id']} | {row['username']} |")

    lines += ["", "## Referential integrity", "", "| check | result |", "|---|---|"]
    for key, value in checks.items():
        if key.startswith("profiles_") or key == "media_rows":
            continue
        if value and isinstance(value[0], dict) and "n" in value[0]:
            lines.append(f"| {key} | {value[0]['n']} |")
        else:
            lines.append(f"| {key} | error |")

    lines += ["", "## Storage objects", "", "| bucket | files |", "|---|---|"]
    for row in checks["media_rows"]:
        if "bucket_id" in row:
            lines.append(f"| {row['bucket_id']} | {row['files']} |")

    lines += ["", "> Note: non-zero orphan or mismatch counts are migration blockers for the",
              "> affected entity, not for the whole pipeline. They are reported, never auto-fixed.", ""]
    (out_dir / "INTEGRITY.md").write_text("\n".join(lines))
    return checks


def main():
    stamp = datetime.now().strftime("%Y-%m-%d")
    out_dir = REPO / "migration-export" / stamp
    out_dir.mkdir(parents=True, exist_ok=True)

    tables = table_list()
    print(f"exporting {len(tables)} tables → {out_dir}", flush=True)

    counts, failures = {}, {}
    for name in tables:
        try:
            total, written = export_table(name, out_dir)
            counts[name] = total
            flag = "" if total == written else f"  (!) wrote {written} of {total}"
            print(f"  {name:28} {total:6}{flag}", flush=True)
        except Exception as exc:  # noqa: BLE001 - report and continue, never abort silently
            failures[name] = str(exc)[:200]
            print(f"  {name:28} FAILED: {failures[name]}", flush=True)

    checks = integrity(out_dir, counts)
    (out_dir / "summary.json").write_text(json.dumps(
        {"generated": datetime.now(timezone.utc).isoformat(), "counts": counts,
         "failures": failures}, indent=2))

    print(f"\nsummary  → {out_dir/'summary.json'}")
    print(f"report   → {out_dir/'INTEGRITY.md'}")
    if failures:
        print(f"WARNING: {len(failures)} table(s) failed to export")
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
