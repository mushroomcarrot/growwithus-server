# 로컬 음성 인식

기존 모델을 사용하는 CPU 음성 인식 서버다. 직접 모델을 학습하지 않고 외부 유료 API도 호출하지 않는다. Java 백엔드와 같은 호스트의 127.0.0.1에서만 실행한다.

## 구성

1. `stt_server.py`: 로컬 faster-whisper 모델을 적재하는 Python 서버
2. `LocalWhisperSttClient`: Java의 `SttClient` 구현체
3. `VoiceMemoAnalysisService`: STT 결과를 기존 LLM 인터페이스로 정리

현재 작업은 서버와 연결 코드 및 모의 테스트까지 완료한 상태다. 이 PC에는 faster-whisper 설치와 실제 모델 적재가 확인되지 않았고, 실제 녹음으로 정확도나 응답 시간을 검증하지 않았다. 관리사 업로드, 메모 저장과 확정, 재시도 API를 추가했으며 실제 텃밭 조회 어댑터와 DB 검증이 필요하다. [음성 API](../docs/voice-api.md)를 참고한다.

## 준비

프로젝트 루트에서 Python 가상환경을 만들고 의존성을 설치한다. Python 버전에 맞는 의존성 wheel이 필요하며, GPU와 CUDA는 사용하지 않는다.

```powershell
python -m venv local-ai/.venv
.\local-ai\.venv\Scripts\python.exe -m pip install -r local-ai/requirements.txt
```

한국어를 지원하는 다국어 faster-whisper 모델을 별도로 다운로드한다. 영어 전용 `.en` 모델을 쓰지 않는다. 모델 폴더에 `model.bin`, `config.json`, `tokenizer.json`이 있어야 한다. `preprocessor_config.json`은 선택 파일이다. 공식 base 저장소에 없는 선택 파일 때문에 시작을 막지 않는다. 모델과 라이브러리의 이용 조건은 배포 전에 확인한다.

Windows에서는 [로컬 시연 환경](../docs/local-demo.md)의 Docker 실행 절차를 먼저 권장한다. Python 3.12 컨테이너에 라이브러리를 설치하고 `download_model.py`를 명시적으로 실행해 모델을 준비한다. 추론 서버가 요청 중 모델을 다운로드하는 경로는 없다.

서버는 모델 이름으로 자동 다운로드하지 않는다. 모델 폴더를 지정하지 않았거나 필요한 파일이 없으면 시작하지 않는다. `local_files_only=True`와 `HF_HUB_OFFLINE=1`을 적용한다.

```powershell
$env:WHISPER_MODEL_PATH = 'C:\path\to\local-multilingual-whisper-model'
$env:WHISPER_PORT = '8765'
.\local-ai\.venv\Scripts\python.exe local-ai/stt_server.py
```

백엔드 `.env`에서 다음을 설정하고 Spring 애플리케이션을 다시 실행한다.

```properties
AI_LOCAL_STT_ENABLED=true
AI_LOCAL_STT_BASE_URL=http://127.0.0.1:8765
```

모델 의존성 설치와 파일 다운로드에는 네트워크와 디스크 공간이 필요하다. 서버 실행 시 API 키나 결제 계정은 필요하지 않다. LLM 정리까지 사용하려면 별도로 로컬 Ollama를 준비하거나, LLM이 없을 때 원문을 임시 요약으로 쓰는 대체 경로를 사용한다.

## 내부 통신

브라우저가 이 서버를 직접 호출하지 않는다. 인증과 파일 형식 검증은 Spring 쪽이 담당하고, 음성 바이트만 로컬 서버에 전달한다. 원본 파일 이름은 전송하지 않는다.

| 요청 | 내용 |
| --- | --- |
| GET `/health` | 모델이 적재된 프로세스의 상태. `{"ready":true}` |
| POST `/transcribe` | `Content-Type: application/octet-stream`, 필수 `Content-Length`, 본문은 음성 바이트 |

응답은 `{"text":"인식된 원문","durationSeconds":3.5}`다. 이 경로는 로컬 작업 서버용이며 공개 API 명세의 관리사 경로를 대신하지 않는다.

1. 파일 최대 20MB, 디코딩된 원본 음성 길이 최대 60초를 검사한다. 공통 업로드 모듈이 길이를 읽지 못하는 webm과 조각 mp4는 그 앞에서 5MB로 제한된다. VAD로 무음을 제거한 뒤의 길이가 아닌 원본 길이로 제한한다.
2. 공통 업로드 모듈에서 허용한 webm, m4a, mp4, wav를 바이트로 전달한다. 실제 각 컨테이너의 인식은 PyAV 디코더에 따르며 브라우저별 실제 녹음 시험은 남아 있다.
3. 인식 결과는 최대 8000자다. 무음은 원문을 지어내지 않고 실패한다.
4. 한 번에 한 건만 인식한다. 처리 중 추가 요청은 503이며 자동으로 큐에 무한히 쌓지 않는다.
5. 음성 파일과 원문을 디스크에 저장하거나 로그에 남기지 않는다. 오류 응답에 내부 예외를 포함하지 않는다.
6. 서버는 loopback에 고정되어 있고 CORS를 허용하지 않는다. 인증 없는 작업 서버이므로 외부에 공개하지 않는다.
7. Java는 공통 읽기 타임아웃을 사용한다. 기본 30초, 로컬 시연 Compose는 90초다. 모델이 느려 이를 넘으면 C502이며 5초 응답 목표 충족은 아직 검증하지 않았다.

| 로컬 상태 | Java 처리 |
| --- | --- |
| 정상 원문과 0초 초과, 60초 이하 길이 | 원문 반환 |
| 400, 413, 415 또는 60초 초과 | C4001 파일 검증 오류 |
| 무음 422, 처리 중 503, 연결 실패, 잘못된 응답 | C502 AI 호출 실패 |

## 검사

모델을 설치하지 않고 경계 로직을 시험한다.

```powershell
python -m unittest discover -s local-ai -p 'test_*.py' -v
```

Java 쪽 HTTP 검사는 `LocalSttSelfTest`와 JUnit 래퍼 `LocalSttTest`에 있다. 실제 모델 대신 Spring 모의 HTTP 서버로 요청 바이트, 길이 제한, 응답 검증과 비활성화 설정을 검사한다.

공식 구현 참고: https://github.com/SYSTRAN/faster-whisper
