import io
import json
import os
import tempfile
import threading
import unittest
from email.message import Message
from types import SimpleNamespace
from pathlib import Path
from unittest.mock import patch

from stt_server import (
    InvalidAudio, MAX_AUDIO_BYTES, NoSpeech, SttHandler, load_model, transcribe,
)


class Model:
    def __init__(self, duration=3.5, texts=("오늘 물을 줬어요.", "잎에 점이 있어요.")):
        self.duration = duration
        self.texts = texts
        self.calls = 0

    def transcribe(self, audio, **kwargs):
        self.calls += 1
        self.input = audio.read()
        self.options = kwargs
        segments = (SimpleNamespace(text=text) for text in self.texts)
        return segments, SimpleNamespace(duration=self.duration)


class Handler(SttHandler):
    def __init__(self, model, payload=b"audio", path="/transcribe", content_type="application/octet-stream"):
        self.path = path
        self.headers = Message()
        self.headers["Content-Type"] = content_type
        self.headers["Content-Length"] = str(len(payload))
        self.rfile = io.BytesIO(payload)
        self.wfile = io.BytesIO()
        self.server = SimpleNamespace(model=model, inference_lock=threading.Lock())
        self.output_headers = {}

    def send_response(self, status):
        self.status = status

    def send_header(self, name, value):
        self.output_headers[name] = value

    def end_headers(self):
        pass

    def result(self):
        return json.loads(self.wfile.getvalue())


class RecognitionTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temp_root = Path(__file__).resolve().parents[1] / "build" / "stt-test-temp"
        cls.temp_root.mkdir(parents=True, exist_ok=True)

    def test_korean_transcription_and_duration(self):
        model = Model()
        result = transcribe(model, b"audio")
        self.assertEqual(result, {"text": "오늘 물을 줬어요. 잎에 점이 있어요.", "durationSeconds": 3.5})
        self.assertEqual(model.input, b"audio")
        self.assertEqual(model.options["language"], "ko")
        self.assertTrue(model.options["vad_filter"])

    def test_empty_or_oversized_audio_rejected_before_inference(self):
        model = Model()
        for audio in (b"", b"x" * (MAX_AUDIO_BYTES + 1)):
            with self.subTest(size=len(audio)), self.assertRaises(InvalidAudio):
                transcribe(model, audio)
        self.assertEqual(model.calls, 0)

    def test_duration_limits(self):
        for duration in (0, -1, 60.01, float("nan"), float("inf")):
            with self.subTest(duration=duration), self.assertRaises(InvalidAudio):
                transcribe(Model(duration=duration), b"audio")
        self.assertEqual(transcribe(Model(duration=60), b"audio")["durationSeconds"], 60)

    def test_silence_is_not_invented_text(self):
        with self.assertRaises(NoSpeech):
            transcribe(Model(texts=("", "  ")), b"audio")

    def test_transcript_length_is_bounded(self):
        with self.assertRaises(InvalidAudio):
            transcribe(Model(texts=("x" * 8001,)), b"audio")

    def test_decoder_failure(self):
        model = Model()
        model.transcribe = lambda *a, **kw: (_ for _ in ()).throw(ValueError("invalid file"))
        with self.assertRaises(InvalidAudio):
            transcribe(model, b"audio")

    def test_missing_local_model_never_triggers_download(self):
        with patch.dict(os.environ, {"WHISPER_MODEL_PATH": ""}), self.assertRaises(SystemExit):
            load_model()

    def test_incomplete_model_is_rejected_before_import(self):
        with tempfile.TemporaryDirectory(dir=self.temp_root) as directory:
            with patch.dict(os.environ, {"WHISPER_MODEL_PATH": directory}), self.assertRaises(SystemExit):
                load_model()

    def test_local_model_configuration_is_offline_and_cpu_only(self):
        calls = []
        def construct(path, **options):
            calls.append((path, options))
            return "model"
        fake_module = SimpleNamespace(WhisperModel=construct)
        with tempfile.TemporaryDirectory(dir=self.temp_root) as directory:
            for name in ("model.bin", "config.json", "tokenizer.json"):
                (Path(directory) / name).write_bytes(b"fixture")
            with patch.dict(os.environ, {"WHISPER_MODEL_PATH": directory}):
                with patch.dict("sys.modules", {"faster_whisper": fake_module}):
                    self.assertEqual(load_model(), "model")
                    self.assertEqual(os.environ["HF_HUB_OFFLINE"], "1")
        self.assertEqual(calls[0][1], {"device": "cpu", "compute_type": "int8", "local_files_only": True})


