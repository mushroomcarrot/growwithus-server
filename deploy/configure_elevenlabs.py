"""Apply only ElevenLabs settings from Actions secrets, preserving the server's other settings."""
import os
from pathlib import Path
import re
import sys
import tempfile


def configure(env_file, key, voice, model):
    if not key:
        return False
    if not re.fullmatch(r"sk_[A-Za-z0-9_]{20,200}", key):
        raise ValueError("Invalid ElevenLabs credential format")
    if not re.fullmatch(r"[A-Za-z0-9]{20}", voice or ""):
        raise ValueError("Invalid ElevenLabs voice ID")
    if not re.fullmatch(r"eleven_[a-z0-9_]{1,60}", model or ""):
        raise ValueError("Invalid ElevenLabs model")
    updates = {"AI_ELEVENLABS_ENABLED": "true", "ELEVENLABS_API_KEY": key,
               "ELEVENLABS_VOICE_ID": voice, "ELEVENLABS_MODEL": model}
    target = Path(env_file)
    lines, seen = [], set()
    for line in target.read_text(encoding="utf-8-sig").splitlines(keepends=True):
        field = line.partition("=")[0].strip()
        if field in updates:
            if field not in seen:
                lines.append(field + "=" + updates[field] + "\n")
                seen.add(field)
        else:
            lines.append(line)
    output = "".join(lines)
    if output and not output.endswith("\n"):
        output += "\n"
    output += "".join(k + "=" + v + "\n" for k, v in updates.items() if k not in seen)
    fd, name = tempfile.mkstemp(prefix=".env-ai-", dir=target.parent)
    try:
        with os.fdopen(fd, "w", encoding="utf-8", newline="") as stream:
            stream.write(output)
        os.replace(name, target)
    finally:
        if os.path.exists(name):
            os.unlink(name)
    return True


if __name__ == "__main__":
    try:
        applied = configure(".env", os.environ.get("ELEVENLABS_API_KEY", ""),
                            os.environ.get("ELEVENLABS_VOICE_ID", ""),
                            os.environ.get("ELEVENLABS_MODEL", ""))
        print("ElevenLabs settings applied." if applied else "No ElevenLabs secret supplied; server settings preserved.")
    except (ValueError, OSError):
        print("ElevenLabs configuration failed. No credential values were logged.", file=sys.stderr)
        sys.exit(1)
