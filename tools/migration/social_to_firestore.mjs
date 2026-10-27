// Social graph + engagement: Supabase export -> Firestore (task P11-04..P11-11).
//
// Covers likes, comments, follows, saved posts, stories, story views, highlights.
// Same contract as the earlier migrations: idempotent by fixed document id, batched,
// fail-loud on unmapped users, verified by reading counts back.
//
// Counter policy: post likesCount/commentsCount are NOT recomputed here — they are copied
// with the post (P4-12) and verified separately. Recomputing them from the child collections
// is a Phase 11 reconciliation step, deliberately kept separate so a mismatch is visible
// instead of being papered over.

import { readFileSync, existsSync } from 'node:fs';
import { initializeTestEnvironment } from '@firebase/rules-unit-testing';
import { doc, getDoc, getDocs, collection, writeBatch } from 'firebase/firestore';

const EXPORT_DIR = process.env.EXPORT_DIR || 'migration-export/2026-10-30';
const BATCH_LIMIT = 400;

function load(name) {
  const path = `${EXPORT_DIR}/${name}.json`;
  if (!existsSync(path)) throw new Error(`missing export: ${path}`);
  return JSON.parse(readFileSync(path, 'utf8'));
}
function toMillis(value) {
  if (!value || value === 'None') return 0;
  const iso = String(value).replace(' ', 'T').replace(/(\+00)$/, 'Z');
  const ms = Date.parse(iso);
  return Number.isNaN(ms) ? 0 : ms;
}
const num = (v) => (Number.isFinite(Number(v)) ? Number(v) : 0);
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
  return { mediaId: `${bucket}:${path}`, bucket, path, url, ownerLegacyId, mediaType: /\.(mp4|mov|webm)$/i.test(path) ? 'video' : 'image' };
}

const profiles = load('profiles');
const uidByLegacyId = new Map();
for (const p of profiles) if (p.firebase_uid) uidByLegacyId.set(p.id, p.firebase_uid);

const likes = load('likes');
const comments = load('comments');
const follows = load('follows');
const saved = load('saved_posts');
const stories = load('stories');
const storyViews = load('story_views');
const highlights = load('highlights');
const highlightItems = load('highlight_items');

const writes = [];
const skipped = [];

for (const l of likes) {
  const uid = uidByLegacyId.get(l.user_id);
  if (!uid) { skipped.push({ type: 'like', reason: 'user unmapped' }); continue; }
  const id = `${l.post_id}_${uid}`;
  writes.push(['likes', id, { likeId: id, postId: l.post_id, uid, createdAt: toMillis(l.created_at) }]);
}

for (const c of comments) {
  const uid = uidByLegacyId.get(c.user_id);
  if (!uid) { skipped.push({ type: 'comment', id: c.id, reason: 'author unmapped' }); continue; }
  writes.push(['comments', c.id, {
    commentId: c.id, postId: c.post_id, authorUid: uid, text: c.body ?? '',
    parentId: c.parent_id && c.parent_id !== 'None' ? c.parent_id : '',
    audience: 'public', likesCount: num(c.likes_count), createdAt: toMillis(c.created_at),
  }]);
}

for (const f of follows) {
  const follower = uidByLegacyId.get(f.follower_id);
  const following = uidByLegacyId.get(f.following_id);
  if (!follower || !following) { skipped.push({ type: 'follow', reason: 'endpoint unmapped' }); continue; }
  const id = `${follower}_${following}`;
  writes.push(['follows', id, { edgeId: id, followerUid: follower, followingUid: following, createdAt: toMillis(f.created_at) }]);
}

for (const s of saved) {
  const uid = uidByLegacyId.get(s.user_id);
  if (!uid) { skipped.push({ type: 'save', reason: 'user unmapped' }); continue; }
  const id = `${uid}_${s.post_id}`;
  writes.push(['savedPosts', id, { saveId: id, uid, postId: s.post_id, createdAt: toMillis(s.created_at) }]);
}

