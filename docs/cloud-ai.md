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

## 검증과 남은 확인

일반 테스트는 가짜 키와 모의 HTTP 응답을 사용하며 Google에 요청하지 않는다. 키 누락 시 시작 실패, 로컬 제공자와 중복 등록 방지, 429, 차단 및 잘린 응답, 원문 파싱과 사진 불일치 판정을 검사한다. Linux Docker 테스트는 FFmpeg로 실제 WAV, WEBM, MP4와 M4A 변환 및 60초 제한, 한국어 MP3 합성과 디코딩을 확인한다.

키 설정 스크립트의 성공은 해당 모델에 텍스트 요청 1건을 보낼 수 있다는 뜻이다. 실제 한국어 음성 인식 품질이나 사진 판정 정확도를 보장하지 않는다. 배포 전후 공개 시연용 녹음과 사진으로 음성 업로드, 정리와 확정, 작업 완료, 성장 설명, 사진 판정을 실제 HTTP로 확인한다. 2 GB 서버의 동시 요청 지연과 메모리도 운영 주소에서 확인한다. 이 확인 전에는 운영 AI 검증 완료로 보고하지 않는다.

## 공식 자료

- 모델: https://ai.google.dev/gemini-api/docs/models/gemini-3.5-flash-lite
- 가격과 무료 티어 데이터 처리: https://ai.google.dev/gemini-api/docs/pricing/
- API 키: https://ai.google.dev/gemini-api/docs/api-key
- generateContent: https://ai.google.dev/api/generate-content


## 배포 전 실제 호출 검사

PowerShell에서 `& .\scripts\verify-cloud-ai.ps1`을 실행한다. 기존 `.env.local-demo`의 DB 설정과 `.env.cloud-ai`의 비공개 API 키가 필요하다. DB와 Redis만 시작하며 Ollama나 Whisper 모델을 내려받거나 시작하지 않는다. 기존 DB를 초기화하거나 볼륨을 삭제하지 않는다.

먼저 일반 테스트와 Linux 음성 코덱 검사를 실행한다. 이후 저장소의 공개 합성 한국어 음성을 WAV, WEBM, MP4와 M4A로 직접 변환하고, 체크섬을 검증한 공개 토마토 사진만 실제 API에 전송한다. 최대 9건의 Gemini 요청을 15초 이상 간격으로 보내며 자동 재시도는 없다. 이 간격이 프로젝트별 무료 한도 준수를 보장하지는 않으며 실제 한도는 AI Studio에서 확인한다. 실제 사람의 녹음, 운영 사용자 사진이나 DB 레코드는 외부 호출 검사 입력으로 사용하지 않는다. 일반 테스트와 실제 호출 검사 결과는 따로 기록한다.

검사 항목은 음성 4종에서 물, 잎과 점에 대한 발화 인식, 인식 원문 기반 요약과 잎 반점 추출, 명시적인 잎 반점 문장, 성장 설명, 한국어 MP3와 캐시, 토마토 사진의 일치 판정과 무관한 요청의 불일치 판정이다. 실제 AI 결과를 메모 저장, 확정과 일기 연결용 조회에 넘기는 흐름은 메모리 저장소 대역으로 검증한다. 이것을 실제 DB 또는 HTTP 연동 검증으로 보고하지 않는다.

결과는 `build/reports/cloud-ai-predeploy.json`, `build/reports/cloud-live-receipt.json`과 `build/reports/cloud-verification.log`에 저장한다. 공개 합성 데이터의 인식 원문과 출력은 검토용으로 JSON에 포함되며 API 키는 포함하지 않는다. `build/reports/tts-review.mp3`는 사람이 직접 들어서 발음을 확인한다. 정상 MP3 생성과 디코딩 성공만으로 발음 품질을 판정하지 않는다.

무료 한도나 네트워크 문제로 실패하면 실패 결과를 그대로 남긴다. 합격 판정을 만들기 위해 오류를 무시하거나 반복 호출하지 않는다. 일반 테스트만 실행하려면 `-TestsOnly`를 사용한다. 일반 테스트에서는 가짜 키와 모의 응답만 사용한다.


검사용 토마토 사진: Softeis, [Tomato_je.jpg](https://commons.wikimedia.org/wiki/File:Tomato_je.jpg), [CC BY-SA 3.0](https://creativecommons.org/licenses/by-sa/3.0/). 원본을 검증해 보관하며 API 입력에는 앱이 생성한 축소 JPEG를 사용한다.
