import http.client
import json
import shutil
import subprocess
import threading
import unittest
from http.server import ThreadingHTTPServer
from unittest.mock import patch
import tts_server


class SynthesisTest(unittest.TestCase):
    def test_rejects_invalid_text_and_speed_before_running_commands(self):
        with patch('tts_server.subprocess.run') as run:
            for text, speed in [(None, .9), (' ', .9), ('x' * 301, .9), ('x\0', .9),
                                ('x', True), ('x', float('nan')), ('x', .49), ('x', 1.51)]:
                with self.assertRaises(ValueError):
                    tts_server.synthesize(text, speed)
            run.assert_not_called()

    def test_passes_text_as_stdin_and_speed_as_a_separate_argument(self):
        wave = b'RIFF' + b'x' * 100
        with patch('tts_server.subprocess.run', side_effect=[
                subprocess.CompletedProcess([], 0, stdout=wave),
                subprocess.CompletedProcess([], 0, stdout=b'ID3test')]) as run:
            self.assertEqual(b'ID3test', tts_server.synthesize('안녕하세요; $(whoami)', 1.2))
            self.assertEqual('210', run.call_args_list[0].args[0][-1])
            self.assertEqual('안녕하세요; $(whoami)'.encode(), run.call_args_list[0].kwargs['input'])
            self.assertNotIn('shell', run.call_args_list[0].kwargs)

    def test_propagates_worker_failure(self):
        with patch('tts_server.subprocess.run', side_effect=subprocess.TimeoutExpired('espeak-ng', 15)):
            with self.assertRaises(subprocess.TimeoutExpired):
                tts_server.synthesize('안녕하세요')

    @unittest.skipUnless(shutil.which('espeak-ng') and shutil.which('ffmpeg'), 'TTS binaries unavailable on this host')
    def test_real_korean_audio_is_decodable(self):
        audio = tts_server.synthesize('오늘 잎과 흙을 살펴보세요.')
        result = subprocess.run(['ffmpeg', '-v', 'error', '-i', 'pipe:0', '-f', 'null', '-'],
                                input=audio, capture_output=True, timeout=15)
        self.assertEqual(0, result.returncode)


class HttpTest(unittest.TestCase):
    def setUp(self):
        self.server = ThreadingHTTPServer(('127.0.0.1', 0), tts_server.TtsHandler)
        self.server.inference_lock = threading.Lock()
        self.thread = threading.Thread(target=self.server.serve_forever, daemon=True)
        self.thread.start()

    def tearDown(self):
        self.server.shutdown(); self.server.server_close(); self.thread.join()

    def request(self, body, content_type='application/json'):
        connection = http.client.HTTPConnection(*self.server.server_address, timeout=3)
        connection.request('POST', '/synthesize', body, {'Content-Type': content_type})
        response = connection.getresponse()
        result = response.status, response.getheader('Content-Type'), response.read()
        connection.close()
        return result

    def test_returns_mp3_and_rejects_invalid_requests(self):
        with patch('tts_server.synthesize', return_value=b'ID3audio'):
            status, content_type, audio = self.request(json.dumps({'text': '안녕하세요'}).encode())
            self.assertEqual((200, 'audio/mpeg', b'ID3audio'), (status, content_type, audio))
        self.assertEqual(400, self.request(b'null')[0])
        self.assertEqual(415, self.request(b'{}', 'text/plain')[0])
        self.assertEqual(413, self.request(b' ' * 8193)[0])

    def test_returns_retryable_worker_failure_and_releases_lock(self):
        with patch('tts_server.synthesize', side_effect=RuntimeError('private error')):
            status, _, body = self.request(b'{"text":"hello"}')
            self.assertEqual(503, status); self.assertNotIn(b'private', body)
        self.assertTrue(self.server.inference_lock.acquire(False))
        try:
            self.assertEqual(503, self.request(b'{"text":"hello"}')[0])
        finally:
            self.server.inference_lock.release()


if __name__ == '__main__':
    unittest.main()
