import hashlib
import importlib.util
import io
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('prepare_ai_fixtures', ROOT / 'scripts/prepare-ai-fixtures.py')
fixtures = importlib.util.module_from_spec(spec)
spec.loader.exec_module(fixtures)


class FixturePreparationTest(unittest.TestCase):
    def prepare(self, payload, checksum=None, url=fixtures.PHOTO):
        with tempfile.TemporaryDirectory(dir=ROOT / 'build') as temporary:
            root = Path(temporary)
            source = root / 'src/test/resources/ai-evaluation'
            source.mkdir(parents=True)
            (source / 'synthetic-korean.wav').write_bytes(b'RIFFpublic-fixture')
            response = io.BytesIO(payload); response.url = url
            with patch.object(fixtures, '__file__', str(root / 'scripts/prepare-ai-fixtures.py')), \
                    patch.object(fixtures, 'PHOTO_SHA1', checksum or hashlib.sha1(payload).hexdigest()), \
                    patch.object(fixtures.urllib.request, 'urlopen', return_value=response), \
                    patch.object(fixtures.subprocess, 'run') as process:
                fixtures.prepare(root / 'output')
                self.assertEqual(3, process.call_count)
                for call in process.call_args_list:
                    self.assertNotIn('shell', call.kwargs)
                self.assertEqual(payload, (root / 'output/tomato.jpg').read_bytes())
                sources = json.loads((root / 'output/sources.json').read_text())
                self.assertEqual('Softeis', sources['photo']['author'])

    def test_downloads_only_verified_public_fixture_and_keeps_attribution(self):
        self.prepare(b'public-image')

    def test_rejects_changed_image(self):
        with self.assertRaises(ValueError):
            self.prepare(b'changed', checksum='0' * 40)

    def test_rejects_unexpected_redirect(self):
        with self.assertRaises(ValueError):
            self.prepare(b'image', url='https://unrelated.example/image.jpg')


if __name__ == '__main__':
    unittest.main()
