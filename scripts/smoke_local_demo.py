"""Run against the real local-demo backend. Never prints passwords, tokens, or voice text."""
import argparse
import base64
import json
import mimetypes
from pathlib import Path
import urllib.error
import urllib.parse
import urllib.request
import uuid
from datetime import datetime, timezone
import tempfile


def local_url(value):
    parsed = urllib.parse.urlsplit(value)
    if (parsed.scheme != "http" or parsed.hostname not in ("localhost", "127.0.0.1", "::1")
            or parsed.username is not None or parsed.password is not None or parsed.port == 0
            or parsed.path not in ("", "/") or parsed.query or parsed.fragment):
        raise ValueError("Use a loopback HTTP backend URL with no credentials or path.")
    return value.rstrip("/")


def settings(path):
    result = {}
    for line in path.read_text(encoding="utf-8-sig").splitlines():
        line = line.strip()
        if line and not line.startswith("#") and "=" in line:
            key, _, value = line.partition("=")
            result[key.strip()] = value.strip()
    if not result.get("LOCAL_DEMO_PASSWORD"):
        raise ValueError("Prepare .env.local-demo first.")
    return result


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, request, file, code, message, headers, new_url):
        return None


class Client:
    def __init__(self, base):
        self.base = local_url(base)
        self.opener = urllib.request.build_opener(NoRedirect)

    def request(self, path, token=None, method="GET", body=None, content_type=None, expected=200):
        headers = {}
        if token:
            headers["Authorization"] = "Bearer " + token
        if isinstance(body, dict):
            body = json.dumps(body).encode()
            content_type = "application/json"
        if content_type:
            headers["Content-Type"] = content_type
        request = urllib.request.Request(self.base + path, data=body, headers=headers, method=method)
        try:
            with self.opener.open(request, timeout=190) as response:
                status, payload = response.status, json.load(response)
        except urllib.error.HTTPError as error:
            status, payload = error.code, json.loads(error.read())
        if status != expected:
            raise RuntimeError(f"{method} {path}: HTTP {status}, expected {expected}")
        if expected >= 400:
            return payload
        if (payload.get("error") or {}).get("code"):
            raise RuntimeError(f"{path}: API reported an error")
        return payload["data"]

    def login(self, username, password):
        return self.request("/auth/login", method="POST", body={"username": username, "password": password})["accessToken"]

    def upload(self, path, token, field, name, data, content_type):
        boundary = "demo" + uuid.uuid4().hex
        header = (f"--{boundary}\r\nContent-Disposition: form-data; name=\"{field}\"; filename=\"{name}\"\r\n"
                  f"Content-Type: {content_type}\r\n\r\n").encode()
        body = header + data + f"\r\n--{boundary}--\r\n".encode()
        return self.request(path, token, "POST", body, "multipart/form-data; boundary=" + boundary)


