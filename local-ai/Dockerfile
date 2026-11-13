FROM python:3.12-slim
WORKDIR /worker
ENV PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1
RUN apt-get update && apt-get install -y --no-install-recommends libgomp1 espeak-ng ffmpeg && rm -rf /var/lib/apt/lists/*
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY check_audio_decoder.py ./
RUN python check_audio_decoder.py
COPY stt_server.py tts_server.py test_tts_server.py download_model.py ./
RUN python -m unittest test_tts_server
CMD ["python", "stt_server.py"]
