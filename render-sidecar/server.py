"""Tiny HTTP shim in front of gpcl6 (PCL -> PDF).

POST /render with raw PCL bytes -> 200 with PDF bytes, or 422 with gpcl6's stderr.
GET  /healthz -> 200 "ok".

Stdlib only, on purpose: this file is the whole service. gpcl6 itself is AGPL and
runs strictly as a child process of this sidecar container (docs/decisions.md D-018).
"""

import os
import subprocess
import tempfile
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

MAX_BYTES = int(os.environ.get("RENDER_MAX_BYTES", 200 * 1024 * 1024))
TIMEOUT_SECONDS = int(os.environ.get("RENDER_TIMEOUT_SECONDS", 60))
GPCL6 = os.environ.get("GPCL6_PATH", "/usr/local/bin/gpcl6")


class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def do_GET(self):
        if self.path == "/healthz":
            self._reply(200, b"ok", "text/plain")
        else:
            self._reply(404, b"not found", "text/plain")

    def do_POST(self):
        if self.path != "/render":
            self._reply(404, b"not found", "text/plain")
            return
        length = int(self.headers.get("Content-Length", 0))
        if length <= 0:
            self._reply(400, b"empty body", "text/plain")
            return
        if length > MAX_BYTES:
            self._reply(413, b"payload too large", "text/plain")
            return
        pcl = self.rfile.read(length)

        with tempfile.TemporaryDirectory() as tmp:
            src = os.path.join(tmp, "in.pcl")
            dst = os.path.join(tmp, "out.pdf")
            with open(src, "wb") as f:
                f.write(pcl)
            cmd = [GPCL6, "-dNOPAUSE", "-dBATCH", "-dSAFER",
                   "-sDEVICE=pdfwrite", "-o", dst, src]
            try:
                proc = subprocess.run(cmd, capture_output=True, timeout=TIMEOUT_SECONDS)
            except subprocess.TimeoutExpired:
                self._reply(422, b"gpcl6 timed out", "text/plain")
                return
            if proc.returncode != 0 or not os.path.exists(dst):
                detail = (proc.stderr or proc.stdout or b"gpcl6 failed")[:4096]
                self._reply(422, detail, "text/plain")
                return
            with open(dst, "rb") as f:
                self._reply(200, f.read(), "application/pdf")

    def _reply(self, status, body, content_type):
        self.send_response(status)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, fmt, *args):  # one-line access log to stdout
        print(f"{self.address_string()} {fmt % args}", flush=True)


if __name__ == "__main__":
    port = int(os.environ.get("PORT", 8080))
    print(f"render-sidecar listening on :{port} (gpcl6={GPCL6})", flush=True)
    ThreadingHTTPServer(("0.0.0.0", port), Handler).serve_forever()
