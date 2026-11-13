"""Exercise the installed audio decoder without downloading or loading a model."""
import io
import json
import wave
from importlib.metadata import version

from faster_whisper.audio import decode_audio


def main():
    audio = io.BytesIO()
    with wave.open(audio, "wb") as output:
        output.setnchannels(1)
        output.setsampwidth(2)
        output.setframerate(16000)
        output.writeframes(b"\0\0" * 1600)
    decoded = decode_audio(io.BytesIO(audio.getvalue()), sampling_rate=16000)
    if decoded.shape != (1600,):
        raise RuntimeError("Audio decoder returned an unexpected sample count")
    print(json.dumps({"audioDecoder": "passed", "fasterWhisper": version("faster-whisper"), "pyav": version("av")}))


if __name__ == "__main__":
    main()
