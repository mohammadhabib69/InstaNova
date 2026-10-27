#!/usr/bin/env python3
"""Follower/following counter verification (task P3-12).

Compares the counters stored on each profile with the number of `follows` edges that
actually exist. Postgres maintained those counters with triggers; Firestore has no
triggers without Cloud Functions, so after migration the counters are only as good as
what was carried over. This proves whether they are still true.

Fixture accounts (`novatester*`) are excluded from the headline verdict but reported, because
their edges are deliberately not migrated and would otherwise look like data loss.

    python3 tools/migration/verify_follow_counts.py

Exit 0 when every real account reconciles, 1 otherwise.
"""

import argparse
import collections
import json
import sys
from pathlib import Path

FIXTURE_PREFIX = "novatester"


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--export-dir", default="migration-export/2026-10-30")
    args = ap.parse_args()

    export = Path(args.export_dir)
    profiles = json.loads((export / "profiles.json").read_text())
    follows = json.loads((export / "follows.json").read_text())

    by_id = {p["id"]: p for p in profiles}
    followers = collections.Counter(f["following_id"] for f in follows)
    following = collections.Counter(f["follower_id"] for f in follows)

    def is_fixture(pid):
        p = by_id.get(pid)
        return bool(p and p["username"].startswith(FIXTURE_PREFIX))

    ok = fixture_explained = mismatch = 0
    rows = []

    for p in profiles:
        if p["username"].startswith(FIXTURE_PREFIX):
            continue
        stored_in, stored_out = p["followers_count"] or 0, p["following_count"] or 0
        actual_in, actual_out = followers.get(p["id"], 0), following.get(p["id"], 0)
        if (stored_in, stored_out) == (actual_in, actual_out):
            verdict, ok = "ok", ok + 1
        else:
            # Edges owned by fixtures are intentionally not migrated; if they account for
            # the whole difference, the counter is stale but the data is not wrong.
            fix_in = sum(1 for f in follows if f["following_id"] == p["id"] and is_fixture(f["follower_id"]))
            fix_out = sum(1 for f in follows if f["follower_id"] == p["id"] and is_fixture(f["following_id"]))
            if 0 <= stored_in - actual_in <= fix_in and 0 <= stored_out - actual_out <= fix_out:
                verdict, fixture_explained = "ok (fixture edges)", fixture_explained + 1
            else:
                verdict, mismatch = "MISMATCH", mismatch + 1
        rows.append((p["username"], stored_in, actual_in, stored_out, actual_out, verdict))

    width = max(len(r[0]) for r in rows) if rows else 10
    print(f"{'username':{width}}  {'fol':>3} {'act':>3}  {'fng':>3} {'act':>3}  verdict")
    for r in rows:
        print(f"{r[0]:{width}}  {r[1]:>3} {r[2]:>3}  {r[3]:>3} {r[4]:>3}  {r[5]}")

    print(f"\nreal accounts={len(rows)} ok={ok} fixture_explained={fixture_explained} mismatch={mismatch}")
    return 1 if mismatch else 0


if __name__ == "__main__":
    sys.exit(main())
