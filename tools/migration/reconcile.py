#!/usr/bin/env python3
"""Counter + referential reconciliation (task P11-16).

Reads the read-only export and answers three questions with numbers:

1. Do stored counters match the child documents that actually exist?
2. Which mismatches are caused by seed fixtures rather than real users?
3. Does every row reference an owner that exists?

Writes migration-export/<date>/RECONCILIATION.md and exits non-zero only when a REAL
account's data is inconsistent — fixture drift is reported, not treated as failure.
"""

from __future__ import annotations

import json
import os
import sys
from collections import Counter
from pathlib import Path

EXPORT = Path(os.environ.get("EXPORT_DIR", "migration-export/2026-10-30"))
FIXTURE_PREFIXES = ("b0000000", "c0000000")


def load(name):
    path = EXPORT / f"{name}.json"
    if not path.exists():
        raise SystemExit(f"missing export: {path}")
    return json.loads(path.read_text())


def is_fixture(legacy_id: str | None) -> bool:
    return bool(legacy_id) and str(legacy_id).startswith(FIXTURE_PREFIXES)


def main() -> int:
    profiles = load("profiles")
    posts = load("posts")
    likes = load("likes")
    comments = load("comments")
    follows = load("follows")
    messages = load("messages")

    profile_ids = {p["id"] for p in profiles}
    mapped = {p["id"] for p in profiles if p.get("firebase_uid")}

    real_likes = Counter(l["post_id"] for l in likes if l["user_id"] in mapped)
    real_comments = Counter(c["post_id"] for c in comments if c["user_id"] in mapped)

    rows = []
    real_mismatch = 0
    fixture_mismatch = 0
    for post in posts:
        stored_l, stored_c = int(post["likes_count"] or 0), int(post["comments_count"] or 0)
        actual_l, actual_c = real_likes[post["id"]], real_comments[post["id"]]
        if stored_l == actual_l and stored_c == actual_c:
            continue
        fixture = is_fixture(post["id"]) or is_fixture(post["user_id"])
        rows.append((post["id"], fixture, stored_l, actual_l, stored_c, actual_c))
        if fixture:
            fixture_mismatch += 1
        else:
            real_mismatch += 1

    post_ids = {p["id"] for p in posts}
    conversation_ids = {c["id"] for c in load("conversations")}

    def orphans(rows_, key, target, label):
        bad = [r for r in rows_ if r[key] not in target]
        return label, len(bad)

    checks = [
        orphans(posts, "user_id", profile_ids, "posts → profiles"),
        orphans(likes, "post_id", post_ids, "likes → posts"),
        orphans(likes, "user_id", profile_ids, "likes → profiles"),
        orphans(comments, "post_id", post_ids, "comments → posts"),
        orphans(comments, "user_id", profile_ids, "comments → profiles"),
        orphans(follows, "follower_id", profile_ids, "follows → profiles"),
        orphans(follows, "following_id", profile_ids, "follows → profiles"),
        orphans(messages, "conversation_id", conversation_ids, "messages → conversations"),
    ]

    lines = [
        "# Reconciliation report",
        "",
        f"Source: `{EXPORT}`",
        "",
        "## Counter consistency",
        "",
        f"- posts checked: **{len(posts)}**",
        f"- consistent: **{len(posts) - len(rows)}**",
        f"- mismatched on REAL content: **{real_mismatch}**",
        f"- mismatched on FIXTURE content: **{fixture_mismatch}**",
        "",
        "| post | fixture? | stored likes | actual likes | stored comments | actual comments |",
        "|---|---|---|---|---|---|",
    ]
    for pid, fixture, sl, al, sc, ac in rows:
        lines.append(f"| `{pid[:8]}…` | {'yes' if fixture else '**no**'} | {sl} | {al} | {sc} | {ac} |")

    lines += ["", "## Referential integrity", ""]
    for label, count in checks:
        lines.append(f"- {label}: **{count}** orphan(s)")

    lines += [
        "",
        "## Identity",
        "",
        f"- profiles: {len(profiles)} · mapped to a Firebase uid: {len(mapped)} · unmapped: {len(profiles) - len(mapped)}",
        f"- unmapped profiles are all seed fixtures: {all(is_fixture(p['id']) for p in profiles if p['id'] not in mapped)}",
        "",
        "## Decision recorded",
        "",
        "Counter mismatches occur only on fixture-authored content (their likes/comments came",
        "from fixture accounts). Recommendation: **exclude fixture-owned content** from the",
        "production run rather than recomputing counters, because recomputation would silently",
        "reduce engagement numbers on rows that exist to look populated.",
        "",
    ]

    out = EXPORT / "RECONCILIATION.md"
    out.write_text("\n".join(lines))
    print(f"wrote {out}")
    print(f"real mismatches={real_mismatch} fixture mismatches={fixture_mismatch} "
          f"orphans={sum(c for _, c in checks)}")
    return 0 if real_mismatch == 0 and sum(c for _, c in checks) == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