class HttpBoundaryTest(unittest.TestCase):
    def test_corrupt_container_is_invalid_audio_not_a_retryable_server_failure(self):
        class DecoderInvalidData(Exception):
            pass
        model = Model()
        model.transcribe = lambda *args, **kwargs: (_ for _ in ()).throw(DecoderInvalidData("private decoder details"))
        handler = Handler(model)
        with patch("stt_server.INVALID_DECODE_ERRORS", (ValueError, EOFError, DecoderInvalidData)):
            handler.do_POST()
        self.assertEqual(handler.status, 400)
        self.assertEqual(handler.result(), {"error": "invalid_audio"})
        self.assertFalse(handler.server.inference_lock.locked())

    def test_internal_model_failure_remains_retryable_and_releases_worker(self):
        model = Model()
        model.transcribe = lambda *args, **kwargs: (_ for _ in ()).throw(RuntimeError("private model details"))
        handler = Handler(model)
        handler.do_POST()
        self.assertEqual(handler.status, 503)
        self.assertEqual(handler.result(), {"error": "inference_failed"})
        self.assertFalse(handler.server.inference_lock.locked())

    def test_success(self):
        handler = Handler(Model())
        handler.do_POST()
        self.assertEqual(handler.status, 200)
        self.assertEqual(handler.result()["durationSeconds"], 3.5)
        self.assertEqual(handler.output_headers["Cache-Control"], "no-store")
        self.assertEqual(handler.output_headers["Connection"], "close")
        self.assertNotIn("Access-Control-Allow-Origin", handler.output_headers)
        self.assertFalse(handler.server.inference_lock.locked())

    def test_unsupported_route(self):
        handler = Handler(Model(), path="/unknown")
        handler.do_POST()
        self.assertEqual(handler.status, 404)
        self.assertEqual(handler.server.model.calls, 0)

    def test_content_type(self):
        handler = Handler(Model(), content_type="text/plain")
        handler.do_POST()
        self.assertEqual(handler.status, 415)
        self.assertEqual(handler.server.model.calls, 0)

    def test_missing_invalid_and_duplicate_lengths(self):
        for lengths in ([], ["-1"], [" 5"], ["5", "5"], ["x"], ["9" * 100]):
            with self.subTest(lengths=lengths):
                handler = Handler(Model())
                del handler.headers["Content-Length"]
                for value in lengths:
                    handler.headers["Content-Length"] = value
                handler.do_POST()
                self.assertEqual(handler.status, 400)
                self.assertEqual(handler.server.model.calls, 0)

    def test_transfer_encoding_rejected(self):
        handler = Handler(Model())
        handler.headers["Transfer-Encoding"] = "chunked"
        handler.do_POST()
        self.assertEqual(handler.status, 400)

    def test_oversized_content_length(self):
        handler = Handler(Model())
        handler.headers.replace_header("Content-Length", str(MAX_AUDIO_BYTES + 1))
        handler.do_POST()
        self.assertEqual(handler.status, 413)
        self.assertEqual(handler.server.model.calls, 0)

    def test_truncated_upload(self):
        handler = Handler(Model())
        handler.headers.replace_header("Content-Length", "100")
        handler.do_POST()
        self.assertEqual(handler.status, 400)
        self.assertEqual(handler.server.model.calls, 0)
        self.assertFalse(handler.server.inference_lock.locked())

    def test_busy_model_rejects_without_inference(self):
        handler = Handler(Model())
        handler.server.inference_lock.acquire()
        handler.do_POST()
        self.assertEqual(handler.status, 503)
        self.assertEqual(handler.server.model.calls, 0)
        self.assertTrue(handler.server.inference_lock.locked())

    def test_long_recording_rejected_even_if_small_file(self):
        handler = Handler(Model(duration=61))
        handler.do_POST()
        self.assertEqual(handler.status, 400)
        self.assertFalse(handler.server.inference_lock.locked())

    def test_silence(self):
        handler = Handler(Model(texts=()))
        handler.do_POST()
        self.assertEqual(handler.status, 422)
        self.assertFalse(handler.server.inference_lock.locked())

    def test_runtime_error_is_private_and_unlocks_model(self):
        handler = Handler(Model())
        handler.server.model.transcribe = lambda *a, **kw: (_ for _ in ()).throw(RuntimeError("PRIVATE DETAIL"))
        handler.do_POST()
        self.assertEqual(handler.status, 503)
        self.assertNotIn("PRIVATE", handler.wfile.getvalue().decode())
        self.assertFalse(handler.server.inference_lock.locked())

    def test_health_does_not_expose_model_path(self):
        handler = Handler(Model(), path="/health")
        handler.do_GET()
        self.assertEqual(handler.result(), {"ready": True})
        self.assertEqual(handler.server.model.calls, 0)


if __name__ == "__main__":
    unittest.main()
