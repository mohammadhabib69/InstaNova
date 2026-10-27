// Profile migration: Supabase `profiles` -> Firestore `users/{uid}` + `publicProfiles/{uid}`
// (tasks P3-11, P3-12; depends on the identity map from P1-08).
//
// Design rules:
//  * Keyed by Firebase uid, never the legacy UUID. The legacy id is preserved as
//    `legacyProfileId` because every Storage path begins with it.
//  * Two documents per person, deliberately: `users/{uid}` is owner-only and holds
//    birthday, settings and counters; `publicProfiles/{uid}` is readable by any signed-in
//    user and holds only what another person may see. Firestore has no column-level rules,
//    so the split is the only way to keep a private field private.
//  * Idempotent — `set(..., {merge:true})` keyed by uid.
//  * Fail-loud — a profile with no Firebase uid is SKIPPED and counted. The 12 `novatester`
//    seed rows are expected here; they have no Auth account by construction.
//  * fcm_token is NOT migrated. Device tokens belong to `devices/{uid}` (DeviceRepository)
//    and are re-registered by the app on next start; copying a stale token into a profile
//    document would create a second, wrong source of truth for push delivery.
//
// Usage:
//   firebase emulators:exec --only firestore "node tools/migration/profiles_to_firestore.mjs"

import { readFileSync, existsSync } from 'node:fs';
import { initializeTestEnvironment } from '@firebase/rules-unit-testing';
import { doc, getDoc, writeBatch } from 'firebase/firestore';

const EXPORT_DIR = process.env.EXPORT_DIR || 'migration-export/2026-10-30';
const BATCH_LIMIT = 400;

function load(name) {
  const path = `${EXPORT_DIR}/${name}.json`;
  if (!existsSync(path)) throw new Error(`missing export: ${path}`);
  return JSON.parse(readFileSync(path, 'utf8'));
}

/** "2026-10-19 20:28:53.870778+00" -> epoch millis (Supabase emits a space, not a T). */
function toMillis(value) {
  if (!value) return 0;
  const iso = String(value).replace(' ', 'T').replace(/(\+00)$/, 'Z');
  const ms = Date.parse(iso);
  return Number.isNaN(ms) ? 0 : ms;
}

/** Legacy empty values are either null or the literal string "None". */
function text(value) {
  if (value === null || value === undefined) return '';
  const s = String(value);
  return s === 'None' ? '' : s;
}

const profiles = load('profiles');
const identity = existsSync(`${EXPORT_DIR}/identity_map.json`)
  ? JSON.parse(readFileSync(`${EXPORT_DIR}/identity_map.json`, 'utf8'))
  : null;
if (!identity) {
  throw new Error('identity_map.json missing — run tools/migration/identity_map.py first (P1-08)');
}
const uidByLegacyId = new Map(identity.entries.map((e) => [e.legacyProfileId, e.firebaseUid]));

const now = Date.now();
const users = [];
const publics = [];
const skipped = [];

for (const p of profiles) {
  const uid = uidByLegacyId.get(p.id) || p.firebase_uid || '';
  if (!uid) {
    skipped.push({ username: p.username, reason: 'no firebase uid (fixture)' });
    continue;
  }
  const username = text(p.username);
  const createdAt = toMillis(p.created_at);
  const counters = {
    posts: Number(p.posts_count ?? 0),
    followers: Number(p.followers_count ?? 0),
    following: Number(p.following_count ?? 0),
  };

  users.push({
    id: uid,
    data: {
      uid,
      legacyProfileId: p.id,
      username,
      fullName: text(p.full_name),
      bio: text(p.bio),
      website: text(p.website),
      avatarUrl: text(p.avatar_url),
      isVerified: p.is_verified === true,
      isPrivate: p.is_private === true,
      accountType: text(p.account_type) || 'personal',
      birthday: text(p.birthday),
      showActivity: p.show_activity !== false,
      whoCanCall: text(p.who_can_call) || 'everyone',
      createdAt,
      updatedAt: now,
      counters,
      // The settings screen reads `settings` (FirestoreSettingsRepository), while the
      // profile reads the top-level fields above. Both are written so the two views agree
      // from the first launch instead of drifting until the first settings save.
      settings: {
        privateAccount: p.is_private === true,
        showActivity: p.show_activity !== false,
        whoCanCall: text(p.who_can_call) || 'everyone',
      },
      migratedAt: now,
    },
  });

  publics.push({
    id: uid,
    data: {
      uid,
      username,
      usernameLower: username.toLowerCase(),
      fullName: text(p.full_name),
      avatarUrl: text(p.avatar_url),
      bio: text(p.bio),
      website: text(p.website),
      isVerified: p.is_verified === true,
      isPrivate: p.is_private === true,
      followersCount: counters.followers,
      followingCount: counters.following,
      postsCount: counters.posts,
      migratedAt: now,
    },
  });
}

