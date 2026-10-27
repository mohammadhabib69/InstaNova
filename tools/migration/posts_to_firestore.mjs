// Post metadata migration: Supabase export -> Firestore (task P4-12).
//
// Design rules this follows:
//  * Idempotent — every write is `set(..., {merge:true})` keyed by the legacy post UUID,
//    so a re-run cannot duplicate and cannot clobber a migrated field with null.
//  * Batched — 400 writes per commit (Firestore's cap is 500; the margin absorbs retries).
//  * Fail-loud — a post whose author has no Firebase uid is SKIPPED and counted, never
//    written with a placeholder author.
//  * Media untouched — only references are copied; Supabase Storage objects are not read,
//    moved or deleted.
//  * Verifiable — after writing, it reads every document back and compares.
//
// Usage (against the emulator):
//   firebase emulators:exec --only firestore "node tools/migration/posts_to_firestore.mjs"
// Production runs must use the Admin SDK with a service account; the client SDK here is
// for emulator proof only.

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

/** The stored URLs are the legacy "/storage/v1/object/<bucket>/<path>" shape. */
function mediaRef(url, ownerLegacyId) {
  if (!url || url === 'None') return null;
  const marker = '/storage/v1/object/';
  const at = url.indexOf(marker);
  if (at < 0) return null;
  let rest = url.slice(at + marker.length);
  if (rest.startsWith('public/')) rest = rest.slice(7);
  else if (rest.startsWith('authenticated/')) rest = rest.slice(14);
  const slash = rest.indexOf('/');
  if (slash <= 0) return null;
  const bucket = rest.slice(0, slash);
  const path = rest.slice(slash + 1);
  return {
    mediaId: `${bucket}:${path}`,
    bucket,
    path,
    url,
    ownerLegacyId,
    mediaType: /\.(mp4|mov|webm)$/i.test(path) ? 'video' : 'image',
  };
}

const profiles = load('profiles');
const posts = load('posts');
const postMedia = load('post_media');

const uidByLegacyId = new Map();
for (const p of profiles) {
  if (p.firebase_uid) uidByLegacyId.set(p.id, p.firebase_uid);
}
const privacyByLegacyId = new Map(profiles.map((p) => [p.id, p.is_private === true]));

const mediaByPost = new Map();
for (const m of postMedia) {
  if (!mediaByPost.has(m.post_id)) mediaByPost.set(m.post_id, []);
  mediaByPost.get(m.post_id).push(m);
}

const docs = [];
const skipped = [];
for (const post of posts) {
  const authorUid = uidByLegacyId.get(post.user_id);
  if (!authorUid) {
    skipped.push({ id: post.id, reason: 'author has no firebase_uid', authorLegacyId: post.user_id });
    continue;
  }
  const media = [];
  for (const m of (mediaByPost.get(post.id) || []).sort((a, b) => (a.position ?? 0) - (b.position ?? 0))) {
    const ref = mediaRef(m.url, post.user_id);
    if (ref) media.push({ ...ref, ownerUid: authorUid });
  }
  const thumb = mediaRef(post.thumbnail_url, post.user_id);
  if (thumb) media.push({ ...thumb, ownerUid: authorUid, mediaType: 'image', role: 'thumbnail' });
  const video = mediaRef(post.video_url, post.user_id);
  if (video) media.push({ ...video, ownerUid: authorUid, mediaType: 'video', role: 'video' });

  docs.push({
    id: post.id,
    data: {
      postId: post.id,
      authorUid,
      authorLegacyId: post.user_id,
      caption: post.caption ?? '',
      location: post.location && post.location !== 'None' ? post.location : '',
      audience: privacyByLegacyId.get(post.user_id) ? 'followers' : 'public',
      archived: post.is_archived === true,
      isReel: post.is_reel === true,
      space: post.space ?? 'insta',
      media,
      likesCount: Number(post.likes_count ?? 0),
      commentsCount: Number(post.comments_count ?? 0),
      createdAt: toMillis(post.created_at),
      migratedAt: Date.now(),
    },
  });
}

const env = await initializeTestEnvironment({
  projectId: process.env.FIREBASE_PROJECT || 'instanova-migration-test',
  firestore: { rules: readFileSync('firestore.rules', 'utf8'), host: '127.0.0.1', port: 8080 },
});

let written = 0;
let failed = 0;
await env.withSecurityRulesDisabled(async (ctx) => {
  const db = ctx.firestore();
  for (let i = 0; i < docs.length; i += BATCH_LIMIT) {
    const chunk = docs.slice(i, i + BATCH_LIMIT);
    const batch = writeBatch(db);
    for (const d of chunk) batch.set(doc(db, 'posts', d.id), d.data, { merge: true });
    try {
      await batch.commit();
      written += chunk.length;
    } catch (e) {
      failed += chunk.length;
      console.error(`batch @${i} failed: ${e.message}`);
    }
  }
});

// Verification: every written document must exist and keep its author attribution.
let verified = 0;
const mismatches = [];
await env.withSecurityRulesDisabled(async (ctx) => {
  const db = ctx.firestore();
  for (const d of docs) {
    const snap = await getDoc(doc(db, 'posts', d.id));
    if (!snap.exists()) { mismatches.push({ id: d.id, reason: 'missing' }); continue; }
    const data = snap.data();
    if (data.authorUid !== d.data.authorUid) {
      mismatches.push({ id: d.id, reason: `author ${data.authorUid} != ${d.data.authorUid}` });
    } else if ((data.media?.length ?? 0) !== d.data.media.length) {
      mismatches.push({ id: d.id, reason: `media ${data.media?.length ?? 0} != ${d.data.media.length}` });
    } else {
      verified += 1;
    }
  }
});

await env.cleanup();

console.log(JSON.stringify({
  source: EXPORT_DIR,
  postsInExport: posts.length,
  mapped: docs.length,
  skipped: skipped.length,
  skippedDetail: skipped,
  written,
  failed,
  verified,
  mismatches,
  totalMediaRefs: docs.reduce((n, d) => n + d.data.media.length, 0),
}, null, 2));

process.exit(failed === 0 && mismatches.length === 0 ? 0 : 1);
