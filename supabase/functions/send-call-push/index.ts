import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "npm:@supabase/supabase-js@2.48.1";

/**
 * send-call-push — Prompt J
 *
 * Sends a high-priority, data-only FCM message to the callee when a row
 * appears in public.calls. This is the only path that reaches a device whose
 * process is dead or backgrounded, because the Android client's Realtime
 * subscription dies with the process.
 *
 * Invocation: either a Database Webhook bound to INSERT on public.calls
 * (recommended — no client call needed), or a direct authenticated POST from
 * the caller device. Both shapes are accepted.
 *
 *   direct : { call_id, caller_id, callee_id, call_type }
 *   webhook: { type: "INSERT", table: "calls", record: { ...calls row } }
 *
 * The callee's FCM token lives on profiles.fcm_token (written by the Android
 * client at login and on FirebaseMessaging.onNewToken).
 */

interface CallsRecord {
  id?: string;
  caller_id?: string;
  callee_id?: string;
  call_type?: string;
  status?: string;
  push_dispatched_at?: string | null;
}

interface WebhookPayload {
  type?: string;
  table?: string;
  record?: CallsRecord;
}

// Convert base64 / PEM RSA private key into a Web Crypto CryptoKey and
// mint an OAuth2 bearer token for FCM v1.
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
    btoa(JSON.stringify(obj))
      .replace(/=/g, "")
      .replace(/\+/g, "-")
      .replace(/\//g, "_");

  const unsignedToken = `${base64UrlEncode(header)}.${base64UrlEncode(
    claimSet
  )}`;
  const signature = await crypto.subtle.sign(
    "RSASSA-PKCS1-v1_5",
    key,
    new TextEncoder().encode(unsignedToken)
  );

  const base64Signature = btoa(
    String.fromCharCode(...new Uint8Array(signature))
  )
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

const CORS = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
  "Access-Control-Allow-Headers":
    "Authorization, Content-Type, apikey, x-client-info",
};

const json = (payload: unknown, status = 200) =>
  new Response(JSON.stringify(payload), {
    status,
    headers: { "Content-Type": "application/json", ...CORS },
  });

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") {
    return new Response(null, { status: 204, headers: CORS });
  }

  try {
    let body: any = {};
    try {
      body = await req.json();
    } catch {
      return json({ error: "Invalid JSON body" }, 400);
    }

    const record: CallsRecord = body.record || body;

    const callId = record.id || body.call_id;
    const callerId = record.caller_id || body.caller_id;
    const calleeId = record.callee_id || body.callee_id;
    const callType = record.call_type || body.call_type || "audio";
    const status = record.status || body.status || "calling";

    if (!callId || !calleeId) {
      return json({ error: "Missing required call_id or callee_id" }, 400);
    }

    // Only a fresh incoming call warrants waking the device. Terminal and
    // mid-call states must not raise a ringing notification.
    if (status !== "calling") {
      return json({
        message: `Ignoring status '${status}' — only 'calling' raises a push`,
        dispatched: 0,
      });
    }

    const supabaseUrl = Deno.env.get("SUPABASE_URL");
    const supabaseServiceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");

    if (!supabaseUrl || !supabaseServiceKey) {
      return json(
        { error: "Missing Supabase service environment configuration" },
        500
      );
    }

    const supabase = createClient(supabaseUrl, supabaseServiceKey);

    // Claim this call id before doing any network work. A direct client retry,
    // a duplicate lifecycle callback, or a second process must not send a second
    // FCM notification for the same call.
    const dispatchClaimAt = new Date().toISOString();
    const { data: claimedRows, error: claimError } = await supabase
      .from("calls")
      .update({ push_dispatched_at: dispatchClaimAt })
      .eq("id", callId)
      .eq("status", "calling")
      .is("push_dispatched_at", null)
      .select("id");

    if (claimError) {
      return json({ error: "Failed to claim push dispatch", details: claimError }, 500);
    }
    if (!claimedRows || claimedRows.length === 0) {
      return json({ status: "already_dispatched", call_id: callId, dispatched: 0 });
    }

    // 1. Callee's push token
    const { data: callee, error: calleeError } = await supabase
      .from("profiles")
      .select("username, avatar_url, fcm_token")
      .eq("id", calleeId)
      .single();

    if (calleeError) {
      return json(
        { error: "Failed to fetch callee profile", details: calleeError },
        500
      );
    }

    const fcmToken = callee?.fcm_token;
    if (!fcmToken) {
      return json({
        status: "no_token",
        message:
          "Callee has no fcm_token on profiles — app was never opened or " +
          "token was never refreshed. Realtime delivery still applies while " +
          "the process is alive.",
        call_id: callId,
        dispatched: 0,
      });
    }

    // 2. Caller identity for the notification body
    let callerUsername = "Instagram User";
    let callerAvatar = "";
    if (callerId) {
      const { data: caller } = await supabase
        .from("profiles")
        .select("username, avatar_url")
        .eq("id", callerId)
        .single();

      if (caller) {
        callerUsername = caller.username || callerUsername;
        callerAvatar = caller.avatar_url || "";
      }
    }

    // 3. Firebase service credentials
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
      const { data: rpcData, error: rpcError } = await supabase.rpc(
        "get_firebase_credentials"
      );

      if (rpcError) {
        console.warn(
          "Could not load credentials via get_firebase_credentials RPC:",
          rpcError.message
        );
      } else if (rpcData && rpcData.length > 0) {
        const creds = rpcData[0];
        firebaseProjectId = creds.project_id || firebaseProjectId;
        firebaseClientEmail = creds.client_email || firebaseClientEmail;
        firebasePrivateKey = creds.private_key || firebasePrivateKey;
      }
    }

    if (!firebaseClientEmail || !firebasePrivateKey) {
      return json({
        status: "pending_credentials",
        message:
          "Firebase service credentials are not configured in Supabase " +
          "secrets or the private.firebase_credentials table.",
        call_id: callId,
        dispatched: 0,
      });
    }

    // 4. OAuth2 access token for FCM v1
    const googleAccessToken = await getGoogleAccessToken(
      firebaseClientEmail,
      firebasePrivateKey
    );

    // 5. High-priority data-only push.
    //
    //    android.notification is deliberately omitted: a data-only message is
    //    handled in FirebaseMessagingService, which lets the client decide
    //    whether to show a heads-up (foreground) or a full-screen intent
    //    (background). A notification block would bypass that decision.
    //
    //    collapse_key groups retries of the same call so a slow network
    //    cannot stack two ringing notifications for one call_id.
    const fcmMessage = {
      message: {
        token: fcmToken,
        data: {
          type: "incoming_call",
          call_id: String(callId),
          call_type: String(callType),
          caller_id: String(callerId || ""),
          caller_name: String(callerUsername),
          caller_username: String(callerUsername),
          caller_avatar: String(callerAvatar),
        },
        android: {
          priority: "high",
          ttl: "30000s",
          collapse_key: String(callId),
          // Keep the payload alive on an offline device long enough to
          // deliver the missed-call record rather than silently dropping it.
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
      console.error("FCM rejected the push:", fcmData);
    }

    return json({
      success: fcmRes.ok,
      call_id: callId,
      callee_id: calleeId,
      dispatched: fcmRes.ok ? 1 : 0,
      fcm_status: fcmRes.status,
      result: fcmData,
    });
  } catch (err: any) {
    console.error("Unhandled error in send-call-push:", err);
    return json({ error: err.message || "Internal server error" }, 500);
  }
});