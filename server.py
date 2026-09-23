import json
import os
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

ROOT = Path(__file__).resolve().parent
INDEX = (ROOT / "index.html").read_bytes()

class Handler(BaseHTTPRequestHandler):
    def _send(self, status, body, content_type):
        self.send_response(status)
        self.send_header("Content-Type", content_type)
        self.send_header("Cache-Control", "no-store")
        self.send_header("X-Content-Type-Options", "nosniff")
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        path = self.path.split("?", 1)[0]
        if path in ("/", "/tazeris-ai"):
            self._send(200, INDEX, "text/html; charset=utf-8")
            return
        if path == "/health":
            body = json.dumps({"ok": True, "service": "TAZERIS-AI"}).encode()
            self._send(200, body, "application/json")
            return
        self._send(404, b"Not found", "text/plain; charset=utf-8")

    def log_message(self, fmt, *args):
        print(fmt % args, flush=True)

if __name__ == "__main__":
    port = int(os.environ.get("PORT", "8080"))
    ThreadingHTTPServer(("0.0.0.0", port), Handler).serve_forever()
