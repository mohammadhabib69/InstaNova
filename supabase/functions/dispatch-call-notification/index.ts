import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "npm:@supabase/supabase-js@2.48.1";

interface CallPayload {
  call_id?: string;
  caller_id?: string;
  recipient_id?: string;
  call_type?: string;
  conversation_id?: string;
  status?: string;
}

interface WebhookRecord {
  id?: string;
  caller_id?: string;
  recipient_id?: string;
  call_type?: string;
  conversation_id?: string;
  status?: string;
}

interface WebhookPayload {
  type?: string;
  table?: string;
  record?: WebhookRecord;
}

// Convert base64 / PEM RSA private key into Web Crypto CryptoKey and generate an OAuth2 Bearer token
async function getGoogleAccessToken(clientEmail: string, privateKeyPem: string): Promise<string> {
  const cleanPem = privateKeyPem
    .replace(/\\n/g, "\n")
    .replace(/-----BEGIN PRIVATE KEY-----/, "")
    .replace(/-----END PRIVATE KEY-----/, "")
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

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") {
    return new Response(null, {
      status: 204,
      headers: {
        "Access-Control-Allow-Origin": "*",
        "Access-Control-Allow-Methods": "POST, OPTIONS",
        "Access-Control-Allow-Headers": "Authorization, Content-Type, apikey, x-client-info",
      },
    });
  }

  try {
    let body: any = {};
    try {
      body = await req.json();
    } catch {
      return new Response(JSON.stringify({ error: "Invalid JSON body" }), {
        status: 400,
        headers: { "Content-Type": "application/json" },
      });
    }

    // Support both direct client payloads and Supabase Database Webhook payloads
    const record: WebhookRecord = body.record || body;
    const callId = record.id || body.call_id;
    const callerId = record.caller_id || body.caller_id;
    const recipientId = record.recipient_id || body.recipient_id;
    const callType = record.call_type || body.call_type || "audio";
    const conversationId = record.conversation_id || body.conversation_id || "";
    const status = record.status || body.status || "ringing";

    if (!callId || !recipientId) {
      return new Response(
        JSON.stringify({ error: "Missing required call_id or recipient_id" }),
        { status: 400, headers: { "Content-Type": "application/json" } }
      );
    }

    // Only dispatch for active incoming calls
    if (status !== "ringing") {
      return new Response(
        JSON.stringify({ message: `Ignoring status '${status}' - only 'ringing' triggers notification` }),
        { status: 200, headers: { "Content-Type": "application/json" } }
      );
    }

    const supabaseUrl = Deno.env.get("SUPABASE_URL");
    const supabaseServiceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");

    if (!supabaseUrl || !supabaseServiceKey) {
      return new Response(
        JSON.stringify({ error: "Missing Supabase service environment configuration" }),
        { status: 500, headers: { "Content-Type": "application/json" } }
      );
    }

    const supabase = createClient(supabaseUrl, supabaseServiceKey);

    // 1. Fetch recipient's registered device tokens
    const { data: devices, error: devicesError } = await supabase
      .from("user_devices")
      .select("fcm_token, device_model, platform")
      .eq("user_id", recipientId);

    if (devicesError) {
      return new Response(
        JSON.stringify({ error: "Failed to fetch recipient devices", details: devicesError }),
        { status: 500, headers: { "Content-Type": "application/json" } }
      );
    }

    if (!devices || devices.length === 0) {
      return new Response(
        JSON.stringify({ message: "No registered device tokens found for recipient", delivered: 0 }),
        { status: 200, headers: { "Content-Type": "application/json" } }
      );
    }

    // 2. Fetch caller profile (username, avatar) for rich display
    let callerUsername = "Instagram User";
    let callerAvatar = "";
    if (callerId) {
      const { data: profile } = await supabase
        .from("profiles")
        .select("username, avatar_url")
        .eq("id", callerId)
        .single();

      if (profile) {
        callerUsername = profile.username || callerUsername;
        callerAvatar = profile.avatar_url || "";
      }
    }

    // 3. Resolve Firebase credentials
    let firebaseProjectId = Deno.env.get("FIREBASE_PROJECT_ID") || "instanova-62ff0";
    let firebaseClientEmail = Deno.env.get("FIREBASE_CLIENT_EMAIL");
    let firebasePrivateKey = Deno.env.get("FIREBASE_PRIVATE_KEY");

    // Check JSON secret
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

    // Fallback: check private.firebase_credentials via secure RPC
    if (!firebaseClientEmail || !firebasePrivateKey) {
      const { data: rpcData, error: rpcError } = await supabase.rpc("get_firebase_credentials");

      if (rpcError) {
        console.warn("Could not load credentials via get_firebase_credentials RPC:", rpcError.message);
      } else if (rpcData && rpcData.length > 0) {
        const creds = rpcData[0];
        firebaseProjectId = creds.project_id || firebaseProjectId;
        firebaseClientEmail = creds.client_email || firebaseClientEmail;
        firebasePrivateKey = creds.private_key || firebasePrivateKey;
      }
    }

    if (!firebaseClientEmail || !firebasePrivateKey) {
      return new Response(
        JSON.stringify({
          status: "pending_credentials",
          message: "Firebase service credentials not yet configured in Supabase secrets or private.firebase_credentials table.",
          target_devices: devices.length,
          recipient_id: recipientId,
          call_id: callId
        }),
        { status: 200, headers: { "Content-Type": "application/json" } }
      );
    }

    // 4. Generate Google OAuth2 Access Token for FCM v1
    const googleAccessToken = await getGoogleAccessToken(firebaseClientEmail, firebasePrivateKey);

    // 5. Send FCM v1 High-Priority Data Message to each recipient device
    const results = [];
    for (const device of devices) {
      const fcmMessage = {
        message: {
          token: device.fcm_token,
          data: {
            type: "incoming_call",
            call_id: String(callId),
            call_type: String(callType),
            caller_id: String(callerId || ""),
            caller_username: String(callerUsername),
            caller_avatar: String(callerAvatar),
            conversation_id: String(conversationId || ""),
          },
          android: {
            priority: "high",
            ttl: "30s",
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
      results.push({
        token: device.fcm_token.substring(0, 10) + "...",
        status: fcmRes.status,
        result: fcmData,
      });
    }

    return new Response(
      JSON.stringify({
        success: true,
        call_id: callId,
        recipient_id: recipientId,
        dispatched: results.length,
        results,
      }),
      {
        status: 200,
        headers: { "Content-Type": "application/json" },
      }
    );
  } catch (err: any) {
    console.error("Unhandled error in dispatch-call-notification:", err);
    return new Response(
      JSON.stringify({ error: err.message || "Internal server error" }),
      { status: 500, headers: { "Content-Type": "application/json" } }
    );
  }
});
