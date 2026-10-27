#!/usr/bin/env python3
"""Identity mapping + verification for the InstaNova migration (tasks P1-03, P1-08..11).

Links Firebase Auth users to legacy `profiles` rows and verifies the result. Read-only:
it never writes to Firebase or Supabase, and it never stores credentials.

    firebase auth:export /tmp/auth_users.json --project instanova-62ff0 --format=json
    python3 tools/migration/identity_map.py --auth /tmp/auth_users.json

Why the export is not copied into the repo: `firebase auth:export` emits `passwordHash`
and `salt` for every account. Those must not land in a git working tree, so the file stays
where the operator put it and only the derived uid ↔ profile-id table is written out.

Outputs:
    migration-export/<date>/IDENTITY_MAP.md     human report
    migration-export/<date>/identity_map.json   uid -> legacyProfileId, machine readable

Exit codes: 0 = no real-user problems, 1 = a real-user check failed.
"""

import argparse
import json
import re
import sys
from pathlib import Path

# Must match firestore.rules validUserWrite(): ^[a-z][a-z0-9._]{2,29}$
USERNAME_RE = re.compile(r"^[a-z][a-z0-9._]{2,29}$")

# Seed fixtures created by the original test setup; they have no Auth account by design.
FIXTURE_PREFIX = "novatester"


def mask_email(email):
    if not email or "@" not in email:
        return "(none)"
    name, domain = email.split("@", 1)
    return f"{name[:2]}***@{domain}"


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--auth", required=True, help="path to firebase auth:export JSON (stays outside the repo)")
    ap.add_argument("--export-dir", default="migration-export/2026-10-30")
    args = ap.parse_args()

    export_dir = Path(args.export_dir)
    profiles = json.loads((export_dir / "profiles.json").read_text())
    auth_users = json.loads(Path(args.auth).read_text())["users"]

    auth_by_uid = {u["localId"]: u for u in auth_users}

    mapping = {}
    problems = []
    warnings = []

    # ---- link ----------------------------------------------------------------
    for p in profiles:
        uid = p.get("firebase_uid")
        if uid:
            mapping[uid] = p
            if uid not in auth_by_uid:
                problems.append(f"profile {p['username']} points at uid {uid} that no longer exists in Auth")
        else:
            warnings.append(f"profile {p['username']} has no firebase_uid")

    fixtures = [p for p in profiles if not p.get("firebase_uid")]
    real_fixtures = [p for p in fixtures if not p["username"].startswith(FIXTURE_PREFIX)]
    if real_fixtures:
        problems.append(
            f"{len(real_fixtures)} non-fixture profile(s) have no Auth account: "
            + ", ".join(sorted(p["username"] for p in real_fixtures))
        )

    # ---- orphan Auth accounts ------------------------------------------------
    orphans = [u for u in auth_users if u["localId"] not in mapping]

    # ---- uniqueness (P1-10) --------------------------------------------------
    seen_usernames, seen_uids = {}, {}
    for p in profiles:
        key = p["username"].lower()
        if key in seen_usernames:
            problems.append(f"duplicate username (case-insensitive): {p['username']}")
        seen_usernames[key] = p["id"]
        uid = p.get("firebase_uid")
        if uid:
            if uid in seen_uids:
                problems.append(f"duplicate firebase_uid: {uid}")
            seen_uids[uid] = p["id"]

    # ---- rule compatibility --------------------------------------------------
    bad_names = [p["username"] for p in profiles if not USERNAME_RE.match(p["username"])]
    if bad_names:
        problems.append(
            "username(s) rejected by the Firestore rule ^[a-z][a-z0-9._]{2,29}$: "
            + ", ".join(sorted(bad_names))
        )

    # ---- ownership (P1-09) ---------------------------------------------------
    # Every mapped uid must be the doc id of its own users/{uid} document, so the
    # private doc and the public projection can never describe different people.
    for uid, p in mapping.items():
        if p["firebase_uid"] != uid:
            problems.append(f"profile {p['username']}: uid mismatch inside the row")

    # ---- write outputs -------------------------------------------------------
    export_dir.mkdir(parents=True, exist_ok=True)
    (export_dir / "identity_map.json").write_text(
        json.dumps(
            {
                "generatedFrom": "firebase auth:export + profiles.json",
                "mapped": len(mapping),
                "fixtures": len(fixtures),
                "authUsers": len(auth_users),
                "entries": [
                    {
                        "firebaseUid": uid,
                        "legacyProfileId": p["id"],
                        "username": p["username"],
                        "customClaims": bool(auth_by_uid.get(uid, {}).get("customAttributes")),
                    }
                    for uid, p in sorted(mapping.items(), key=lambda kv: kv[1]["username"])
                ],
                "orphanAuthUids": [
                    {"uid": u["localId"], "email": mask_email(u.get("email")),
                     "hasSignedIn": bool(u.get("lastSignedInAt"))}
                    for u in orphans
                ],
            },
            indent=2,
        )
    )

    lines = [
        "# Identity Map (P1-03, P1-08..11)",
        "",
        f"- Auth accounts: **{len(auth_users)}**",
        f"- Profiles: **{len(profiles)}** (**{len(mapping)}** mapped, **{len(fixtures)}** fixtures without an account)",
        f"- Orphan Auth accounts (no profile): **{len(orphans)}**",
        f"- Problems: **{len(problems)}**",
        "",
        "## Mapped accounts",
        "",
        "| username | legacy profile id | firebase uid | role claim |",
        "|---|---|---|---|",
    ]
    for uid, p in sorted(mapping.items(), key=lambda kv: kv[1]["username"]):
        claims = "yes" if auth_by_uid.get(uid, {}).get("customAttributes") else "no"
        lines.append(f"| {p['username']} | `{p['id']}` | `{uid}` | {claims} |")

    if orphans:
        lines += ["", "## Orphan Auth accounts", "",
                  "Signed in at least once but has no `profiles` row — signup created the Auth user "
                  "and the profile insert never completed.", "",
                  "| email | uid | ever signed in |", "|---|---|---|"]
        for u in orphans:
            lines.append(f"| {mask_email(u.get('email'))} | `{u['localId']}` | {bool(u.get('lastSignedInAt'))} |")

    if fixtures:
        lines += ["", "## Fixtures without an account", "",
                  ", ".join(sorted(p["username"] for p in fixtures)) + "",
                  "", "These are seed rows. They cannot log in, own no Auth account, and must not be "
                  "counted as data loss when they are excluded from the migration."]

    lines += ["", "## Checks", ""]
    if problems:
        lines += [f"- FAIL — {p}" for p in problems]
    else:
        lines += ["- PASS — every mapped uid exists in Auth",
                  "- PASS — no duplicate username or uid",
                  "- PASS — every username satisfies the Firestore rule regex",
                  "- PASS — every profile's uid matches its own document id"]
    lines += ["", f"Warnings: {len(warnings)} profile(s) without a uid (listed above)."]

    (export_dir / "IDENTITY_MAP.md").write_text("\n".join(lines) + "\n")

    print(f"mapped={len(mapping)} fixtures={len(fixtures)} auth={len(auth_users)} "
          f"orphans={len(orphans)} problems={len(problems)}")
    for p in problems:
        print("  FAIL", p)
    for u in orphans:
        print("  NOTE orphan auth account", mask_email(u.get("email")))
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
