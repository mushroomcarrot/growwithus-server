import json
import tempfile
import unittest
from pathlib import Path
from unittest.mock import Mock, patch
from smoke_local_demo import Client, NoRedirect, checked_run, local_url, run, settings

ROOT = Path(__file__).resolve().parents[1]


class LocalDemoSetupTest(unittest.TestCase):
    def test_deployment_workers_have_no_demo_database_and_bind_only_loopback(self):
        config = json.loads((ROOT / "compose.ai-workers.json").read_text(encoding="utf-8"))
        services = config["services"]
        self.assertEqual(set(services), {"ollama", "ollama-init", "whisper-init", "stt", "tts"})
        self.assertFalse(any(service.get("ports") for service in services.values()))
        self.assertEqual(services["ollama"]["network_mode"], "host")
        self.assertEqual(services["stt"]["network_mode"], "host")
        self.assertEqual(services["tts"]["network_mode"], "host")
        self.assertEqual(services["tts"]["command"], ["python", "tts_server.py"])
        self.assertEqual(services["ollama"]["environment"]["OLLAMA_HOST"], "127.0.0.1:11434")
        self.assertEqual(services["ollama"]["environment"]["OLLAMA_NO_CLOUD"], "1")
        self.assertEqual(services["stt"]["environment"]["HF_HUB_OFFLINE"], "1")
        self.assertNotIn("demo-db", config["volumes"])
    def test_smoke_report_records_real_result_without_call_credentials(self):
        directory = ROOT / "build" / "setup-tests"
        directory.mkdir(parents=True, exist_ok=True)
        with tempfile.TemporaryDirectory(dir=directory) as temp:
            report = Path(temp) / "report.json"
            result = {"voice": "not_tested", "completion": "not_tested"}
            with patch("smoke_local_demo.run", return_value=result):
                self.assertEqual(checked_run(Mock(), "private-password", report), result)
            saved = json.loads(report.read_text(encoding="utf-8"))
            self.assertEqual(saved["status"], "passed")
            self.assertEqual(saved["checks"], result)
            self.assertNotIn("private-password", report.read_text())
            self.assertEqual(list(Path(temp).glob("*.tmp")), [])

    def test_failed_check_replaces_previous_success_without_exception_details(self):
        directory = ROOT / "build" / "setup-tests"
        directory.mkdir(parents=True, exist_ok=True)
        with tempfile.TemporaryDirectory(dir=directory) as temp:
            report = Path(temp) / "report.json"
            report.write_text('{"status":"passed"}')
            with patch("smoke_local_demo.run", side_effect=RuntimeError("private-voice-text")):
                with self.assertRaises(RuntimeError): checked_run(Mock(), "private-password", report)
            saved = json.loads(report.read_text())
            self.assertEqual(saved["status"], "failed")
            self.assertEqual(saved["errorType"], "RuntimeError")
            self.assertNotIn("private-voice-text", report.read_text())

    def test_loopback_url_boundary(self):
        self.assertEqual(local_url("http://localhost:8080/"), "http://localhost:8080")
        self.assertEqual(local_url("http://[::1]:8080"), "http://[::1]:8080")
        for url in ("https://localhost", "http://example.com", "http://user@localhost", "http://@localhost",
                    "http://localhost/path", "http://localhost?token=x", "http://localhost:0", "http://localhost:99999"):
            with self.subTest(url=url), self.assertRaises(ValueError):
                local_url(url)

    def test_redirect_never_forwards_credentials(self):
        self.assertIsNone(NoRedirect().redirect_request(None, None, 302, "Found", {}, "https://example.com"))

    def test_complete_requires_explicit_audio_before_login(self):
        client = Mock()
        with self.assertRaises(ValueError):
            run(client, "not-a-real-secret", complete=True)
        client.login.assert_not_called()

    def test_private_settings_parse_embedded_equals(self):
        directory = ROOT / "build" / "setup-tests"
        directory.mkdir(parents=True, exist_ok=True)
        with tempfile.TemporaryDirectory(dir=directory) as temp:
            path = Path(temp) / "settings"
            path.write_text("# comment\nLOCAL_DEMO_PASSWORD=test=value\n", encoding="utf-8")
            self.assertEqual(settings(path)["LOCAL_DEMO_PASSWORD"], "test=value")
            path.write_text("LOCAL_DEMO_PASSWORD=\n", encoding="utf-8")
            with self.assertRaises(ValueError): settings(path)

    def test_only_local_backend_port_is_published(self):
        config = json.loads((ROOT / "compose.local-demo.json").read_text())
        services = config["services"]
        published = [port for service in services.values() for port in service.get("ports", [])]
        self.assertEqual(published, ["127.0.0.1:8080:8080"])
        self.assertEqual(services["backend"]["network_mode"], "service:ollama")
        self.assertEqual(services["stt"]["network_mode"], "service:ollama")
        self.assertEqual(services["backend"]["environment"]["AI_LOCAL_STT_BASE_URL"], "http://127.0.0.1:8765")

    def test_models_are_installed_before_backend_and_credentials_are_required(self):
        config = json.loads((ROOT / "compose.local-demo.json").read_text())
        services = config["services"]
        self.assertEqual(services["backend"]["depends_on"]["ollama-init"]["condition"], "service_completed_successfully")
        self.assertEqual(services["stt"]["depends_on"]["whisper-init"]["condition"], "service_completed_successfully")
        self.assertIn(":?", services["backend"]["environment"]["JWT_SECRET_KEY"])
        self.assertIn(":?", services["db"]["environment"]["MARIADB_PASSWORD"])
        self.assertEqual(services["ollama"]["environment"]["OLLAMA_NO_CLOUD"], "1")

    def test_no_audio_is_reported_as_not_tested(self):
        client = Mock()
        client.login.side_effect = ["care-token", "owner-token"]
        def request(path, *args, **kwargs):
            if kwargs.get("expected") == 403: return {}
            if path == "/local-demo/status": return {"localDemo": True}
            if path == "/users/me": return {"role": "CARETAKER", "caretaker": {"name": "fixture"}}
            if path == "/caretaker/tasks/today": return {"gardens": [{"gardenId": "gdn_1", "tasks": []}]}
            if path.endswith("/growth"): return {"stage": "FLOWER"}
            return {}
        client.request.side_effect = request
        result = run(client, "not-a-real-secret")
        self.assertEqual(result["voice"], "not_tested")
        self.assertEqual(result["completion"], "not_tested")
        client.upload.assert_not_called()

    def test_reference_photo_requires_real_vision_and_preserves_negative_finding(self):
        with tempfile.TemporaryDirectory(dir=ROOT / 'build') as temporary:
            audio, photo = Path(temporary) / 'reference.wav', Path(temporary) / 'tomato.jpg'
            audio.write_bytes(b'RIFFfixture'); photo.write_bytes(b'public-photo')
            for vision in (None, {'matched': False, 'message': 'Cannot prove watering from this photo'}):
                client = Mock(); client.login.side_effect = ['care-token', 'owner-token']
                def request(path, *args, **kwargs):
                    if kwargs.get('expected') == 403: return {}
                    if path == '/local-demo/status': return {'localDemo': True}
                    if path == '/users/me': return {'role': 'CARETAKER', 'caretaker': {'name': 'fixture'}}
                    if path == '/caretaker/tasks/today': return {'gardens': [{'gardenId': 'gdn_1', 'tasks': [{'taskId': 'tsk_1', 'type': 'CHECK', 'status': 'TODO'}]}]}
                    if path.endswith('/growth'): return {'stage': 'FLOWER'}
                    if path.endswith('/complete'): return {'careLogId': 'clg_1'}
                    if path.endswith('/diary'): return {'items': [{'careLogId': 'clg_1', 'transcript': 'source', 'summary': 'summary'}]}
                    return {}
                client.request.side_effect = request
                client.upload.side_effect = [{'voiceMemoId': 'vm_1', 'transcript': 'source', 'summary': 'summary', 'processingStatus': 'READY'},
                                             {'photoId': 'pht_1', 'aiCheck': vision}]
                if vision is None:
                    with self.assertRaises(RuntimeError): run(client, 'fixture', audio, True, photo)
                else:
                    result = run(client, 'fixture', audio, True, photo)
                    self.assertEqual('passed', result['vision']); self.assertEqual('passed', result['completion'])


if __name__ == "__main__":
    unittest.main()
