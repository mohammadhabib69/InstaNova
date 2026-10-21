#!/usr/bin/env python3
"""
Lightweight PostgREST-compatible Calling Signaling Relay Server.
Provides local peer-to-peer WebRTC signaling between ADB-connected physical devices
when Supabase PostgREST calling tables have not yet been migrated.
"""

import http.server
import json
import socketserver
import time
import urllib.parse
import uuid

PORT = 8088

sessions = {}  # call_id -> dict
signals = []   # list of signal dicts
devices = {}   # user_id -> fcm_token

class SignalingHandler(http.server.BaseHTTPRequestHandler):

    def _send_json(self, status_code, data):
        body = json.dumps(data).encode("utf-8")
        self.send_response(status_code)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Headers", "*")
        self.send_header("Access-Control-Allow-Methods", "GET, POST, PATCH, OPTIONS")
        self.end_headers()
        self.wfile.write(body)

    def do_OPTIONS(self):
        self.send_response(204)
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Headers", "*")
        self.send_header("Access-Control-Allow-Methods", "GET, POST, PATCH, OPTIONS")
        self.end_headers()

    def do_GET(self):
        parsed = urllib.parse.urlparse(self.path)
        path = parsed.path
        query = urllib.parse.parse_qs(parsed.query)

        if path == "/" or path == "/health":
            self._send_json(200, {"status": "ok", "time": time.time()})
            return

        # GET /rest/v1/call_sessions
        if "call_sessions" in path:
            matching = list(sessions.values())
            if "id" in query:
                val = query["id"][0].replace("eq.", "")
                matching = [s for s in matching if s.get("id") == val]
            if "recipient_id" in query:
                val = query["recipient_id"][0].replace("eq.", "")
                matching = [s for s in matching if s.get("recipient_id") == val]
            if "caller_id" in query:
                val = query["caller_id"][0].replace("eq.", "")
                matching = [s for s in matching if s.get("caller_id") == val]
            if "status" in query:
                val = query["status"][0].replace("eq.", "")
                matching = [s for s in matching if s.get("status") == val]

            # Sort desc by created_at
            matching.sort(key=lambda x: x.get("created_at", 0), reverse=True)
            if "limit" in query:
                try:
                    lim = int(query["limit"][0])
                    matching = matching[:lim]
                except ValueError:
                    pass
            self._send_json(200, matching)
            return

        # GET /rest/v1/call_signals
        if "call_signals" in path:
            matching = list(signals)
            if "call_id" in query:
                val = query["call_id"][0].replace("eq.", "")
                matching = [s for s in matching if s.get("call_id") == val]
            if "recipient_id" in query:
                val = query["recipient_id"][0].replace("eq.", "")
                matching = [s for s in matching if s.get("recipient_id") == val]
            matching.sort(key=lambda x: x.get("created_at", 0))
            self._send_json(200, matching)
            return

        self._send_json(404, {"error": "Not found"})

    def do_POST(self):
        parsed = urllib.parse.urlparse(self.path)
        path = parsed.path
        content_len = int(self.headers.get("Content-Length", 0))
        body = self.rfile.read(content_len).decode("utf-8") if content_len > 0 else "{}"
        try:
            payload = json.loads(body)
        except Exception:
            payload = {}

        # POST /rest/v1/call_sessions
        if "call_sessions" in path:
            call_id = payload.get("id") or str(uuid.uuid4())
            session = {
                "id": call_id,
                "conversation_id": payload.get("conversation_id"),
                "caller_id": payload.get("caller_id"),
                "recipient_id": payload.get("recipient_id"),
                "call_type": payload.get("call_type", "audio"),
                "status": payload.get("status", "ringing"),
                "created_at": time.time(),
                "duration_seconds": 0,
                "caller": {
                    "username": "qaverify" if payload.get("caller_id") == "3c96a7cc-5c8b-4a79-9138-8c6c9491aed7" else "signup_e2e_1026",
                    "avatar_url": ""
                }
            }
            sessions[call_id] = session
            print(f"[SignalingServer] Created call session: {call_id} ({session['call_type']}) from {session['caller_id']} to {session['recipient_id']}")
            self._send_json(201, [session])
            return

        # POST /rest/v1/call_signals
        if "call_signals" in path:
            sig_id = str(uuid.uuid4())
            signal = {
                "id": sig_id,
                "call_id": payload.get("call_id"),
                "sender_id": payload.get("sender_id"),
                "recipient_id": payload.get("recipient_id"),
                "signal_type": payload.get("signal_type"),
                "payload": payload.get("payload"),
                "created_at": time.time()
            }
            signals.append(signal)
            print(f"[SignalingServer] Received signal: {signal['signal_type']} for call {signal['call_id']} from {signal['sender_id']}")
            self._send_json(201, [signal])
            return

        # POST /rest/v1/user_devices
        if "user_devices" in path:
            user_id = payload.get("user_id")
            token = payload.get("fcm_token")
            if user_id and token:
                devices[user_id] = token
            self._send_json(200, {"success": True})
            return

        self._send_json(404, {"error": "Not found"})

    def do_PATCH(self):
        parsed = urllib.parse.urlparse(self.path)
        path = parsed.path
        query = urllib.parse.parse_qs(parsed.query)
        content_len = int(self.headers.get("Content-Length", 0))
        body = self.rfile.read(content_len).decode("utf-8") if content_len > 0 else "{}"
        try:
            payload = json.loads(body)
        except Exception:
            payload = {}

        if "call_sessions" in path:
            call_id = None
            if "id" in query:
                call_id = query["id"][0].replace("eq.", "")
            if call_id and call_id in sessions:
                sessions[call_id].update(payload)
                print(f"[SignalingServer] Updated session {call_id}: status={payload.get('status')}, duration={payload.get('duration_seconds')}")
                self._send_json(200, sessions[call_id])
                return

        self._send_json(204, {})

    def log_message(self, format, *args):
        # Suppress routine logging
        pass

class ReusableTCPServer(socketserver.TCPServer):
    allow_reuse_address = True

if __name__ == "__main__":
    with ReusableTCPServer(("0.0.0.0", PORT), SignalingHandler) as httpd:
        print(f"[SignalingServer] Listening on 0.0.0.0:{PORT}...")
        httpd.serve_forever()
