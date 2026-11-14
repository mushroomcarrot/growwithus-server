from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
from configure_elevenlabs import configure


class ElevenLabsEnvironmentTest(unittest.TestCase):
    key = "sk_" + "a" * 32
    voice = "uyVNoMrnUku1dZyVEXwD"
    model = "eleven_flash_v2_5"

    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.target = Path(self.directory.name) / ".env"
        self.original = "# Server settings\nDB_PASSWORD=keep-db\nJWT_SECRET_KEY=keep-jwt\nGEMINI_API_KEY=keep-gemini"
        self.target.write_text(self.original, encoding="utf-8")

    def apply(self):
        return configure(self.target, self.key, self.voice, self.model)

    def test_preserves_database_jwt_and_gemini_settings(self):
        self.assertTrue(self.apply())
        result = self.target.read_text(encoding="utf-8")
        self.assertTrue(result.startswith(self.original + "\n"))
        self.assertIn("AI_ELEVENLABS_ENABLED=true\n", result)
        self.assertIn("ELEVENLABS_API_KEY=" + self.key + "\n", result)

    def test_missing_secret_preserves_every_byte(self):
        self.assertFalse(configure(self.target, "", "", ""))
        self.assertEqual(self.original, self.target.read_text(encoding="utf-8"))

    def test_replaces_duplicates_and_is_idempotent(self):
        self.target.write_text(self.original + "\nELEVENLABS_API_KEY=old\nELEVENLABS_API_KEY=duplicate\n", encoding="utf-8")
        self.apply()
        first = self.target.read_bytes()
        self.apply()
        self.assertEqual(first, self.target.read_bytes())
        self.assertEqual(1, first.count(b"ELEVENLABS_API_KEY="))

    def test_rejects_invalid_settings_without_mutation(self):
        for key, voice, model in [("sk_bad\nDB_PASSWORD=bad", self.voice, self.model),
                                  (self.key, "../bad", self.model), (self.key, self.voice, "bad\nmodel")]:
            with self.subTest(voice=voice, model=model):
                with self.assertRaises(ValueError):
                    configure(self.target, key, voice, model)
                self.assertEqual(self.original, self.target.read_text(encoding="utf-8"))

    def test_atomic_failure_keeps_original_and_removes_temporary_secret(self):
        with patch("configure_elevenlabs.os.replace", side_effect=OSError("simulated")):
            with self.assertRaises(OSError):
                self.apply()
        self.assertEqual(self.original, self.target.read_text(encoding="utf-8"))
        self.assertEqual([self.target], list(Path(self.directory.name).iterdir()))


if __name__ == "__main__":
    unittest.main()
