# AI 기능 인수인계

## develop 6d8f52b 통합 후 변경

실제 텃밭, 농장과 관리사 조회 구현을 추가했다. 돌봄 기록을 팀의 홈, 날짜 커서 일기와 감사 하트에 연결하고 음성 원문을 운영 일기 응답에도 포함했다. 성장 계산 결과를 실제 Garden과 팀 진화 이벤트로 반영하며 이벤트 ID와 알림을 맞췄다. 역할 변경 시 주인과 관리사 프로필 상태를 모두 복구한다.

운영 일기의 커서는 팀 구현의 날짜다. local-demo만 기존 dry_ 커서를 사용한다. 관리사 요약의 받은 하트는 팀 하트 테이블에서 계산한다. 원격 머지 전 Docker Java 21 전체 빌드와 테스트, 로컬 contextLoads 검사가 남아 있다. 아래의 이전 미연결 목록은 통합 전 기록이며 이 문단의 변경을 우선해 읽는다.

현재도 남는 부분은 작물별 작업 지침 자료 연결, 실제 기상 이력 확보, 배포 서버의 AI 실행 구성과 HTTPS 프론트 통합, 운영 DB 스키마 반영과 경합 검사다. 자체 학습, 서버 TTS와 AI 아바타는 구현하지 않았다.

현재 우선 작업은 추가 로컬 평가가 아닌 배포 서버와 AI 실행 위치 확정, 실제 도메인 연결, 배포 주소에서 프론트 통합 확인이다. [ai-release.md](ai-release.md)와 [.env.prod.example](../.env.prod.example)에 조건과 연결 지점을 정리했다. 아래의 로컬 확인 기록은 배포 완료를 뜻하지 않는다.

## 2026-11-13 로컬 실행 확인

11:33 KST Ollama 직접 검사에서 앱의 정리 프롬프트와 공개 정답 문장에 대해 JSON 응답을 받았다. 모델 응답 시간은 약 7.06초이며 LEAF_SPOT과 원문에 실제 존재하는 근거 문장을 반환했다. 저장된 응답을 같은 입력으로 Java VoiceMemoAnalysisService에 넣는 재생 검사도 대체 처리 없이 통과했다. 앞서 STT가 '잎'을 '입'으로 인식한 입력과는 다르므로 이전 요약 실패의 원인이 해결됐다고 단정하지 않는다.

RUN-AI-EVALUATION.cmd는 기존 evaluation 프로필로 Java 실제 분석 서비스와 로컬 모델을 사용해 공개 합성 문장 12개를 검사한다. 원본 모델 응답도 보고서의 modelReplies에 기록해 형식이나 근거 검증 실패를 확인할 수 있다. 사용자 녹음이나 DB 작업은 변경하지 않는다. 이 평가를 실행하기 전에는 정확도나 실패율을 보고하지 않는다.

11:28 KST 수정 STT 이미지에서 한국어 합성 음성 진단이 통과했다. 약 5.3초 파일의 직접 모델 검사 시간은 약 1.16초다. 한 개 합성 파일의 결과이므로 사람 녹음의 정확도나 전체 지연 지표로 일반화하지 않는다. 정답의 '잎'을 '입'으로 인식한 부분이 있어 확인과 수정이 필요하다.

11:30 KST 실제 HTTP 검사에서 로그인, 역할별 접근 제한, 성장 조회, 음성 업로드와 STT, 메모 확정, 합성 사진 업로드, 작업 완료, 가족 일기의 원문과 정리문 연결이 통과했다. LLM 정리는 실패해 원문 대체를 사용했다. 전체 모델 요약 성공으로 보고하면 안 된다. 오늘의 유일한 CHECK 작업은 이 검사로 완료된 상태다. 사진은 실제 인증 사진이 아닌 합성 테스트 이미지다.

CHECK-LOCAL-LLM.cmd는 STT 컨테이너의 공유 네트워크 안에서 Ollama에 실제 앱의 정리 프롬프트와 공개 합성 문장을 전송한다. 원래 모델 응답이나 HTTP 오류와 지연을 build/reports/local-llm-diagnostic.json에 기록해 요약 실패 원인을 확인한다. 모델 응답 수신 성공과 앱의 정리문 검증 성공은 별개다. 사용자 녹음과 인증 정보는 사용하지 않으며 작업 기록을 변경하지 않는다.

