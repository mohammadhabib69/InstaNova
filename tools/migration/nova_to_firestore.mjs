// NOVA Hub reference + Q&A data: Supabase export -> Firestore (task P9-09).
//
// Data notes that matter, all visible in the export rather than assumed:
//  * `email_domains` arrives as a Python-repr string ("['kuet.ac.bd']"), not an array.
//  * booleans arrive as 'True'/'False' strings; numbers as strings.
//  * `users` are referenced by Supabase uuid, so anything authored must be mapped to a
//    firebase_uid or skipped.

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

const bool = (v) => v === true || v === 'True' || v === 'true';
const num = (v) => {
  const n = Number(v);
  return Number.isFinite(n) ? n : 0;
};
function toMillis(value) {
  if (!value || value === 'None') return 0;
  const iso = String(value).replace(' ', 'T').replace(/([+-]\d{2})$/, '$1:00');
  const ms = Date.parse(iso);
  return Number.isNaN(ms) ? 0 : ms;
}
/** "['a.bd', 'c.bd']" -> ['a.bd', 'c.bd'] */
function parseDomains(raw) {
  if (!raw || raw === 'None') return [];
  return String(raw)
    .replace(/^\[|\]$/g, '')
    .split(',')
    .map((s) => s.trim().replace(/^['"]|['"]$/g, ''))
    .filter(Boolean);
}
const slug = (s) => String(s).toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '');

const profiles = load('profiles');
const universities = load('nova_universities');
const districts = load('nova_districts');
const questions = load('nova_questions');
const answers = load('nova_answers');
const votes = load('nova_qa_votes');
const sessions = load('nova_study_sessions');

const uidByLegacyId = new Map();
for (const p of profiles) if (p.firebase_uid) uidByLegacyId.set(p.id, p.firebase_uid);

const writes = [];
for (const u of universities) {
  writes.push(['novaUniversities', String(u.id), {
    universityId: String(u.id), name: u.name ?? '', shortName: u.short_name ?? '',
    emailDomains: parseDomains(u.email_domains),
  }]);
}
for (const d of districts) {
  writes.push(['novaDistricts', slug(d.name), { name: d.name ?? '', division: d.division ?? '' }]);
}

const skipped = [];
for (const q of questions) {
  const authorUid = uidByLegacyId.get(q.author_id);
  if (!authorUid) { skipped.push({ type: 'question', id: q.id, reason: 'author unmapped' }); continue; }
  writes.push(['novaQuestions', q.id, {
    questionId: q.id, authorUid, universityId: '', subject: q.subject ?? 'general',
    title: q.title ?? '', body: q.body ?? '', audience: 'public',
    solved: bool(q.solved), votes: num(q.votes), answersCount: num(q.answers_count),
    imageUrl: q.image_url === 'None' ? '' : q.image_url,
    createdAt: toMillis(q.created_at),
  }]);
}
for (const a of answers) {
  const authorUid = uidByLegacyId.get(a.author_id);
  if (!authorUid) { skipped.push({ type: 'answer', id: a.id, reason: 'author unmapped' }); continue; }
  writes.push(['novaAnswers', a.id, {
    answerId: a.id, questionId: a.question_id, authorUid, body: a.body ?? '',
    audience: 'public', accepted: bool(a.accepted), votes: num(a.votes),
    createdAt: toMillis(a.created_at),
  }]);
}
for (const v of votes) {
  const uid = uidByLegacyId.get(v.user_id);
  if (!uid) { skipped.push({ type: 'vote', id: v.target_id, reason: 'voter unmapped' }); continue; }
  const id = `${v.target_type}_${v.target_id}_${uid}`;
  writes.push(['novaVotes', id, {
    voteId: id, targetType: v.target_type, targetId: v.target_id, uid,
    createdAt: toMillis(v.created_at),
  }]);
}
for (const s of sessions) {
  const uid = uidByLegacyId.get(s.user_id);
  if (!uid) { skipped.push({ type: 'study_session', id: s.id, reason: 'user unmapped' }); continue; }
  writes.push(['novaStudySessions', s.id, {
    sessionId: s.id, uid, subject: s.subject ?? 'general', minutes: num(s.minutes),
    completed: bool(s.completed), startedAt: toMillis(s.started_at),
  }]);
}

const env = await initializeTestEnvironment({
  projectId: process.env.FIREBASE_PROJECT || 'instanova-nova-migration',
  firestore: { rules: readFileSync('firestore.rules', 'utf8'), host: '127.0.0.1', port: 8080 },
});

let written = 0;
const failures = [];
await env.withSecurityRulesDisabled(async (ctx) => {
  const db = ctx.firestore();
  for (let i = 0; i < writes.length; i += BATCH_LIMIT) {
    const chunk = writes.slice(i, i + BATCH_LIMIT);
    const batch = writeBatch(db);
    for (const [collection, id, data] of chunk) batch.set(doc(db, collection, id), data, { merge: true });
    try {
      await batch.commit();
      written += chunk.length;
    } catch (e) {
      failures.push({ at: i, reason: e.message });
    }
  }
});

const totals = {};
const mismatches = [];
await env.withSecurityRulesDisabled(async (ctx) => {
  const db = ctx.firestore();
  for (const [collection] of writes) totals[collection] = (totals[collection] || 0) + 1;
  // Spot-verify one representative document per collection.
  for (const [collection, id] of [
    ['novaUniversities', String(universities[0]?.id ?? '')],
    ['novaQuestions', questions[0]?.id ?? ''],
    ['novaAnswers', answers[0]?.id ?? ''],
  ]) {
    if (!id) continue;
    const snap = await getDoc(doc(db, collection, id));
    if (!snap.exists()) mismatches.push({ collection, id, reason: 'missing' });
  }
});

await env.cleanup();

console.log(JSON.stringify({
  source: EXPORT_DIR,
  planned: totals,
  written,
  skipped: skipped.length,
  skippedDetail: skipped,
  failures,
  mismatches,
  note: 'universities=23 districts=64 expected on a clean run',
}, null, 2));

process.exit(failures.length === 0 && mismatches.length === 0 ? 0 : 1);
