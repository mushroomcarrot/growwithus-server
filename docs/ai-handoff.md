# AI 기능 인수인계

2026-11-13, 작업 브랜치 `feat/seungyoon-ai` 기준이다. main과 develop에 병합하지 않았다. 현재 기준 develop은 `0f025dc`이며 최신 팀 코드와 통합 검사는 접근 권한 복구 뒤 진행해야 한다.

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

현재 실제 모델 추론, Docker 부팅, 운영 DB, 팀 프론트 연동 결과는 없다. 합성 평가 보고서도 실행 전에는 생성하지 않는다. GitHub 업로드가 성공하기 전에는 팀이 이 변경을 원격에서 받을 수 없으므로 브랜치 푸시 여부를 별도로 확인해야 한다.


## 예약과 취소 추가 사항

1. 마지막 자리의 경쟁 요청을 직렬화하기 위해 visit_booking_day 테이블을 추가했다. 운영 DB에 새 잠금 테이블을 준비해야 하며 기존 visit 행은 그대로 읽는다. 기본 시연은 ddl-auto=update이므로 전용 DB에서 자동 생성한다.
2. 취소는 예약 행 잠금을 사용한다. 중복 취소는 기존 C4095이고 알림을 다시 만들지 않는다. 실제 DB에서 알림과 예약이 같이 롤백되는지 확인해야 한다.
3. 로컬 콘솔에는 방문 슬롯, 1명 예약, 해당 화면에서 받은 예약 ID로 취소하는 경로를 추가했다. 새로고침 후 예약 목록과 생성 응답 유실 복구는 제공하지 않는다. 팀 프론트의 예약 취소 우선순위를 올린다는 의미는 아니다.
4. 현재 검사 수는 핵심 Java 68개, Python 28개, 화면 대역 검사 10개다. 예약 서비스 전체 테스트와 DB 동시 실행은 이 환경에서 미검증이다.