11:16 KST 모델 직접 진단에서도 metadata_errors TypeError를 재현했다. 공식 faster-whisper 이슈 #1589와 같은 라이브러리 호환 오류다. faster-whisper 1.2.1과 PyAV 18 계열로 설치 범위를 제한하고 이미지 빌드 시 실제 WAV 해독 검사를 추가했다. FIX-LOCAL-STT.cmd로 STT만 재빌드하고 다시 진단할 수 있다. 수정 이미지 빌드와 음성 HTTP 흐름은 재실행 전까지 미검증이다.

- Docker 설치와 Compose 서버 시작을 확인했다. Java 21 컨테이너 이미지 빌드와 서버 시작이 성공했으며, 전체 Gradle 테스트의 통과를 뜻하지 않는다.
- 09:59 KST 기본 HTTP 검사에서 관리사 로그인, 가족 로그인, 역할별 접근 제한, 오늘 할 일, 성장 단계 FLOWER, 수확 안내, 가족 일기 조회가 통과했다. 실제 MariaDB와 Redis를 사용하는 로컬 시연 환경의 결과다.
- 10:03 KST 한국어 합성 음성 업로드는 STT_FAILED로 끝났다. 파일은 보존됐고 메모 확정, 사진 업로드, 작업 완료는 실행되지 않았다. 음성 흐름 전체가 검증됐다고 설명하면 안 된다.
- `CHECK-LOCAL-STT.cmd`는 실행 중인 STT 컨테이너에서 공개 합성 음성으로 모델을 직접 검사한다. 결과는 `build/reports/local-stt-diagnostic.json`에 기록한다. 일반 Windows 계정에서 실행하며, 설정 파일 내용이나 사용자 녹음을 출력하지 않는다.
- 합성 WAV와 정답 문장, 생성 정보를 `src/test/resources/ai-evaluation/`에 추가했다. Windows 내장 한국어 음성으로 만든 약 5.3초 파일이다. 실제 사람의 음성 인식 정확도를 평가한 자료가 아니다. 이 파일의 업로드 검사까지 핵심 Java 78개가 통과했다.
- 원격 업로드는 `5f11d5c`까지 확인했다. 이후 로컬 커밋은 추가 push가 필요하다. main과 develop은 병합하지 않았다.

아래의 기존 미검증 목록은 위 실행 결과를 우선해서 읽는다. 운영 DB, 팀 프론트 연동, 실제 사람 녹음과 STT 전체 흐름은 여전히 미검증이다.

2026-11-13, 작업 브랜치 `feat/seungyoon-ai` 기준이다. main과 develop에 병합하지 않았다. 사용자 fetch 결과 origin/develop은 기준 `0f025dc`와 같고, 작업 브랜치 `f16edaf`까지 원격 업로드를 확인했다. 실제 팀 도메인과 실행 통합 검사는 남아 있다.

## 이슈별 상태

| 이슈 | 구현한 코드 | 실제 연동 전에 남은 항목 |
| --- | --- | --- |
| #5 관리사 | 프로필 등록, 기본 SVG, 월별 요약, TTS 인터페이스와 캐시, 내 정보의 돌봄 수 | 운영 농장 조회, TTS 공급자. AI 아바타 생성과 상태 폴링은 후순위로 미구현 |
| #7 오늘 작업 | 검증된 작업 자료와 날씨, 성장, 최근 이상 징후, 반복 간격, 관리사 하루 120분을 반영. 조회 시 생성과 배치 | 팀 텃밭 조회와 실제 검증된 작물별 작업 자료. 모델 출력 자체로 농법이나 작업 시간을 만들지 않음 |
| #8 성장 | 적산온도 계산, 관찰 보정, 상태와 이벤트, 가족 일기, 알림, 수확 응답에 저장 예측일 반영 | 실제 온도 자료와 작물별 기준, MariaDB 잠금과 프론트 검증. 시연 수치는 가상 자료 |
| #12 작업 인증 | 사진 저장, 선택적 로컬 비전 연결, 완료 검증, 돌봄 기록, 가족 알림과 일기 | 실제 담당자 조회와 통합 검사. 기본 시연 모델은 비전 모델이 아니므로 aiCheck는 null |
| #13 음성 | WEBM, MP4, M4A, WAV, STT와 문장 정리, 초안 보존, 확인과 수정, 재시도, 응답 유실 복구, 이상 징후 입력 연결 | 실제 한국어 녹음 품질과 지연, 서버 부팅, JWT와 DB 연동 검사 |
| #14 수확과 방문 | 기존 팀 구현에 성장 예측일, 예약 정원 잠금, 중복 취소 방지 추가 | 실제 텃밭과 농장 연결, 정원과 취소 잠금의 MariaDB 검사, 관리사 가능 시간. 로컬 시연은 실제 예약 API 호출 가능 |
| #15 알림 | PR #24의 기존 구현 유지, 돌봄과 성장 알림에서 공통 서비스 호출 | 실제 저장과 읽기 요청 검증. 새로 구현한 것으로 중복 보고하지 않음 |

