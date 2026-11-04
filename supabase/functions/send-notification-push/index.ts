import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "npm:@supabase/supabase-js@2.48.1";

/**
 * send-notification-push
 *
 * Delivers a data-only FCM message for a row in public.notifications, so an event
 * that happens while the app is dead still reaches the device.
 *
 * WHY THIS EXISTS
 *   A story reaction is written to public.notifications and the app reads that table
 *   when the Notifications screen opens. Before this, nothing woke a closed app, so a
 *   reaction was silent until the owner happened to look.
 *
 * INVOCATION
 *   Server-side only, from the trigger tg_push_notification() (migration
 *   2026-11-03_notification_push.sql) through pg_net. The function is deployed with
 *   --no-verify-jwt because the trigger carries no user session; it is safe to expose
 *   because it can only act on a notification row that already exists and has not been
 *   dispatched yet — a repeated or guessed call delivers the same row once.
 *
 *   body: { type: "INSERT", table: "notifications", record: { ...row } }
 *      or { notification_id: "<uuid>" }
 */

interface NotificationRecord {
  id?: string;
  user_id?: string;
  actor_id?: string;
  type?: string;
  body?: string | null;
  post_id?: string | null;
  push_dispatched_at?: string | null;
}

interface WebhookPayload {
  type?: string;
  table?: string;
  record?: NotificationRecord;
  notification_id?: string;
}

/** Types that are worth waking a device for. Anything else is ignored. */
const PUSHED_TYPES = new Set(["story_reaction", "follow", "follow_request"]);

function serviceKey(): string | undefined {
  const raw = Deno.env.get("SUPABASE_SECRET_KEYS");
  if (raw) {
    try {
      const parsed = JSON.parse(raw);
      if (typeof parsed === "string") return parsed;
      if (parsed && typeof parsed === "object") {
        const obj = parsed as Record<string, unknown>;
        const first = obj.default ?? Object.values(obj)[0];
        if (typeof first === "string") return first;
      }
    } catch {
      // not JSON — fall through to the legacy variable
    }
  }
  return Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
}

async function getGoogleAccessToken(
  clientEmail: string,
  privateKeyPem: string
): Promise<string> {
  const cleanPem = privateKeyPem
    .replace(/\\n/g, "\n")
    .replace("-----BEGIN PRIVATE KEY-----", "")
    .replace("-----END PRIVATE KEY-----", "")
    .replace(/\s+/g, "");

  const binaryDer = Uint8Array.from(atob(cleanPem), (c) => c.charCodeAt(0));

  const key = await crypto.subtle.importKey(
    "pkcs8",
    binaryDer,
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["sign"]
  );

  const now = Math.floor(Date.now() / 1000);
  const header = { alg: "RS256", typ: "JWT" };
  const claimSet = {
    iss: clientEmail,
    scope: "https://www.googleapis.com/auth/firebase.messaging",
    aud: "https://oauth2.googleapis.com/token",
    exp: now + 3600,
    iat: now,
  };

  const base64UrlEncode = (obj: unknown) =>
    btoa(JSON.stringify(obj)).replace(/=/g, "").replace(/\+/g, "-").replace(/\//g, "_");

  const unsignedToken = `${base64UrlEncode(header)}.${base64UrlEncode(claimSet)}`;
  const signature = await crypto.subtle.sign(
    "RSASSA-PKCS1-v1_5",
    key,
    new TextEncoder().encode(unsignedToken)
  );

  const base64Signature = btoa(String.fromCharCode(...new Uint8Array(signature)))
    .replace(/=/g, "")
    .replace(/\+/g, "-")
    .replace(/\//g, "_");

  const jwt = `${unsignedToken}.${base64Signature}`;

  const res = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion: jwt,
    }),
  });

  if (!res.ok) {
    const errText = await res.text();
    throw new Error(`Google OAuth error: ${res.status} ${errText}`);
  }

  const tokenData = await res.json();
  return tokenData.access_token;
}

