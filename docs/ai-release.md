# 배포와 팀 연동 우선순위

develop 6d8f52b 통합으로 실제 텃밭, 농장, 관리사와 돌봄 기록 연결 구현을 추가했다. 성장 계산도 실제 Garden과 팀 진화 이벤트에 반영한다. 운영 일기의 날짜 커서와 휴식일, 하트를 보존하며 음성 원문을 추가했다. 새 통합 검사는 아직 실행 전이므로 아래 초기 상태 표와 구분한다. 작물별 작업 지침과 배포 구성, 실제 기상 이력과 운영 검증은 남아 있다.

2026-11-13 작업 브랜치 기준이다. 추가 로컬 평가보다 배포 환경과 실제 도메인 연결을 먼저 확정한다.

## 먼저 필요한 세 가지

| 순서 | 해야 할 일 | 현재 상태 | 완료 기준 |
| --- | --- | --- | --- |
| 1 | 배포 서버와 AI 실행 위치 확정 | 서버 주소, 실행 방식, CPU와 여유 메모리 미확인 | Spring, STT, LLM을 실행할 서버와 네트워크 합의 |
| 2 | 실제 텃밭 도메인 연결 | 기본 조회 구현은 빈 목록이나 없음 반환. local-demo만 가상 자료 제공 | 실제 가입자의 텃밭과 관리사로 오늘 할 일, 음성, 수확 API 동작 |
| 3 | 배포 주소로 프론트 통합 확인 | 로컬 HTTP 흐름만 확인 | HTTPS 주소에서 로그인, 브라우저 녹음, 확정, 완료, 가족 일기 확인 |

AI 아바타, 서버 TTS 제공자, 자체 학습, 추가 평가와 후순위 화면은 위 연결 이후다.

## 서버 담당자와 확정할 내용

배포 대상, Linux 여부, Spring이 호스트 프로세스인지 컨테이너인지, CPU와 여유 메모리, DB와 Redis, HTTPS 주소를 확인한다. 저장소에는 서버 접속 정보나 운영 비밀번호가 없다.

현재 구현된 AI는 faster-whisper와 Ollama 호출이다. LLM_API_KEY, STT_API_KEY, TTS_API_KEY만 넣으면 외부 제공자가 연결되지는 않는다. 별도 제공자 구현 또는 팀의 기존 구현이 필요하다. 무료 API의 가용성이나 한도는 확인하지 않았다. 신규 지출이나 기존 유료 키가 있다고 가정하지 않는다.

기존 서버에서 모델을 실행하려면 자원을 확인해야 한다. 로컬 Compose의 Ollama와 STT 메모리 상한은 각각 4g와 2g다. 이는 운영 최소 사양 측정치가 아니며 JVM, DB와 다른 서비스 자원은 별도다.

## 동일 서버에서 AI를 실행하는 경우

Spring의 두 주소는 http://127.0.0.1:11434와 http://127.0.0.1:8765다. 현재 클라이언트는 loopback만 허용한다. 같은 서버라도 서로 다른 컨테이너의 127.0.0.1은 서로 연결되지 않는다. 로컬 시연은 service:ollama 네트워크 공유로 이 조건을 맞춘다. 팀 배포 구성에서도 같은 네트워크 공간을 사용하도록 정해야 한다. 임의 외부 주소는 현재 주소 검증에서 거부한다.

Ollama는 OLLAMA_NO_CLOUD=1과 qwen2.5:3b를 사용한다. STT는 다국어 base 모델을 명시적으로 준비한 뒤 오프라인으로 실행한다. local-ai/Dockerfile과 requirements.txt의 faster-whisper 1.2.1, PyAV 18 계열 호환 수정도 배포 이미지에 함께 반영한다.

운영용 docker-compose.yml(app, mariadb, redis, nginx, certbot)을 추가했다. app은 bridge 네트워크의 별도 컨테이너라 loopback AI 작업자에 닿지 않으므로 이 구성에서는 AI를 끈다. AI를 켜려면 같은 네트워크 공간 구성을 따로 정해야 한다. compose.local-demo.json은 가상 계정과 농장, local 프로필을 포함하므로 운영 배포 설정으로 그대로 사용하지 않는다.

## 실제 도메인 연결 지점