## 프론트 연동 기준

1. 주소 접두어는 없다. `{ data, error: { code, message } }`를 사용한다. 음성 응답은 HTTP 200이어도 `processingStatus`가 실패일 수 있다. `READY` 또는 원문이 있는 대체 결과를 관리사가 확인한 후 확정한다.
2. 음성 업로드의 multipart 필드는 `audio`, 사진은 `photo`다. `voiceMemoId`를 받은 뒤 확정하고, 완료 요청에 `photoId`, `voiceMemoId`를 전달한다. 확정만으로 작업이 완료되지 않는다.
3. 업로드 응답이 끊기면 `/caretaker/tasks/{taskId}/voice-memos/latest`를 먼저 조회한다. `retryable`을 기준으로 재시도한다. 처리 만료는 기본 2분, HTTP 읽기 제한 90초 환경에서는 3분 30초다. 네트워크 오류가 났다는 이유만으로 새 녹음을 자동 재업로드하지 않는다.
4. 가족 일기는 `/gardens/{gardenId}/diary?filter=ALL&limit=20`이다. `PHOTO`, `STAGE` 필터를 지원한다. `transcript`와 `summary`를 따로 표시하고 `nextCursor`의 dry_ 값을 그대로 다음 요청에 넣는다. 원문이나 사진이 없는 항목은 null이다.
5. 이상 징후는 LEAF_SPOT, YELLOW_LEAF, WILTING, PEST_SUSPECTED, STEM_DAMAGE 코드다. [voice-api.md](voice-api.md)의 한글 문구로 표시한다. 원문 근거가 있는 관찰이며 병명 확진이 아니다. 관리사가 정리문을 수정하면 기존 자동 태그를 비운다.
6. 사진 분석 실패도 사진 업로드는 유지한다. `aiCheck=null`을 일치 판정으로 표시하지 않는다. 모델 출력만으로 완료하거나 재작업을 강제하지 않는다.
7. `/tts`는 공급자가 없어 C502다. 팀 프론트는 브라우저 읽어주기 대체를 유지해야 한다. 로컬 시연 콘솔의 읽어주기도 브라우저 기능이다.
8. 주인 홈, 응원, 하트 보내기, 게임 보상, 신규 주인 프로필의 첫 작업과 첫 일기 연결은 다른 도메인과 합의가 필요하다. 가족 일기 조회가 이 기능 전체의 완료를 의미하지 않는다.

## 실제 팀 도메인에 필요한 연결

| 접점 | 구현해야 할 값 또는 동작 |
| --- | --- |
| `CareGardenReader` | 실제 텃밭 ID, 주인과 관리사 로그인 아이디, 작물, 시작일, 격자, 검증된 작업 자료와 성장 기준. 변경 조회는 같은 트랜잭션에서 실제 텃밭 행 잠금 |
| `CaretakerFarmReader` | 등록 가능한 실제 농장 ID와 이름 |
| `OwnerSummaryReader` | 실제 주인 닉네임, district, 연결된 텃밭 ID. `/users/me` 주인 요약에 사용 |
| `HarvestGardenReader` | 소유권, 농장과 위치, 담당자, 실제 수확 가능 여부와 돌봄 수 |
| `GrowthWeatherReader` | 실제 일평균 기온 이력. 시연의 합성 기온을 운영에 사용하지 않음 |
| 주인 프로필 등록 | 등록 완료 후 첫 작업 생성, 첫 가족 일기의 의미와 생성 시점 연결. 현재 local-demo 시드와 별개 |

