"""Offline Korean speech recognition. Load an existing local faster-whisper model only."""

import io
import json
import math
import os
import socket
import threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

MAX_AUDIO_BYTES = 20 * 1024 * 1024
MAX_DURATION_SECONDS = 60
MAX_TEXT_LENGTH = 8000


class InvalidAudio(ValueError):
    pass


class NoSpeech(ValueError):
    pass


def transcribe(model, audio):
    if not audio or len(audio) > MAX_AUDIO_BYTES:
        raise InvalidAudio("Invalid audio size")
    try:
        segments, info = model.transcribe(io.BytesIO(audio), language="ko", beam_size=1, vad_filter=True)
    except (ValueError, EOFError) as exc:
        raise InvalidAudio("Cannot decode audio") from exc
    duration = info.duration
    if not math.isfinite(duration) or duration <= 0 or duration > MAX_DURATION_SECONDS:
        raise InvalidAudio("Audio must be at most 60 seconds")
    parts = []
    length = 0
    for segment in segments:
        text = segment.text.strip()
        if text:
            length += len(text) + (1 if parts else 0)
            if length > MAX_TEXT_LENGTH:
                raise InvalidAudio("Transcript exceeds maximum length")
            parts.append(text)
    if not parts:
        raise NoSpeech("No speech recognized")
    return {"text": " ".join(parts), "durationSeconds": duration}


class SttHandler(BaseHTTPRequestHandler):
    # No CORS headers: browsers cannot call this local worker directly.
    def setup(self):
        super().setup()
        self.connection.settimeout(30)

    def reply(self, status, body):
        data = json.dumps(body, ensure_ascii=False, allow_nan=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(data)))
        self.send_header("Cache-Control", "no-store")
        self.send_header("Connection", "close")
        self.end_headers()
        self.close_connection = True
        self.wfile.write(data)

    def do_GET(self):
        if self.path == "/health":
            self.reply(200, {"ready": True})
        else:
            self.reply(404, {"error": "not_found"})

    def do_POST(self):
        if self.path != "/transcribe":
            self.reply(404, {"error": "not_found"})
            return
        if self.headers.get("Transfer-Encoding") is not None:
            self.reply(400, {"error": "content_length_required"})
            return
        lengths = self.headers.get_all("Content-Length", [])
        if len(lengths) != 1 or not lengths[0].isascii() or not lengths[0].isdigit() or len(lengths[0]) > 10:
            self.reply(400, {"error": "invalid_content_length"})
            return
        size = int(lengths[0])
        if size <= 0 or size > MAX_AUDIO_BYTES:
            self.reply(413, {"error": "audio_size_limit"})
            return
        if self.headers.get_content_type() != "application/octet-stream":
            self.reply(415, {"error": "unsupported_media_type"})
            return
        if not self.server.inference_lock.acquire(blocking=False):
            self.reply(503, {"error": "busy"})
            return
        try:
            audio = self.rfile.read(size)
            if len(audio) != size:
                raise InvalidAudio("Truncated audio")
            result = transcribe(self.server.model, audio)
            self.reply(200, result)
        except (InvalidAudio, socket.timeout):
            self.reply(400, {"error": "invalid_audio"})
        except NoSpeech:
            self.reply(422, {"error": "no_speech"})
        except Exception:
            # Do not expose transcripts, audio, filenames, or internal exception details.
            self.reply(503, {"error": "inference_failed"})
        finally:
            self.server.inference_lock.release()

    def log_message(self, format, *args):
        pass


def load_model():
    model_path = os.environ.get("WHISPER_MODEL_PATH", "")
    if not model_path or not Path(model_path).is_dir():
        raise SystemExit("Set WHISPER_MODEL_PATH to an existing local faster-whisper model directory.")
    for name in ("model.bin", "config.json", "tokenizer.json", "preprocessor_config.json"):
        if not (Path(model_path) / name).is_file():
            raise SystemExit(f"Local model is incomplete: {name}")
    os.environ["HF_HUB_OFFLINE"] = "1"
    from faster_whisper import WhisperModel

    # Force offline loading. Starting this server never downloads model files.
    return WhisperModel(model_path, device="cpu", compute_type="int8", local_files_only=True)


def main():
    model = load_model()
    port = int(os.environ.get("WHISPER_PORT", "8765"))
    if not 1024 <= port <= 65535:
        raise SystemExit("WHISPER_PORT must be between 1024 and 65535.")
    server = ThreadingHTTPServer(("127.0.0.1", port), SttHandler)
    server.model = model
    server.inference_lock = threading.Lock()
    print(f"Local STT ready on 127.0.0.1:{port}", flush=True)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()


if __name__ == "__main__":
    main()