const json = (payload: unknown, status = 200) =>
  new Response(JSON.stringify(payload), {
    status,
    headers: { "Content-Type": "application/json" },
  });

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") return new Response(null, { status: 204 });
  if (req.method !== "POST") return json({ error: "POST only" }, 405);

  try {
    let payload: WebhookPayload = {};
    try {
      payload = await req.json();
    } catch {
      return json({ error: "Invalid JSON body" }, 400);
    }

    const record: NotificationRecord = payload.record || {};
    const notificationId = record.id || payload.notification_id;
    if (!notificationId) {
      return json({ error: "Missing notification id" }, 400);
    }

    const supabaseUrl = Deno.env.get("SUPABASE_URL");
    const supabaseServiceKey = serviceKey();
    if (!supabaseUrl || !supabaseServiceKey) {
      return json({ error: "Missing Supabase service configuration" }, 500);
    }
    const supabase = createClient(supabaseUrl, supabaseServiceKey);

    // Claim before any network work: a webhook retry or a duplicate trigger must not
    // produce a second buzz for the same row.
    const { data: claimed, error: claimError } = await supabase
      .from("notifications")
      .update({ push_dispatched_at: new Date().toISOString() })
      .eq("id", notificationId)
      .is("push_dispatched_at", null)
      .select("id, user_id, actor_id, type, body");

    if (claimError) {
      return json({ error: "Failed to claim notification", details: claimError }, 500);
    }
    if (!claimed || claimed.length === 0) {
      return json({ status: "already_dispatched", notification_id: notificationId, dispatched: 0 });
    }

    const row = claimed[0];
    if (!PUSHED_TYPES.has(String(row.type))) {
      return json({ status: "type_not_pushed", type: row.type, dispatched: 0 });
    }

    const { data: recipient } = await supabase
      .from("profiles")
      .select("fcm_token")
      .eq("id", row.user_id)
      .single();

    const fcmToken = recipient?.fcm_token;
    if (!fcmToken) {
      return json({ status: "no_token", notification_id: notificationId, dispatched: 0 });
    }

    let actorName = "Someone";
    let actorAvatar = "";
    if (row.actor_id) {
      const { data: actor } = await supabase
        .from("profiles")
        .select("username, avatar_url")
        .eq("id", row.actor_id)
        .single();
      if (actor) {
        actorName = actor.username || actorName;
        actorAvatar = actor.avatar_url || "";
      }
    }

    let firebaseProjectId = Deno.env.get("FIREBASE_PROJECT_ID") || "instanova-62ff0";
    let firebaseClientEmail = Deno.env.get("FIREBASE_CLIENT_EMAIL");
    let firebasePrivateKey = Deno.env.get("FIREBASE_PRIVATE_KEY");

    const rawServiceAccount = Deno.env.get("FIREBASE_SERVICE_ACCOUNT");
    if (rawServiceAccount) {
      try {
        const parsed = JSON.parse(rawServiceAccount);
        firebaseProjectId = parsed.project_id || firebaseProjectId;
        firebaseClientEmail = parsed.client_email || firebaseClientEmail;
        firebasePrivateKey = parsed.private_key || firebasePrivateKey;
      } catch (e) {
        console.warn("Failed to parse FIREBASE_SERVICE_ACCOUNT JSON:", e);
      }
    }

    if (!firebaseClientEmail || !firebasePrivateKey) {
      const { data: rpcData } = await supabase.rpc("get_firebase_credentials");
      if (rpcData && rpcData.length > 0) {
        firebaseProjectId = rpcData[0].project_id || firebaseProjectId;
        firebaseClientEmail = rpcData[0].client_email || firebaseClientEmail;
        firebasePrivateKey = rpcData[0].private_key || firebasePrivateKey;
      }
    }

    if (!firebaseClientEmail || !firebasePrivateKey) {
      return json({ status: "pending_credentials", dispatched: 0 });
    }

    const googleAccessToken = await getGoogleAccessToken(
      firebaseClientEmail,
      firebasePrivateKey
    );

    // Data-only, as with calls: the client decides how to present it, and a
    // notification block would bypass the user's per-type switches.
    const fcmMessage = {
      message: {
        token: fcmToken,
        data: {
          type: String(row.type),
          notification_id: String(notificationId),
          actor_id: String(row.actor_id || ""),
          actor_name: String(actorName),
          actor_avatar: String(actorAvatar),
          body: String(row.body || ""),
        },
        android: {
          priority: "high",
          ttl: "86400s",
          collapse_key: String(notificationId),
          restricted_package_name: "com.insta.nova",
        },
      },
    };

    const fcmRes = await fetch(
      `https://fcm.googleapis.com/v1/projects/${firebaseProjectId}/messages:send`,
      {
        method: "POST",
        headers: {
          Authorization: `Bearer ${googleAccessToken}`,
          "Content-Type": "application/json",
        },
        body: JSON.stringify(fcmMessage),
      }
    );

    const fcmData = await fcmRes.json();
    if (!fcmRes.ok) {
      console.error("FCM rejected the notification push:", fcmData);
    }

    return json({
      success: fcmRes.ok,
      notification_id: notificationId,
      type: row.type,
      dispatched: fcmRes.ok ? 1 : 0,
      fcm_status: fcmRes.status,
      result: fcmData,
    });
  } catch (err: any) {
    console.error("Unhandled error in send-notification-push:", err);
    return json({ error: err.message || "Internal server error" }, 500);
  }
});