// Fields that must never appear in the public projection.
const FORBIDDEN_IN_PUBLIC = ['birthday', 'legacyProfileId', 'fcmToken', 'fcm_token', 'settings', 'whoCanCall'];
for (const doc of publics) {
  for (const f of FORBIDDEN_IN_PUBLIC) {
    if (f in doc.data) throw new Error(`public projection for ${doc.data.username} leaks ${f}`);
  }
}

const env = await initializeTestEnvironment({
  projectId: process.env.FIREBASE_PROJECT || 'instanova-migration-test',
  firestore: { rules: readFileSync('firestore.rules', 'utf8'), host: '127.0.0.1', port: 8080 },
});

let written = 0;
let failed = 0;
await env.withSecurityRulesDisabled(async (ctx) => {
  const db = ctx.firestore();
  for (const [collection, docs] of [['users', users], ['publicProfiles', publics]]) {
    for (let i = 0; i < docs.length; i += BATCH_LIMIT) {
      const chunk = docs.slice(i, i + BATCH_LIMIT);
      const batch = writeBatch(db);
      for (const d of chunk) batch.set(doc(db, collection, d.id), d.data, { merge: true });
      try {
        await batch.commit();
        written += chunk.length;
      } catch (e) {
        failed += chunk.length;
        console.error(`batch ${collection} @${i} failed: ${e.message}`);
      }
    }
  }
});

// ---- verification: read every document back ------------------------------------
let verified = 0;
const mismatches = [];
await env.withSecurityRulesDisabled(async (ctx) => {
  const db = ctx.firestore();
  for (const d of users) {
    const snap = await getDoc(doc(db, 'users', d.id));
    if (!snap.exists()) { mismatches.push(`users/${d.id} missing`); continue; }
    const got = snap.data();
    if (got.username !== d.data.username) mismatches.push(`users/${d.data.username}: username`);
    if (got.legacyProfileId !== d.data.legacyProfileId) mismatches.push(`users/${d.data.username}: legacyProfileId`);
    if ((got.counters?.followers ?? -1) !== d.data.counters.followers) mismatches.push(`users/${d.data.username}: followers counter`);
    verified += 1;
  }
  for (const d of publics) {
    const snap = await getDoc(doc(db, 'publicProfiles', d.id));
    if (!snap.exists()) { mismatches.push(`publicProfiles/${d.id} missing`); continue; }
    const got = snap.data();
    if (got.usernameLower !== d.data.usernameLower) mismatches.push(`publicProfiles/${d.data.username}: usernameLower`);
    if (got.uid !== d.id) mismatches.push(`publicProfiles/${d.data.username}: uid != doc id`);
    for (const f of FORBIDDEN_IN_PUBLIC) {
      if (f in got) mismatches.push(`publicProfiles/${d.data.username}: leaked ${f}`);
    }
    verified += 1;
  }
});

console.log(`planned users=${users.length} publicProfiles=${publics.length} skipped=${skipped.length}`);
console.log(`written=${written} failed=${failed} verified=${verified} mismatches=${mismatches.length}`);
if (skipped.length) {
  console.log('skipped:', skipped.map((s) => s.username).join(', '));
}
for (const m of mismatches) console.error('MISMATCH', m);
await env.cleanup();
process.exit(mismatches.length === 0 && failed === 0 ? 0 : 1);
