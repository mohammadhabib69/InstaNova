// InstaNova — Firestore security-rules tests (migration task P2-08).
//
// Verifies BOTH sides of the baseline rules: the deny side (already proven with live
// unauthenticated HTTP calls) and the allow side, which can only be proved with a real
// authenticated context. Runs against the Firestore emulator with the rules file from
// the repo, so it can never drift from what is deployed.
//
//   npm test    (or: firebase emulators:exec --only firestore "npm test")
//
// Not shipped in the APK; no Firebase credentials required.

import { readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

import {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} from '@firebase/rules-unit-testing';
import { doc, getDoc, setDoc, deleteDoc } from 'firebase/firestore';

const here = dirname(fileURLToPath(import.meta.url));
const rulesPath = resolve(here, '../../firestore.rules');

const testEnv = await initializeTestEnvironment({
  projectId: 'instanova-rules-test',
  firestore: {
    rules: readFileSync(rulesPath, 'utf8'),
    host: '127.0.0.1',
    port: 8080,
  },
});

const results = [];
async function check(name, expectation, action) {
  try {
    if (expectation === 'allow') await assertSucceeds(action());
    else await assertFails(action());
    results.push({ name, expected: expectation, ok: true });
  } catch (error) {
    results.push({ name, expected: expectation, ok: false, error: error.message ?? String(error) });
  }
}

const anon = testEnv.unauthenticatedContext().firestore();
const alice = testEnv.authenticatedContext('alice').firestore();
const bob = testEnv.authenticatedContext('bob').firestore();

// ---- seed through a privileged context (bypasses rules) --------------------
await testEnv.withSecurityRulesDisabled(async (ctx) => {
  const db = ctx.firestore();
  await setDoc(doc(db, 'users/alice'), { uid: 'alice', username: 'alice' });
  await setDoc(doc(db, 'publicProfiles/alice'), { uid: 'alice', username: 'alice' });
  await setDoc(doc(db, 'publicProfiles/bob'), { uid: 'bob', username: 'bob' });
  await setDoc(doc(db, 'posts/p1'), { authorUid: 'alice', caption: 'seeded' });
});

// ---- users/{uid}: private, owner-only -------------------------------------
await check('anon reads users/alice', 'deny', () => getDoc(doc(anon, 'users/alice')));
await check('owner reads users/alice', 'allow', () => getDoc(doc(alice, 'users/alice')));
await check('other user reads users/alice', 'deny', () => getDoc(doc(bob, 'users/alice')));
await check('owner creates users/alice', 'allow', () =>
  setDoc(doc(alice, 'users/alice'), { uid: 'alice', fcmTokens: { d1: 't' } }, { merge: true }));
await check('owner updates users/alice', 'allow', () =>
  setDoc(doc(alice, 'users/alice'), { bio: 'updated' }, { merge: true }));
await check('other user writes users/alice', 'deny', () =>
  setDoc(doc(bob, 'users/alice'), { bio: 'pwned' }, { merge: true }));
await check('other user creates users/bob (own uid, allowed)', 'allow', () =>
  setDoc(doc(bob, 'users/bob'), { uid: 'bob' }));
await check('owner deletes users/alice', 'deny', () => deleteDoc(doc(alice, 'users/alice')));

// ---- publicProfiles/{uid}: signed-in read, owner write --------------------
await check('anon reads publicProfiles/bob', 'deny', () => getDoc(doc(anon, 'publicProfiles/bob')));
await check('signed-in reads publicProfiles/bob', 'allow', () => getDoc(doc(alice, 'publicProfiles/bob')));
await check('owner writes publicProfiles/alice', 'allow', () =>
  setDoc(doc(alice, 'publicProfiles/alice'), { bio: 'hi' }, { merge: true }));
await check('other user writes publicProfiles/bob', 'deny', () =>
  setDoc(doc(alice, 'publicProfiles/bob'), { bio: 'pwned' }, { merge: true }));

// ---- everything not migrated yet stays closed -----------------------------
// posts migrated in Phase 4 — this pair used to assert "not migrated yet".
await check('signed-in reads a public post', 'allow', () => getDoc(doc(alice, 'posts/p1')));
await check('signed-in cannot spoof a post author', 'deny', () =>
  setDoc(doc(alice, 'posts/p2'), { postId: 'p2', authorUid: 'someone-else' }));
await check('anon reads notifications/n1', 'deny', () => getDoc(doc(anon, 'notifications/n1')));

// ---- P2-10: field validation must reject what Postgres used to reject ----
// These must write to the OWNER's document: a different doc id would be denied by the
// ownership rule instead, which would make the test pass for the wrong reason.
await check('owner sets invalid username', 'deny', () =>
  setDoc(doc(alice, 'users/alice'), { username: 'Bad Name' }, { merge: true }));
await check('owner sets uppercase username', 'deny', () =>
  setDoc(doc(alice, 'users/alice'), { username: 'Habib' }, { merge: true }));
await check('owner claims another uid', 'deny', () =>
  setDoc(doc(alice, 'users/alice'), { uid: 'bob' }, { merge: true }));
await check('owner writes bio over 200 chars', 'deny', () =>
  setDoc(doc(alice, 'users/alice'), { bio: 'x'.repeat(201) }, { merge: true }));
await check('owner writes valid username', 'allow', () =>
  setDoc(doc(alice, 'users/alice'), { username: 'habib.r_99', bio: 'hi' }, { merge: true }));

// ---- P10: settings live on the owner-only user document --------------
await check('owner writes a settings map', 'allow', () =>
  setDoc(doc(alice, 'users/alice'), { settings: { notifyLikes: false, privateAccount: true } }, { merge: true }));

await check('stranger writes into my settings', 'deny', () =>
  setDoc(doc(bob, 'users/alice'), { settings: { privateAccount: false } }, { merge: true }));

await check('stranger reads my settings', 'deny', () => getDoc(doc(bob, 'users/alice')));

await testEnv.cleanup();

// ---- report ---------------------------------------------------------------
const failed = results.filter((r) => !r.ok);
for (const r of results) {
  const mark = r.ok ? 'PASS' : 'FAIL';
  console.log(`${mark}  expect ${r.expected.padEnd(5)}  ${r.name}${r.ok ? '' : `  -> ${r.error}`}`);
}
console.log(`\n${results.length - failed.length}/${results.length} rules tests passed`);
process.exit(failed.length === 0 ? 0 : 1);