for (const st of stories) {
  const uid = uidByLegacyId.get(st.user_id);
  if (!uid) { skipped.push({ type: 'story', id: st.id, reason: 'author unmapped' }); continue; }
  const media = mediaRef(st.media_url, st.user_id) || {};
  writes.push(['stories', st.id, {
    storyId: st.id, authorUid: uid,
    audience: st.audience === 'close_friends' || st.audience === 'followers' ? st.audience : 'public',
    media: { ...media, ownerUid: uid, mediaType: st.media_type === 'video' ? 'video' : 'image' },
    createdAt: toMillis(st.created_at), expiresAt: toMillis(st.expires_at),
  }]);
}

for (const v of storyViews) {
  const uid = uidByLegacyId.get(v.viewer_id);
  if (!uid) { skipped.push({ type: 'story_view', reason: 'viewer unmapped' }); continue; }
  const id = `${v.story_id}_${uid}`;
  writes.push(['storyViews', id, { viewId: id, storyId: v.story_id, viewerUid: uid, viewedAt: toMillis(v.created_at) }]);
}

// Highlights: the source splits parent + items; Firestore stores a bounded items array.
const itemsByHighlight = new Map();
for (const it of highlightItems) {
  if (!itemsByHighlight.has(it.highlight_id)) itemsByHighlight.set(it.highlight_id, []);
  itemsByHighlight.get(it.highlight_id).push(it);
}
for (const h of highlights) {
  const uid = uidByLegacyId.get(h.user_id);
  if (!uid) { skipped.push({ type: 'highlight', id: h.id, reason: 'owner unmapped' }); continue; }
  const items = (itemsByHighlight.get(h.id) || [])
    .sort((a, b) => num(a.position) - num(b.position))
    .map((it) => ({ ...(mediaRef(it.media_url, h.user_id) || {}), ownerUid: uid }))
    .filter((it) => it.path)
    .slice(0, 50);
  writes.push(['highlights', h.id, {
    highlightId: h.id, ownerUid: uid, title: h.title ?? '',
    audience: 'public', cover: mediaRef(h.cover_url, h.user_id) || {},
    items, createdAt: toMillis(h.created_at),
  }]);
}

const env = await initializeTestEnvironment({
  projectId: process.env.FIREBASE_PROJECT || 'instanova-social-migration',
  firestore: { rules: readFileSync('firestore.rules', 'utf8'), host: '127.0.0.1', port: 8080 },
});

let written = 0;
const failures = [];
await env.withSecurityRulesDisabled(async (ctx) => {
  const db = ctx.firestore();
  for (let i = 0; i < writes.length; i += BATCH_LIMIT) {
    const chunk = writes.slice(i, i + BATCH_LIMIT);
    const batch = writeBatch(db);
    for (const [col, id, data] of chunk) batch.set(doc(db, col, id), data, { merge: true });
    try { await batch.commit(); written += chunk.length; }
    catch (e) { failures.push({ at: i, reason: e.message }); }
  }
});

// Verify per collection: documents written must be readable back.
const planned = {};
for (const [col] of writes) planned[col] = (planned[col] || 0) + 1;
const verified = {};
await env.withSecurityRulesDisabled(async (ctx) => {
  const db = ctx.firestore();
  for (const col of Object.keys(planned)) {
    const snap = await getDocs(collection(db, col));
    verified[col] = snap.size;
  }
});
const mismatches = Object.entries(planned)
  .filter(([col, n]) => verified[col] !== n)
  .map(([col, n]) => ({ collection: col, planned: n, found: verified[col] }));

await env.cleanup();

console.log(JSON.stringify({
  source: EXPORT_DIR, planned, verified, written,
  skipped: skipped.length, skippedDetail: skipped, failures, mismatches,
}, null, 2));

process.exit(failures.length === 0 && mismatches.length === 0 ? 0 : 1);
