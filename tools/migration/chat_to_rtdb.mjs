// Conversations + messages: Supabase export -> Firebase Realtime Database (task P6-11).
//
// Same rules as the post migration: idempotent (writes by fixed key), batched, fail-loud,
// and verified by reading back. A conversation whose members cannot be mapped to Firebase
// uids is SKIPPED entirely — writing it with no members would produce a thread nobody can
// open, which is worse than an obviously missing thread.
//
// Media: `media_url` is carried as `mediaUrl` when present; the object itself stays in
// Supabase Storage and is never touched.

import { readFileSync, existsSync } from 'node:fs';
import { initializeTestEnvironment } from '@firebase/rules-unit-testing';
import { ref, set, get } from 'firebase/database';

const EXPORT_DIR = process.env.EXPORT_DIR || 'migration-export/2026-10-30';

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

const profiles = load('profiles');
const conversations = load('conversations');
const members = load('conversation_members');
const messages = load('messages');

const uidByLegacyId = new Map();
for (const p of profiles) {
  if (p.firebase_uid) uidByLegacyId.set(p.id, p.firebase_uid);
}

const membersByConversation = new Map();
for (const m of members) {
  if (!membersByConversation.has(m.conversation_id)) membersByConversation.set(m.conversation_id, []);
  membersByConversation.get(m.conversation_id).push(m);
}

const messagesByConversation = new Map();
for (const msg of messages) {
  if (!messagesByConversation.has(msg.conversation_id)) messagesByConversation.set(msg.conversation_id, []);
  messagesByConversation.get(msg.conversation_id).push(msg);
}

const chats = [];
const skipped = [];
const orphanMessages = [];

for (const conv of conversations) {
  const mapped = (membersByConversation.get(conv.id) || [])
    .map((m) => ({ legacyId: m.user_id, uid: uidByLegacyId.get(m.user_id), lastReadAt: toMillis(m.last_read_at) }))
    .filter((m) => m.uid);

  if (mapped.length === 0) {
    skipped.push({ id: conv.id, reason: 'no member maps to a firebase_uid' });
    continue;
  }

  const threadMessages = [];
  for (const msg of messagesByConversation.get(conv.id) || []) {
    const senderUid = uidByLegacyId.get(msg.sender_id);
    if (!senderUid) {
      orphanMessages.push({ id: msg.id, conversationId: conv.id, reason: 'sender has no firebase_uid' });
      continue;
    }
    const media = msg.media_url && msg.media_url !== 'None' ? msg.media_url : null;
    threadMessages.push({
      id: msg.id,
      data: {
        senderUid,
        text: msg.body ?? '',
        type: media ? 'image' : 'text',
        ...(media ? { mediaUrl: media } : {}),
        createdAt: toMillis(msg.created_at),
        legacyIsRead: msg.is_read === true,
      },
    });
  }

  chats.push({ id: conv.id, meta: { createdAt: toMillis(conv.created_at) }, members: mapped, messages: threadMessages });
}

const env = await initializeTestEnvironment({
  projectId: process.env.FIREBASE_PROJECT || 'instanova-chat-migration',
  database: { rules: readFileSync('database.rules.json', 'utf8'), host: '127.0.0.1', port: 9000 },
});

let writtenChats = 0;
let writtenMessages = 0;
let writtenMarkers = 0;
const failures = [];

await env.withSecurityRulesDisabled(async (ctx) => {
  const db = ctx.database();
  for (const chat of chats) {
    try {
      await set(ref(db, `chats/${chat.id}/meta`), chat.meta);
      for (const m of chat.members) {
        await set(ref(db, `chats/${chat.id}/members/${m.uid}`), true);
        // Carry the existing last_read_at across so unread badges survive the move.
        if (m.lastReadAt > 0) {
          await set(ref(db, `chats/${chat.id}/reads/${m.uid}`), { lastReadAt: m.lastReadAt });
          writtenMarkers += 1;
        }
      }
      for (const msg of chat.messages) {
        await set(ref(db, `chats/${chat.id}/messages/${msg.id}`), msg.data);
        writtenMessages += 1;
      }
      writtenChats += 1;
    } catch (e) {
      failures.push({ id: chat.id, reason: e.message });
    }
  }
});

// Verification: every chat must expose the same member/message counts as the source.
const mismatches = [];
let verified = 0;
await env.withSecurityRulesDisabled(async (ctx) => {
  const db = ctx.database();
  for (const chat of chats) {
    const membersSnap = await get(ref(db, `chats/${chat.id}/members`));
    const messagesSnap = await get(ref(db, `chats/${chat.id}/messages`));
    const memberCount = membersSnap.exists() ? Object.keys(membersSnap.val()).length : 0;
    const messageCount = messagesSnap.exists() ? Object.keys(messagesSnap.val()).length : 0;
    if (memberCount !== chat.members.length) {
      mismatches.push({ id: chat.id, reason: `members ${memberCount} != ${chat.members.length}` });
    } else if (messageCount !== chat.messages.length) {
      mismatches.push({ id: chat.id, reason: `messages ${messageCount} != ${chat.messages.length}` });
    } else {
      verified += 1;
    }
  }
});

await env.cleanup();

console.log(JSON.stringify({
  source: EXPORT_DIR,
  conversationsInExport: conversations.length,
  conversationsMigrated: chats.length,
  conversationsSkipped: skipped.length,
  skippedDetail: skipped,
  messagesInExport: messages.length,
  messagesMigrated: writtenMessages,
  orphanMessages,
  readMarkersMigrated: writtenMarkers,
  writtenChats,
  failures,
  verified,
  mismatches,
}, null, 2));

process.exit(failures.length === 0 && mismatches.length === 0 ? 0 : 1);
