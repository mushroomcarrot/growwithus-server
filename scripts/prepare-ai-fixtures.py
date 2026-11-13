"""Prepare public test fixtures only. Never reads recordings, credentials or database data."""
import hashlib
import json
from pathlib import Path
import subprocess
import sys
import urllib.request

PHOTO = "https://upload.wikimedia.org/wikipedia/commons/8/89/Tomato_je.jpg"
PHOTO_SHA1 = "b31a3f11a711cf86c8fcafbb4eb243c9f5c37932"


def prepare(output):
    output.mkdir(parents=True, exist_ok=True)
    root = Path(__file__).resolve().parents[1]
    reference = root / "src/test/resources/ai-evaluation/synthetic-korean.wav"
    wave = output / "sample.wav"
    wave.write_bytes(reference.read_bytes())
    for extension, codec in (("webm", "libopus"), ("mp4", "aac"), ("m4a", "aac")):
        subprocess.run(["ffmpeg", "-y", "-nostdin", "-hide_banner", "-loglevel", "error",
                        "-i", str(wave), "-c:a", codec, str(output / ("sample." + extension))],
                       check=True, timeout=30)
    request = urllib.request.Request(PHOTO, headers={"User-Agent": "GrowWithUs-local-AI-check/1.0"})
    with urllib.request.urlopen(request, timeout=45) as response:
        if not response.url.startswith("https://upload.wikimedia.org/"):
            raise ValueError("Unexpected public fixture host")
        photo = response.read(1024 * 1024 + 1)
    if len(photo) > 1024 * 1024 or hashlib.sha1(photo).hexdigest() != PHOTO_SHA1:
        raise ValueError("Public fixture checksum changed; review the source before using it")
    (output / "tomato.jpg").write_bytes(photo)
    (output / "sources.json").write_text(json.dumps({
        "audio": "Project synthetic Korean reference. Not a person's recording.",
        "photo": {"author": "Softeis", "source": "https://commons.wikimedia.org/wiki/File:Tomato_je.jpg",
                  "license": "https://creativecommons.org/licenses/by-sa/3.0/", "sha1": PHOTO_SHA1,
                  "changes": "Original downloaded unchanged; application creates a bounded JPEG preview."}
    }, ensure_ascii=False, indent=2), encoding="utf-8")
    print("Prepared WAV, WEBM, MP4, M4A and attributed tomato photo fixtures.")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit("Pass a dedicated fixture output directory.")
    prepare(Path(sys.argv[1]))