def run(client, password, audio_path=None, complete=False, photo_path=None):
    if photo_path and (not complete or photo_path.suffix.lower() not in (".jpg", ".jpeg")):
        raise ValueError("--photo requires --complete and a public JPEG fixture.")
    if complete and audio_path is None:
        raise ValueError("--complete requires --audio so a failed voice test cannot be reported as a complete workflow.")
    care = client.login("soonja", password)
    owner = client.login("minji", password)
    if not client.request("/local-demo/status", care).get("localDemo"):
        raise RuntimeError("This is not the isolated local-demo backend")
    me = client.request("/users/me", care)
    if me["role"] != "CARETAKER" or not me["caretaker"]:
        raise RuntimeError("Caretaker seed is incomplete")
    today = client.request("/caretaker/tasks/today", care)
    if not today["gardens"]:
        raise RuntimeError("No assigned demo garden")
    garden = today["gardens"][0]["gardenId"]
    client.request("/caretaker/tasks/today", owner, expected=403)
    client.request(f"/gardens/{garden}/diary", care, expected=403)
    growth = client.request(f"/gardens/{garden}/growth", owner)
    client.request(f"/gardens/{garden}/harvest", owner)
    client.request(f"/gardens/{garden}/diary", owner)
    report = {"login": "passed", "authorization": "passed", "garden": garden,
              "growth": growth["stage"], "voice": "not_tested", "completion": "not_tested"}
    if audio_path:
        pending = [task for card in today["gardens"] if card["gardenId"] == garden
                   for task in card["tasks"] if task["status"] == "TODO"]
        if not pending:
            raise RuntimeError("No pending demo task. Existing records were preserved.")
        task = next((task for task in pending if task["type"] == "WATER"), pending[0])
        task_id = task["taskId"]
        extension = audio_path.suffix.lower()
        if extension not in (".webm", ".m4a", ".mp4", ".wav"):
            raise ValueError("Use WAV, WEBM, M4A or MP4 audio.")
        data = audio_path.read_bytes()
        if not data or len(data) > 20 * 1024 * 1024:
            raise ValueError("Choose a non-empty recording under 20 MB and 60 seconds.")
        memo = client.upload(f"/caretaker/tasks/{task_id}/voice-memos", care, "audio", "recording" + extension, data,
                             mimetypes.guess_type(audio_path.name)[0] or "application/octet-stream")
        if not memo.get("transcript"):
            raise RuntimeError(f"STT did not produce text: {memo['processingStatus']}. Audio was preserved for retry.")
        client.request(f"/caretaker/voice-memos/{memo['voiceMemoId']}/confirm", care, "POST", {})
        report["voice"] = "transcribed_and_confirmed"
        report["llm"] = "passed" if memo["processingStatus"] == "READY" else "fallback_used"
        if complete:
            png = base64.b64decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aRZkAAAAASUVORK5CYII=")
            photo = client.upload(f"/caretaker/tasks/{task_id}/photos", care, "photo",
                                 photo_path.name if photo_path else "smoke-test.png",
                                 photo_path.read_bytes() if photo_path else png,
                                 "image/jpeg" if photo_path else "image/png")
            if photo_path and not isinstance((photo.get("aiCheck") or {}).get("matched"), bool):
                raise RuntimeError("Photo upload did not receive a real vision result")
            finished = client.request(f"/caretaker/tasks/{task_id}/complete", care, "POST",
                                      {"photoId": photo["photoId"], "voiceMemoId": memo["voiceMemoId"]})
            diary = client.request(f"/gardens/{garden}/diary", owner)
            entry = next((item for item in diary["items"] if item.get("careLogId") == finished["careLogId"]), None)
            if not entry or entry["transcript"] != memo["transcript"] or entry["summary"] != memo["summary"]:
                raise RuntimeError("Confirmed voice was not connected to the family diary")
            report["completion"] = "passed"
            report["photo"] = "public_reference_image" if photo_path else "synthetic_test_image"
            report["vision"] = "passed" if photo_path else "not_tested"
    return report


def checked_run(client, password, report_path, audio_path=None, complete=False, photo_path=None):
    def save(status, **fields):
        report_path.parent.mkdir(parents=True, exist_ok=True)
        report = {"status": status, "checkedAt": datetime.now(timezone.utc).isoformat(), **fields}
        with tempfile.NamedTemporaryFile(mode="w", encoding="utf-8", dir=report_path.parent, suffix=".tmp", delete=False) as output:
            temporary = Path(output.name)
            json.dump(report, output, ensure_ascii=False, indent=2)
        try:
            temporary.replace(report_path)
        finally:
            temporary.unlink(missing_ok=True)
    save("running")
    try:
        result = run(client, password, audio_path, complete, photo_path)
    except Exception as error:
        # 비밀번호, 토큰, 발언이나 예외 원문을 보고서에 남기지 않는다.
        save("failed", errorType=type(error).__name__)
        raise
    save("passed", checks=result)
    return result


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--base", default="http://localhost:8080")
    parser.add_argument("--settings", type=Path, default=Path(__file__).resolve().parents[1] / ".env.local-demo")
    parser.add_argument("--audio", type=Path)
    parser.add_argument("--complete", action="store_true")
    parser.add_argument("--photo", type=Path, help="Public JPEG fixture; requires an actual vision result")
    args = parser.parse_args()
    report_path = Path(__file__).resolve().parents[1] / "build" / "reports" / "local-demo-smoke.json"
    result = checked_run(Client(args.base), settings(args.settings)["LOCAL_DEMO_PASSWORD"], report_path, args.audio, args.complete, args.photo)
    print(json.dumps(result, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    try:
        main()
    except (ValueError, RuntimeError, urllib.error.URLError) as error:
        raise SystemExit(str(error)) from None
