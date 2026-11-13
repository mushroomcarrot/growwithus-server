# 2 CPU, 2 GB 서버용 AI 구성

## 처리 방식

`AI_GEMINI_ENABLED=true`이면 음성 인식, 음성 메모 정리, 작업 생성, 성장 설명과 사진 확인을 Gemini API로 처리한다. 기존 프론트 API와 응답 형식은 바뀌지 않는다. Ollama, Whisper 모델과 별도 TTS 컨테이너는 실행하지 않는다. 이 구성은 기존 로컬 AI 안내보다 운영 배포에서 우선한다.

서버는 FFmpeg로 WAV, WEBM, MP4와 M4A를 16 kHz 모노 WAV로 변환한다. 실제 변환 길이가 60초를 넘으면 거부한다. 읽어주기는 앱 이미지에 설치한 eSpeak NG와 FFmpeg로 MP3를 생성한다. 한국어 발음의 자연스러움은 신경망 TTS보다 제한적이므로 시연 문장을 직접 들어본다.

동시에 진행하는 Gemini 요청은 최대 2개, 로컬 음성 변환 프로세스는 최대 1개다. 여유가 없으면 기존 오류와 재시도 흐름으로 반환한다. 사진은 축소한 JPEG를 보낸다. 원본 업로드는 유지하며 모델 실패를 사진 일치 판정으로 위장하지 않는다. 성장 설명 실패는 기존 템플릿으로 대체한다. 음성 정리 실패는 이미 얻은 원문을 보존하고 재시도한다. 실패 후 다른 유료 모델이나 로컬 대형 모델로 자동 전환하지 않는다.

## 키 준비와 비용

1. https://aistudio.google.com/apikey 에서 **Cloud Billing을 연결하지 않은 무료 프로젝트**의 키를 발급한다. Gemini Pro 구독과 API 사용은 별개다.
2. 저장소 루트에서 PowerShell로 `& .\scripts\SETUP-CLOUD-AI.cmd`를 실행한다. 숨김 입력으로 키를 받고 공개 테스트 문장 1건을 호출한다. 성공하면 Git에서 제외된 `.env.cloud-ai`에 저장한다.
3. `.env.cloud-ai` 내용을 운영 서버의 비공개 `.env`에 직접 반영한다. 키를 채팅, Git, 프론트 환경변수나 PR에 올리지 않는다. 기존 DB, JWT와 파일 설정을 덮어쓰지 않는다.
4. 기존 키를 교체하면 앱을 다시 생성한다. `docker compose up -d --build app` 실행은 배포 담당자가 운영 환경에서 진행한다.

무료 한도는 프로젝트와 모델에 따라 다르며 AI Studio에서 확인한다. 서버 코드가 결제 상태를 조회하거나 무료 사용을 보장하지는 못한다. 결제가 연결된 키는 사용하지 않는다. 429 응답은 무한 재시도하지 않는다. 무료 티어에 입력한 데이터는 Google 제품 개선에 사용될 수 있으므로 실제 사람의 녹음과 사진을 보내기 전에 데이터 처리 조건과 동의를 확인한다. 설정 스크립트는 개인정보 없는 텍스트만 사용한다.

```dotenv
AI_GEMINI_ENABLED=true
GEMINI_API_KEY=발급받은_서버용_키
GEMINI_MODEL=gemini-3.5-flash-lite
AI_LOCAL_ENABLED=false
AI_LOCAL_STT_ENABLED=false
AI_LOCAL_TTS_ENABLED=false
```

모델 기본값은 Gemini 3.5 Flash Lite다. 모델 접근 권한, 무료 한도와 지원 여부는 발급 프로젝트에서 실제 호출로 확인한다. 운영 이미지에는 FFmpeg와 eSpeak NG가 포함되어 있어 추가 모델 다운로드가 필요 없다. `.env.prod.example`을 참고하되 실제 키를 예제 파일에 넣지 않는다.

## 관리사 AI 아바타

`GEMINI_IMAGE_MODEL`에 이미지 모델 (예: `gemini-3.1-flash-image`)을 넣으면 `POST /caretakers/me/avatar`가 관리사 사진을 캐릭터 그림으로 바꾼다. 비워 두면 그림을 만들지 않고 작업은 FAILED, 화면은 기본 아바타를 쓴다. `AI_GEMINI_ENABLED=true`일 때만 쓰인다.

1. 서버는 사진을 768픽셀 이하 JPEG로 줄여 Interactions API (`POST /v1beta/interactions`)로 보낸다. 받은 그림은 가운데를 잘라 512픽셀 PNG로 다시 만들어 저장한다. 올린 사진 원본은 저장하지 않는다.
2. 동시에 만드는 그림은 1개, 기다리는 요청은 8개까지다. 실패하거나 한도에 걸리면 다시 시도하지 않고 FAILED로 끝낸다. 다른 모델로 자동 전환하지 않는다.
3. 이미지 모델의 무료 한도와 사진 입력 지원 여부는 모델과 프로젝트마다 다르므로 AI Studio에서 확인한다. 이 저장소의 테스트는 모의 HTTP 응답만 쓰며 실제 키로 이미지 모델을 호출해 보지 않았다. 운영에서 켜기 전에 시연용 사진 한 장으로 직접 확인한다.
4. 실제 사람의 얼굴 사진을 외부로 보내는 기능이므로 화면의 초상권 동의 문구에 외부 AI 서비스로 사진을 보낸다는 내용을 포함한다. 서버는 `portraitConsent=true`가 없으면 C4003으로 거부한다.

## 검증과 남은 확인

일반 테스트는 가짜 키와 모의 HTTP 응답을 사용하며 Google에 요청하지 않는다. 키 누락 시 시작 실패, 로컬 제공자와 중복 등록 방지, 429, 차단 및 잘린 응답, 원문 파싱과 사진 불일치 판정을 검사한다. Linux Docker 테스트는 FFmpeg로 실제 WAV, WEBM, MP4와 M4A 변환 및 60초 제한, 한국어 MP3 합성과 디코딩을 확인한다.

키 설정 스크립트의 성공은 해당 모델에 텍스트 요청 1건을 보낼 수 있다는 뜻이다. 실제 한국어 음성 인식 품질이나 사진 판정 정확도를 보장하지 않는다. 배포 전후 공개 시연용 녹음과 사진으로 음성 업로드, 정리와 확정, 작업 완료, 성장 설명, 사진 판정을 실제 HTTP로 확인한다. 2 GB 서버의 동시 요청 지연과 메모리도 운영 주소에서 확인한다. 이 확인 전에는 운영 AI 검증 완료로 보고하지 않는다.

## 공식 자료

- 모델: https://ai.google.dev/gemini-api/docs/models/gemini-3.5-flash-lite
- 가격과 무료 티어 데이터 처리: https://ai.google.dev/gemini-api/docs/pricing/
- API 키: https://ai.google.dev/gemini-api/docs/api-key
- generateContent: https://ai.google.dev/api/generate-content
