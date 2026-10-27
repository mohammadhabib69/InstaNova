// Expired story cleanup tests (P5-04) — run against the Firestore emulator.
//
//   firebase emulators:exec --only firestore "npm test"     (from functions/)

import { createRequire } from 'node:module';
import { initializeApp } from 'firebase-admin/app';
import { getFirestore } from 'firebase-admin/firestore';

const require = createRequire(import.meta.url);
const { cleanupExpiredStories } = require('../lib/cleanup.js');

initializeApp({ projectId: process.env.GCLOUD_PROJECT || 'instanova-functions-test' });
const db = getFirestore();

const results = [];
const check = (name, ok, detail) => results.push({ name, ok: Boolean(ok), detail });

async function clear(collection) {
  const snap = await db.collection(collection).get();
  if (snap.empty) return;
  const batch = db.batch();
  snap.docs.forEach((d) => batch.delete(d.ref));
  await batch.commit();
}

await clear('stories');
await clear('storyViews');

const now = Date.now();
const hour = 60 * 60 * 1000;

await db.doc('stories/old1').set({ storyId: 'old1', authorUid: 'a', audience: 'public', expiresAt: now - 2 * hour });
await db.doc('stories/old2').set({ storyId: 'old2', authorUid: 'b', audience: 'public', expiresAt: now - 1 * hour });
await db.doc('stories/live1').set({ storyId: 'live1', authorUid: 'c', audience: 'public', expiresAt: now + 6 * hour });

await db.doc('storyViews/v1').set({ viewId: 'v1', storyId: 'old1', viewerUid: 'x' });
await db.doc('storyViews/v2').set({ viewId: 'v2', storyId: 'old1', viewerUid: 'y' });
await db.doc('storyViews/v3').set({ viewId: 'v3', storyId: 'live1', viewerUid: 'z' });

const result = await cleanupExpiredStories(db, now);

check('deletes both expired stories', result.deletedStories === 2, JSON.stringify(result));
check('deletes the views of deleted stories', result.deletedViews === 2, JSON.stringify(result));
check('reports no remaining backlog', result.remaining === false, String(result.remaining));

check('old1 is gone', !(await db.doc('stories/old1').get()).exists);
check('old2 is gone', !(await db.doc('stories/old2').get()).exists);
check('the live story survives', (await db.doc('stories/live1').get()).exists);
check('views of a deleted story are gone', !(await db.doc('storyViews/v1').get()).exists);
check('views of the live story survive', (await db.doc('storyViews/v3').get()).exists);

// Second run is a no-op: expiry cleanup must be safe to run on a schedule forever.
const again = await cleanupExpiredStories(db, now);
check('a second run deletes nothing', again.deletedStories === 0 && again.deletedViews === 0, JSON.stringify(again));

// A future timestamp must not be treated as expired.
const future = await cleanupExpiredStories(db, now + 7 * hour);
check('the story expires once its time passes', future.deletedStories === 1, JSON.stringify(future));

let failed = 0;
for (const r of results) {
  if (!r.ok) failed += 1;
  console.log(`${r.ok ? 'PASS' : 'FAIL'}  ${r.name}${r.ok ? '' : `  -> ${r.detail}`}`);
}
console.log(`\n${results.length - failed}/${results.length} cleanup tests passed`);
process.exit(failed === 0 ? 0 : 1);