| 인터페이스 | 팀 도메인에서 제공할 값 | 연결이 없을 때 |
| --- | --- | --- |
| CareGardenReader | 실제 텃밭, 주인과 관리사 로그인 아이디, 작물, 심은 날, 농장, 작업 지침과 변경 시 실제 텃밭 행 잠금 | 오늘 할 일 빈 목록, 텃밭 기반 API 실패 |
| CaretakerFarmReader | 등록할 농장 ID와 이름 | 관리사 프로필 등록 시 농장을 찾지 못함 |
| HarvestGardenReader | 소유권, 농장 위치, 관리사, 수확 상태와 잠금 | 수확과 방문 API가 텃밭을 찾지 못함 |
| OwnerSummaryReader | 주인 이름, district와 연결 텃밭 ID | 사용자 정보에 실제 주인 요약 반영 불가 |
| GrowthWeatherReader | 농장의 날짜별 실제 온도 기록 | 가상 온도를 운영 예측 자료로 사용할 수 없음 |

상세 인터페이스는 [ai-handoff.md](ai-handoff.md)에 있다. 브랜치 밖의 팀 구현은 아직 확인하지 못했다. 실제 엔티티와 저장소 코드에 맞춰 연결해야 하며 새 가상 도메인을 추가해 완료로 처리하지 않는다.

## 환경변수와 DB 반영

[.env.prod.example](../.env.prod.example)은 docker-compose.yml 배포용 서버 .env 초안이다. 서버의 ~/growwithus-server/.env로 복사해 채우면 compose가 app 컨테이너에 넣는다. .env.prod라는 이름만 만들면 자동으로 읽지 않는다. 채운 파일은 커밋하지 않는다.

프로필은 prod를 사용한다. JWT와 내부 서비스 키를 실제 별도 값으로 채우고 CORS_ALLOWED_ORIGINS에는 실제 프론트 HTTPS Origin을 넣는다. FILE_BASE_URL은 실제 백엔드 HTTPS 주소이며 FILE_UPLOAD_DIR은 지속되는 업로드 볼륨과 연결한다. API 경로는 현재 접두어가 없으며 /api/v1을 임의 추가하지 않는다.

prod의 ddl-auto는 JPA_DDL_AUTO이며 기본값은 validate다. 새 DB에 처음 배포할 때만 update로 테이블을 만들고 되돌린다. 기존 DB라면 src/main/resources 아래 voice_memo.sql, care_growth.sql, diary.sql, visit_booking_day.sql과 기존 DB를 대조해 누락 열과 인덱스를 반영한다. 운영 테이블을 삭제하거나 시연 DB로 교체하지 않는다. scheduler를 켜기 전에 실제 텃밭과 기상 수집을 연결한다.

## 프론트 전달 계약

- 음성 multipart 필드는 audio, 형식은 WEBM, MP4, M4A, WAV이며 60초 이하다. 사진 필드는 photo다.
- HTTP 200만으로 성공을 판단하지 않고 processingStatus와 retryable을 확인한다. LLM_FAILED의 정리문은 원문 대체일 수 있다.
- 음성 확정과 작업 완료는 별도 요청이다. 완료에서 photoId와 voiceMemoId를 연결한다.
- 가족 일기는 /gardens/{gardenId}/diary이며 transcript와 summary를 별도 표시한다. [voice-api.md](voice-api.md)를 따른다.
- 비전 미설정 시 aiCheck는 null이다. 서버 TTS 제공자는 미구현이므로 브라우저 읽어주기 대체가 필요하다.

## 확인된 결과와 남은 검사

로컬 실제 서버에서 로그인, 역할 제한, STT, 메모 확정, 합성 사진 업로드, 작업 완료와 가족 일기 연결을 확인했다. 합성 음성에서 '잎'을 '입'으로 잘못 인식했다. 첫 전체 흐름의 LLM은 원문 대체였고 이후 공개 정답 문장의 직접 모델 응답과 Java 검증은 통과했다. 운영 품질 전체의 통과를 뜻하지 않는다.

배포 후에는 실제 사용자와 텃밭으로 Android WEBM 또는 iPhone MP4 녹음, 원문과 요약, 완료, 가족 일기, 소유권 제한과 파일 재시작 보존을 확인한다. 운영 DB의 동시 완료와 예약 경합도 미검증이다. 배포 주소와 서버 구성이 확인되기 전에는 배포 완료로 보고하지 않는다.