## 실행과 검사 순서

1. Docker Desktop이 준비되면 [local-demo.md](local-demo.md)의 시작 스크립트를 실행한다. 전용 가상 농장과 계정을 생성하며, 운영 DB와 연결하지 않는다. 실제 녹음으로 원문과 정리문을 확인하고 가족 일기까지 연결되는지 먼저 검사한다.
2. 로컬 LLM 품질은 선택 평가 명령으로 측정한다. 12개의 합성 한국어 문장으로 이상 징후의 정확한 코드 집합, 부정문과 지시문 처리, 대체 경로 수, 지연을 확인한다. STT와 요약의 의미 정확도, 실제 농업 성능을 측정하는 벤치마크는 아니다.
3. 새 환경의 전체 컴파일과 선별 테스트는 다음 명령으로 실행한다. 이 작성 환경에서는 Gradle 다운로드 제한으로 전체 명령의 성공을 확인하지 못했다.

```powershell
.\gradlew.bat compileJava
.\gradlew.bat test --tests 'community.likelion.growwithusserver.ai.*' --tests 'community.likelion.growwithusserver.voice.*' --tests 'community.likelion.growwithusserver.care.*' --tests 'community.likelion.growwithusserver.growth.*' --tests 'community.likelion.growwithusserver.caretaker.*' --tests 'community.likelion.growwithusserver.diary.*' --tests 'community.likelion.growwithusserver.harvest.*' --tests 'community.likelion.growwithusserver.demo.LocalDemoDataTest'
python -m unittest discover -s local-ai -p 'test_*.py'
python -m unittest discover -s scripts -p test_local_demo.py
node --test scripts/test-local-demo-ui.cjs
```

4. 운영 스키마에는 `voice_memo.sql`, `care_growth.sql`, `diary.sql`, `visit_booking_day.sql`을 검토해 적용한다. 이미 있는 테이블은 누락 필드와 인덱스만 별도 마이그레이션한다. prod는 validate이며 자동 적용하지 않았다.
5. 실제 DB에서 동시 완료, 확정과 완료 경쟁, 중복 일기 방지, 일기 또는 알림 저장 실패 시 원래 처리 롤백을 확인한다. 대역 저장소 검사만으로 DB 잠금과 트랜잭션이 검증된 것은 아니다.
6. 배포 주소가 준비되면 HTTPS, 프론트 CORS, 실제 파일 접근, JWT 갱신, Android WEBM과 iPhone MP4를 확인한다. 로컬 Compose는 외부 Vercel 화면에 공개된 HTTPS 서버가 아니다.

실제 모델 음성 추론은 실패했으며 원인은 진단 중이다. Docker 부팅과 기본 HTTP 검사는 위 기록대로 통과했다. 운영 DB와 팀 프론트 연동은 미검증이다. 합성 평가 보고서는 실행 전에는 생성하지 않는다. 원격 업로드는 5f11d5c까지 확인했으며 이후 로컬 커밋은 추가 push가 필요하다.


## 예약과 취소 추가 사항

1. 마지막 자리의 경쟁 요청을 직렬화하기 위해 visit_booking_day 테이블을 추가했다. 운영 DB에 새 잠금 테이블을 준비해야 하며 기존 visit 행은 그대로 읽는다. 기본 시연은 ddl-auto=update이므로 전용 DB에서 자동 생성한다.
2. 취소는 예약 행 잠금을 사용한다. 중복 취소는 기존 C4095이고 알림을 다시 만들지 않는다. 실제 DB에서 알림과 예약이 같이 롤백되는지 확인해야 한다.
3. 로컬 콘솔에는 방문 슬롯, 1명 예약, 해당 화면에서 받은 예약 ID로 취소하는 경로를 추가했다. 새로고침 후 예약 목록과 생성 응답 유실 복구는 제공하지 않는다. 팀 프론트의 예약 취소 우선순위를 올린다는 의미는 아니다.
4. 현재 검사 수는 핵심 Java 78개, Python 32개, 화면 대역 검사 10개다. 예약 서비스 전체 테스트와 DB 동시 실행은 이 환경에서 미검증이다.
