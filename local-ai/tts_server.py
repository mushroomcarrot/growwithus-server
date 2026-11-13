"""Offline Korean TTS using eSpeak NG and FFmpeg. No voice data leaves the host."""

import json
import math
import os
import subprocess
import threading
from http.server import ThreadingHTTPServer
from stt_server import SttHandler


def synthesize(text, speed=0.9):
    if (not isinstance(text, str) or not text.strip() or len(text) > 300 or "\0" in text
            or isinstance(speed, bool) or not isinstance(speed, (int, float))
            or not math.isfinite(speed) or not 0.5 <= speed <= 1.5):
        raise ValueError("Invalid TTS input")
    wave = subprocess.run(
        ["espeak-ng", "--stdin", "--stdout", "-v", "ko", "-s", str(round(175 * speed))],
        input=text.encode("utf-8"), stdout=subprocess.PIPE, stderr=subprocess.PIPE,
        check=True, timeout=15).stdout
    if not wave.startswith(b"RIFF") or len(wave) > 8 * 1024 * 1024:
        raise RuntimeError("Invalid wave output")
    audio = subprocess.run(
        ["ffmpeg", "-nostdin", "-hide_banner", "-loglevel", "error", "-i", "pipe:0",
         "-t", "120", "-f", "mp3", "-codec:a", "libmp3lame", "-b:a", "48k", "pipe:1"],
        input=wave, stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=True, timeout=15).stdout
    if not audio or len(audio) > 1024 * 1024:
        raise RuntimeError("Invalid MP3 output")
    return audio


class TtsHandler(SttHandler):
    def do_POST(self):
        if self.path != "/synthesize":
            self.reply(404, {"error": "not_found"})
            return
        lengths = self.headers.get_all("Content-Length", [])
        if (self.headers.get("Transfer-Encoding") is not None or len(lengths) != 1
                or not lengths[0].isascii() or not lengths[0].isdigit() or len(lengths[0]) > 5):
            self.reply(400, {"error": "invalid_content_length"})
            return
        size = int(lengths[0])
        if not 0 < size <= 8192:
            self.reply(413, {"error": "size_limit"})
            return
        if self.headers.get_content_type() != "application/json":
            self.reply(415, {"error": "unsupported_media_type"})
            return
        if not self.server.inference_lock.acquire(blocking=False):
            self.reply(503, {"error": "busy"})
            return
        try:
            body = self.rfile.read(size)
            if len(body) != size:
                raise ValueError("Truncated body")
            request = json.loads(body)
            if not isinstance(request, dict):
                raise ValueError("Invalid request")
            audio = synthesize(request.get("text"), request.get("speed", 0.9))
            self.send_response(200)
            self.send_header("Content-Type", "audio/mpeg")
            self.send_header("Content-Length", str(len(audio)))
            self.send_header("Cache-Control", "no-store")
            self.send_header("Connection", "close")
            self.end_headers()
            self.close_connection = True
            self.wfile.write(audio)
        except (ValueError, UnicodeError):
            self.reply(400, {"error": "invalid_input"})
        except Exception:
            self.reply(503, {"error": "synthesis_failed"})
        finally:
            self.server.inference_lock.release()


def main():
    # Fail startup rather than advertising a worker without a functioning Korean voice.
    synthesize("안녕하세요.")
    port = int(os.environ.get("TTS_PORT", "8766"))
    if not 1024 <= port <= 65535:
        raise SystemExit("TTS_PORT must be between 1024 and 65535.")
    server = ThreadingHTTPServer(("127.0.0.1", port), TtsHandler)
    server.inference_lock = threading.Lock()
    print(f"Local TTS ready on 127.0.0.1:{port}", flush=True)
    try:
        server.serve_forever()
    finally:
        server.server_close()


if __name__ == "__main__":
    main()
