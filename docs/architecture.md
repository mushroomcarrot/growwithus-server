# 현재 구조

마지막 갱신: 2026-11-13, 시연 단계 선택, 새싹 관찰, 홈과 수확 안내 및 관리사 요약 응답 확장 (#36).

## 화면 연동 응답 확장 (#36)

1. PUT /demo/stage는 ADMIN 토큰과 X-Demo-Mode 헤더가 필요하다. 요청은 stage (SEED, SPROUT, FLOWER, FRUIT)이고 응답은 빨리 감기와 같은 DemoFastForwardResponseDto (stage, eventId, expectedHarvestDate)다. stage가 없거나 enum이 아니면 C002다. minji, 시연 농장 demo:theme, B-14가 모두 일치하는 텃밭만 변경한다.
2. 현재 단계보다 높은 단계를 고르면 EvolutionEventService.advanceStage로 올리고 새 진화 이벤트의 eventId를 반환한다. 낮은 단계는 대상 검증을 하는 resetDemoStage(gardenId, target)로 내리며 eventId는 null이다. 같은 단계는 이벤트를 만들지 않는다. 단계가 바뀔 때는 이전의 미확인 연출을 먼저 닫는다. 이미 지급한 XP, 배지와 기록은 삭제하지 않는다.
3. 선택한 단계는 garden_growth_state.demo_start_stage에 저장하고 demo_days를 0으로, 관찰 보정을 비운다. 시연 성장 계산은 이 시작 단계를 기준으로 하므로 성장 조회, 배치, 빨리 감기 후에도 선택한 단계 아래로 되돌아가지 않는다. demo_start_stage가 null인 기존 시연 상태는 FLOWER로 본다. 단계 사이는 18일이며 SEED에서 FRUIT까지는 54일이다. expectedHarvestDate는 현재 날짜에 FRUIT까지 남은 시연 일수를 더한 날짜다.
4. POST /demo/reset은 demo_start_stage도 FLOWER로 되돌린다. 초기화 시점의 단계가 FLOWER보다 낮으면 advanceStage로 올린 뒤 그 연출을 닫는다.
5. POST /gardens/{gardenId}/stage-checks의 observed에 SPROUT을 추가했다. GrowthCalculator는 SPROUT 관찰도 보정 기준으로 받는다. SEED는 관찰 값이 아니며 시연 시작 단계로만 쓴다. stageCheck.required의 조건은 그대로이며 FLOWER와 FRUIT 예상 단계에서만 true다. SPROUT은 관리사가 화면에서 직접 선택해 보낸다.
6. GET /gardens/{gardenId}/harvest 응답에 stage를 추가했다. 텃밭의 현재 단계이며 harvestable (관리사의 FRUIT 확인)과는 별개다. HarvestGarden에도 stage를 추가했다.
7. GET /gardens/{gardenId}/home 응답에 farmName, plotCode, nickname을 추가했다. nickname은 주인 프로필의 별명이고 프로필이 없으면 사용자 이름이다.
8. GET /caretakers/me/summary 응답에 heartsReceivedToday를 추가했다. 조회한 month와 관계없이 서버 날짜의 오늘 00:00부터 내일 00:00 전까지 받은 하트 수다.
9. prod는 db/manual/demo_simulation.sql의 demo_start_stage 열 추가를 먼저 적용한다. ADD COLUMN IF NOT EXISTS이므로 다시 실행해도 안전하다.
10. 검증: ./gradlew compileJava unitTest와 build -x test가 통과했다. DB가 필요한 전체 test와 실제 HTTP 호출은 이번 작업에서 실행하지 않았다.

## 담당 이슈 후속 구현과 검증

1. dami6326 담당 #3, #4, #9, #10, #11은 기존 구현을 유지한다. 실제 HTTP에서 관리사 완료 기록의 주인 홈과 일기 연결, 감사 하트와 중복 방지, 퀘스트, 리그, 배지를 확인했다. 이벤트 보상은 텃밭을 먼저 잠근 뒤 이벤트를 잠그도록 순서를 통일했다.
2. #16의 POST /demo/fast-forward는 days 1~365를 받아 누적 최대 3650일까지 저장한다. minji, 시연 농장 demo:theme, B-14가 모두 일치하는 텃밭만 변경한다. 최초 시작은 FLOWER만 허용하며 다른 단계는 먼저 reset한다.
3. garden_growth_state.demo_days가 null이면 실제 기상 계산을 유지한다. 시연에서는 저장된 시작 단계 (기본 FLOWER)부터 단계마다 18일에 다음 단계에 도달하는 합성 일별 기온을 GrowthCalculator에 전달한다. 실제 기상 테이블은 변경하지 않는다. 성장 조회와 배치도 저장된 시연 일수를 사용하므로 다시 조회해도 단계가 되돌아가지 않는다. 응답 explanation에 시연 자료임을 명시한다.
4. 단계 상승은 기존 GrowthProjection과 EvolutionEventService.advanceStage를 통해 주인 이벤트, 일기와 알림에 연결한다. 자동 FRUIT만으로 관리사의 수확 확인을 대신하지 않는다. 수확 예정일은 현재 날짜에 남은 시연 일수를 더해 반환한다.
5. GET /demo/state는 실제 단계, 단계 순서(3/4), 누적 일수와 현재 미완료 작업이 보이는 시연 텃밭 수를 반환한다. 조회 자체는 작업을 생성하지 않는다.
6. POST /demo/reset은 이전 시연 작업을 보관하고, 미확인 연출을 닫고, 주 시연 텃밭을 FLOWER 시작 단계와 0일로 되돌린 뒤 맑음 상태의 작업을 다시 생성한다. 시연 단계 하향은 대상 검증을 다시 하는 EvolutionEventService.resetDemoStage에만 허용한다. 이미 지급한 XP, 배지, 돌봄 기록과 사진, 음성 참조는 삭제하지 않는다.
7. care_task.archive_number는 현재 작업이면 0, 보관된 작업이면 자신의 PK다. 유니크 키는 garden_id, task_date, task_type, archive_number다. 오늘 목록과 신규 생성의 존재 검사는 현재 작업만 조회한다. 작업 생성의 반복 제한 및 음성 입력에서는 이전 시연 회차를 제외하며 돌봄 이력과 통계는 유지한다. 보관된 작업의 사진 업로드와 완료는 C4095로 거부한다.
8. 초기화는 관리사 날짜 행, 모든 작업 행, 텃밭 행 순서로 잠근다. DB 초기화와 재생성은 한 트랜잭션이다. 날씨 덮어쓰기는 기존 Redis 저장소이므로 DB와 분산 트랜잭션은 아니다.
9. GardenDemoSeeder는 기존 minji/B-14 외에 demo_owner_b13/B-13, demo_owner_b15/B-15를 준비한다. demo.enabled와 8자 이상의 DEMO_SEED_PASSWORD 조건을 유지하며 이미 분양되었거나 다른 관리사가 맡은 칸은 덮어쓰지 않는다. 세 칸이 모두 준비되어야 할 일 3곳이 가능하다.
10. PUT /demo/weather는 세 시연 텃밭의 기존 생성기와 날씨 검증을 재사용한다. regeneratedTasks는 새 작업이 추가된 텃밭 수이며 기존 작업이 충분하면 0이다. 날씨에 맞지 않는 기존 작업은 화면과 완료 처리에서 제외된다.
11. #14의 DemoVisitSeeder는 DEMO_ENABLED=true와 DEMO_SEED_VISIT_ENABLED=true일 때만 실행한다. 18일 뒤 이후 첫 토요일의 빈 슬롯에 기존 VisitService로 예약하고, 향후 31일 이내 같은 시연 텃밭 예약이 있으면 추가하지 않는다. 실제 예약 ID는 기존 vst_숫자 규칙이며 과거 날짜나 고정 ID를 강제로 넣지 않는다.
12. prod는 db/manual/demo_simulation.sql을 먼저 적용해야 한다. 성장 일수 열, 시연 시작 단계 열, 작업 보관 번호와 유니크 키 교체를 포함한다. 신규 환경의 ddl-auto=update는 스키마를 생성하지만 기존 유니크 키 삭제는 수동 SQL이 필요하다.
13. ./gradlew unitTest는 외부 DB 없는 검사다. 전체 test에는 기존 DB 컨텍스트 및 영속성 검사도 포함된다. 격리된 로컬 MariaDB와 Redis로 전체 테스트 262개, DB 없는 단위 테스트 258개와 build가 통과했다. 실제 HTTP에서 권한, 잘못된 일수, 세 텃밭, 비 예보, 9일 두 번의 진화, 중복 보상 방지, 반복 초기화, 보관 작업 거부와 이력 보존을 확인했다. 방문 시드는 재시작 후에도 한 건이었다. 운영 배포와 프론트 브라우저, 실제 사람 음성 검증과는 별개다.

### 남는 운영 조건

1. 실제 농장의 칸, 관리사 방문 가능 시간과 실제 과거 기상 자료는 운영 데이터가 필요하다. 임의의 실농장 자료를 생성하지 않았다.
2. 운영 스키마 적용, 인증키, HTTPS와 프론트 연동은 운영 환경에서 확인해야 한다.
3. JungSeungYoon 담당의 실제 AI 공급자, 서버 TTS, AI 아바타 및 사람 음성 품질 검증은 이번 담당 이슈 범위에 포함하지 않았다.
4. 초기화는 재시연을 위한 작업 회차와 성장 상태 초기화다. 사용자의 과거 기록과 보상까지 삭제하는 DB 전체 재적재는 수행하지 않는다.

## PR #32 이후 마무리 구현

CropGuidanceService가 실제 작물별 작업 지침을 읽고 내부 키 전용 API로 교체한다. 토마토 기본 지침의 내용은 용인시 농업기술센터를 참고하며 시간과 간격은 편성 기본 정책으로 구분한다. 저장한 빈 지침은 기본값을 끈 상태로 유지한다. GardenCareReader가 이 지침을 기존 AI 작업 생성과 최종 검증에 전달한다.

DailyWeatherImportService는 출처가 있는 과거 일별 기온을 검증해 원자적으로 저장한다. 관측 일자료는 이후 예보 집계에서 보호한다. 가족 수와 보낸 하트를 사용자 응답에 연결하고 예약 인원 생략 시 가족 수를 사용한다. 프로필 등록 후 첫 작업은 커밋 뒤 모델 없이 초기화한다.

compose.ai-workers.json은 Linux 동일 서버용 AI 작업자만 포함한다. 가상 계정이나 시연 DB는 없으며 host 네트워크에서 AI 서비스는 loopback에만 바인딩한다. 실제 서버 배포는 미실행이다. API, 운영 정책과 추가 스키마는 ai-completion.md와 db/manual/ai_completion.sql을 따른다.

Gradle 테스트 JVM도 앱 시작과 같은 Asia/Seoul 시간대를 사용한다. Docker의 기본 UTC로 실행할 때 기상 API 시각 검사가 Z를 반환하던 환경 차이를 맞췄다. 기대값을 바꾸거나 테스트를 제외하지 않는다. 첫 통합 실행은 234개 중 233개 통과였으며, 실제 도메인 연결 검사와 두 애플리케이션 컨텍스트는 통과했다. 시간대 수정 뒤 전체 재실행 결과는 별도로 확인한다.

## develop 6d8f52b 통합

팀의 농장, 주인, 일기, 진화, 게임 코드를 보존해 작업 브랜치에 통합했다. GardenCareReader가 실제 텃밭과 소유권, 관리사, 작물 GDD 기준을 연결하고 변경 조회는 기존 트랜잭션의 실제 텃밭 행을 잠근다. 작업 지침 자료는 아직 연결하지 않아 안전한 CHECK 대체를 사용한다.

StoredGardenCareLogReader가 관리사 돌봄 기록을 팀의 홈, 일기, 감사 하트에 연결한다. GardenCareLog와 운영 일기 DTO에 transcript를 추가해 정리문과 구분한다. 관리사 요약은 팀의 감사 하트 테이블을 읽는다. 농장과 관리사 프로필 조회도 실제 저장소에 연결했다.

성장 계산은 GrowthProjection을 통해 실제 Garden의 단계, GDD와 예상 수확일을 갱신한다. 단계 상승은 팀의 EvolutionEventService를 호출하며 관리사 확인 응답에는 실제 주인 진화 이벤트 ID를 반환한다. 운영 알림도 그 서비스가 담당해 중복 전송하지 않는다. local-demo는 기존 격리 자료와 알림을 유지한다.

운영 일기는 팀의 날짜 커서, REST_DAY, 감사 하트 기능을 유지한다. local-demo에서만 LocalSnapshotDiaryService와 별도 컨트롤러가 기존 dry_ 커서를 사용한다. 두 컨트롤러는 프로필로 분리해 같은 경로가 중복 등록되지 않는다. 역할 변경 시 주인과 관리사 모두 기존 프로필 등록 여부를 복구한다.

통합 검사 결과는 build/reports/merge-verification.log와 JUnit XML에 기록한다. Docker Java 21의 전체 컴파일과 테스트, 로컬 시연 DB를 사용하는 contextLoads가 통과해야 머지 절차를 진행한다. 이전 핵심 78개 통과 결과와 새 통합 검사 결과를 구분한다.

배포 우선순위와 실제 도메인 연결 조건은 ai-release.md에 정리했다. .env.prod.example은 기존 서버에서 Spring과 같은 네트워크 공간의 AI 작업자를 사용하는 설정 초안이다. 외부 제공자 API 키만으로 연결되는 구현은 없으며 서버 배치와 실제 텃밭 연결을 확정해야 배포할 수 있다. 운영용 Compose나 실제 서버 반영은 아직 수행하지 않았다.

RUN-AI-EVALUATION.cmd는 evaluation 프로필의 일회성 컨테이너에서 기존 evaluateLocalAi Gradle 태스크를 실행한다. Java 실제 HTTP 클라이언트와 정리 서비스를 사용하며 DB 작업은 하지 않는다. 합성 입력별 원본 모델 응답을 local-ai-evaluation.json의 modelReplies에 함께 기록한다. 원본 응답이 없는 경우는 통신 단계 실패와 구분해 살펴본다. 일반 빌드나 테스트에서 이 모델 평가는 자동 실행하지 않는다.

CHECK-LOCAL-LLM.cmd는 기존 진단 스크립트의 CheckLlm 모드로 Ollama 통신과 응답을 확인한다. 실제 Java 정리 프롬프트를 읽고 공개 합성 문장만 전송한다. 내부 AI 포트를 외부에 공개하지 않고 STT 컨테이너의 공유 네트워크를 이용한다. 진단 결과는 로컬 보고서에 기록하고 DB나 작업은 변경하지 않는다.

STT 이미지는 faster-whisper 1.2.1과 PyAV 18 계열을 사용한다. PyAV 19에서 삭제된 metadata_errors 인자로 음성 해독이 실패하는 조합을 제외한다. 이미지 빌드 때 check_audio_decoder.py가 모델 다운로드 없이 생성한 PCM WAV를 실제 faster-whisper 해독기로 읽고 샘플 수를 확인한다. 실패하면 이미지 빌드도 중단한다. FIX-LOCAL-STT.cmd는 STT만 재빌드하고 교체한 뒤 상태와 한국어 합성 음성을 검사한다. 모델 볼륨과 DB는 보존하며 나머지 서비스는 재시작하지 않는다.

로컬 STT 진단에는 `CHECK-LOCAL-STT.cmd`와 `scripts/diagnose-local-stt.ps1`을 사용한다. 기존 STT 컨테이너에서 공개 합성 음성으로 모델을 직접 실행하고 비밀번호나 사용자 녹음 없이 진단 보고서를 기록한다. 일반 Windows 계정의 Docker 접근 권한을 사용하며 작업 기록을 변경하지 않는다. 기본 서버 HTTP 검사와 실제 음성 처리 성공 여부는 별도로 기록한다. 합성 WAV는 `src/test/resources/ai-evaluation/`에 정답 문장과 생성 정보를 함께 보관한다.

> 코드의 현재 상태를 그대로 기록한 문서다. 기능을 추가하거나 바꾸면 이 문서도 함께 갱신한다 ([development-guide.md](development-guide.md#문서-갱신-규칙)).
>
> 마지막 갱신: 2026-11-13, 원격 작업 브랜치 업로드 확인, Windows 사용자 실행용 설치와 시연 시작 도구 추가. 실제 모델과 DB 부팅, 전체 빌드는 미검증

## 한눈에 보기

| 항목 | 내용 |
| --- | --- |
| 언어 / 런타임 | Java 21 |
| 프레임워크 | Spring Boot 4.1.2 (webmvc, data-jpa, security, validation, data-redis) |
| 빌드 | Gradle (Groovy DSL, `build.gradle`) |
| DB | MariaDB (`MariaDBDialect`) |
| 캐시 / 토큰 저장소 | Redis |
| 인증 | JWT (jjwt 0.12.6, HS256), Stateless |
| API 문서 | springdoc-openapi 3.0.3 (`/swagger-ui.html`) |
| 베이스 패키지 | `community.likelion.growwithusserver` |
| 시간대 | JVM 기본 `Asia/Seoul` (`GrowwithusServerApplication.main`에서 `TimeZone.setDefault`), Jackson도 `Asia/Seoul` |
| 외부 호출 | `RestClient` (JDK `HttpClient`, 연결 3초, 읽기 30초 타임아웃) |
| 스케줄러 | `@EnableScheduling` (`scheduler.enabled`로 끌 수 있음) |
| 앱 클래스 | `GrowwithusServerApplication` |

## 패키지 구조

```
community.likelion.growwithusserver/
├── GrowwithusServerApplication.java  # main()에서 JVM 기본 시간대를 Asia/Seoul로 고정
├── common/                         # 공통 모듈 (도메인에 의존하지 않음, 예외: JwtTokenProvider가 UserRepository 참조)
│   ├── ai/
│   │   ├── LlmClient, SttClient, TtsClient  # AI 호출 공통 인터페이스
│   │   ├── AiProperties            # ai.llm, ai.stt, ai.tts 설정 (apiKey, baseUrl, model, voice)
│   │   ├── AiClientConfiguration   # 로컬 LLM 활성화 시 Ollama, 그 외 기본 빈은 AiClientException
│   │   ├── OllamaLlmClient, LocalLlmProperties # loopback HTTP 전용, 기본 비활성화
│   │   ├── LocalWhisperSttClient, LocalSttProperties, LocalAiAddress # 로컬 음성 인식 연결과 공통 주소 검증
│   │   └── AiClientException       # C502
│   ├── file/
│   │   ├── FileUploadService       # uploadImage / uploadAudio: 검증 후 저장, URL 반환
│   │   ├── FileStorage             # 저장소 추상화 (store)
│   │   ├── LocalFileStorage        # 로컬 디스크 구현 (file.storage=local)
│   │   ├── FileWebConfiguration    # 로컬 저장 파일을 /uploads/**로 제공
│   │   ├── AudioDurationReader     # wav, m4a(mp4) 헤더에서 재생 길이 읽기
│   │   └── exception/              # FileNotSupportedException (C4001), FileUploadFailedException (C5001)
│   ├── http/
│   │   └── RestClientConfiguration # externalRestClient 빈 (타임아웃 설정)
│   ├── id/
│   │   ├── IdPrefix                # 외부 ID 형식 (usr_12): format / parse
│   │   └── InvalidIdException      # C002
│   ├── pagination/
│   │   ├── CursorPageRequest       # 쿼리 cursor, limit (기본 20, 1~50)
│   │   └── CursorPageResponse<T>   # {items, nextCursor}
│   ├── scheduler/
│   │   └── SchedulingConfiguration # @EnableScheduling
│   ├── time/
│   │   └── DateTimes               # LocalDateTime을 +09:00 OffsetDateTime으로 변환
│   ├── redis/
│   │   ├── RedisConfiguration      # RedisTemplate<String, Object>, 키와 값 모두 String 직렬화
│   │   └── RedisService            # setValues / getValues / deleteValues / hasKey
│   ├── response/
│   │   ├── GlobalResponse<T>       # 모든 응답 래퍼 {data, error}
│   │   ├── GlobalErrorResponse     # {code, message}
│   │   ├── ErrorCode               # 에러 코드 enum
│   │   ├── GlobalException         # 모든 비즈니스 예외의 부모 (abstract)
│   │   └── GlobalExceptionHandler  # @RestControllerAdvice
│   ├── security/
│   │   ├── SecurityConfiguration   # 필터 체인, 경로 권한, CORS, RoleHierarchy, PasswordEncoder
│   │   ├── JwtTokenProvider        # 토큰 발급, 검증, 블랙리스트, Redis RT 저장
│   │   ├── JwtAuthenticationFilter # Authorization 헤더를 읽어 SecurityContext에 등록
│   │   ├── InternalServiceKeyFilter # /internal/**: X-Service-Key 검증 후 ROLE_SYSTEM 부여
│   │   ├── DemoModeFilter          # /demo/**: X-Demo-Mode: true 검증 (권한은 부여하지 않음)
│   │   ├── SecurityErrorWriter     # 필터 단계의 GlobalResponse 에러 응답 작성
│   │   ├── JwtAuthenticationEntryPoint  # 401 응답 (C4011)
│   │   ├── JwtAccessDeniedHandler  # 403 응답 (C403, 역할 미선택 사용자는 C4031)
│   │   ├── SecurityUtil            # getUsername(): 현재 사용자 로그인 아이디
│   │   ├── AuthenticationToken     # {accessToken, refreshToken, expiresIn}
│   │   └── exception/
│   │       ├── TokenInvalidException        # C4013
│   │       └── AuthenticationInfoException  # C4011, SecurityUtil.getUsername() 실패 시
│   └── swagger/
│       └── SwaggerConfiguration    # OpenAPI 정보 + bearerAuth 스킴
├── user/                           # 회원 / 인증 도메인
│   ├── controller/    AuthController (/auth), UserController (/users)
│   ├── service/       AuthService (가입, 로그인, 재발급, 로그아웃), UserService (사용자 조회, nextStep, 토큰 발급, 역할)
│   └── data/
│       ├── dto/request/   SignUpRequestDto, LoginRequestDto, RefreshRequestDto, RoleChangeRequestDto
│       ├── dto/response/  AuthResponseDto, TokenRefreshResponseDto, UsernameCheckResponseDto, UserMeResponseDto, RoleChangeResponseDto
│       ├── entity/        User (implements UserDetails)
│       ├── enums/         UserRole, NextStep
│       ├── exception/     UsernameDuplicationException, PasswordInvalidException, UserNotFoundException, RoleNotAllowedException
│       └── repository/    UserRepository
├── notification/                   # 알림 저장, 조회 (기기 푸시 없음)
│   ├── controller/    NotificationController (/notifications)
│   ├── service/       NotificationService (send, 목록, 읽음)
│   └── data/
│       ├── dto/           NotificationSendDto, NotificationItemDto, NotificationListResponseDto, NotificationReadResponseDto
│       ├── entity/        Notification
│       ├── enums/         NotificationType
│       ├── exception/     NotificationNotFoundException
│       └── repository/    NotificationRepository
├── harvest/                        # 수확 안내, 주말 방문 예약, 나들이 추천
│   ├── controller/    HarvestController (/gardens/{gardenId}/harvest, visit-slots, outings), VisitController (/visits)
│   ├── service/       HarvestService, VisitService, HarvestGardenService (텃밭 조회와 소유 확인), VisitSlotPolicy (슬롯 규칙), OutingSeeder (시드), KoreanParticle
│   ├── garden/        HarvestGardenReader (텃밭 도메인과의 접점), HarvestGarden, HarvestGardenConfiguration (구현체가 없을 때의 기본 빈)
│   └── data/
│       ├── dto/           HarvestResponseDto, VisitSlotResponseDto, VisitRequestDto, VisitResponseDto, OutingResponseDto
│       ├── entity/        Visit, Outing
│       ├── enums/         VisitStatus, OutingType
│       ├── exception/     GardenNotFoundException, GardenAccessDeniedException, VisitNotFoundException, VisitAccessDeniedException, VisitAlreadyReservedException, VisitAlreadyCanceledException, VisitSlotFullException, VisitSlotNotBookableException, VisitCancelDeadlinePassedException
│       └── repository/    VisitRepository, OutingRepository
├── weather/                        # 기상청 날씨 수집, 저장, 조회
│   ├── controller/    WeatherSyncController (/internal/weather)
│   ├── service/       WeatherService (현재 날씨, 예보, 일별 집계, 저장), WeatherSyncService (수집 배치), WeatherConditionResolver (condition 규칙), DemoWeatherStore (시연 날씨 덮어쓰기)
│   ├── scheduler/     WeatherSyncScheduler (매시 15분)
│   ├── client/        KmaClient (기상청 호출, 재시도), KmaProperties (weather.kma 설정), KmaBaseTime (발표시각 계산), KmaItemConverter (응답을 시각별 한 건으로 묶기), KmaResponse
│   ├── farm/          WeatherFarmReader (농장 도메인과의 접점), WeatherFarm, WeatherFarmConfiguration (구현체가 없을 때 설정의 기본 격자를 주는 기본 빈)
│   └── data/
│       ├── dto/           CurrentWeatherDto, WeatherForecastData, WeatherSyncRequestDto, WeatherSyncResponseDto
│       ├── entity/        WeatherForecast, DailyWeather
│       ├── enums/         WeatherCondition
│       ├── exception/     KmaApiException
│       └── repository/    WeatherForecastRepository, DailyWeatherRepository
└── demo/                           # 시연 컨트롤 (지금은 날씨만)
    ├── controller/    DemoController (/demo)
    ├── service/       DemoService
    └── data/
        ├── dto/           DemoWeatherRequestDto, DemoWeatherResponseDto, DemoStateResponseDto
        └── exception/     DemoWeatherNotAllowedException
```

## 음성 메모 도메인

`voice/controller`, `service`, `data/{dto,entity,enums,exception,repository}`, `task` 패키지를 추가했다. `VoiceMemoService`는 파일과 모델 처리를 맡고, 트랜잭션이 필요한 초안 저장과 확정은 `VoiceMemoStore`가 맡는다. `TaskVoiceReader`는 `care_task`와 현재 텃밭 담당자를 검증한다. [voice-api.md](voice-api.md)에 재시도와 저장 구조를 정리했다.

`GET /caretaker/tasks/{taskId}/voice-memos/latest`는 업로드 응답 유실 시 작업 ID로 마지막 저장 메모를 복구한다. 현재 담당자를 확인한 뒤 본인의 메모 하나만 조회하며 모델 호출이나 저장은 하지 않는다. 미완료 작업의 PROCESSING이 2분 동안 끝나지 않으면 `retryable=true`를 반환한다. 실제 retry는 같은 작업과 메모를 잠그고 처리 토큰을 교체한다. 특정 업로드의 멱등 키를 대신하지는 않는다.

### 관리사 작업과 성장 상태

1. `care`는 `Task`, `TaskDay`, `TaskPhoto`, `CareLog`를 저장한다. 오늘 할 일 조회 시 미생성 작업을 만들며 담당자 전체의 하루 작업 시간을 120분으로 제한한다. 생성 저장 시 `TaskDay`와 텃밭 행을 잠그고 현재 날씨와 단계로 재검증한다. 작업 완료, 돌봄 기록과 알림은 한 트랜잭션이다.
2. `caretaker`는 프로필과 월별 요약, TTS 캐시를 제공한다. 기본 SVG를 사용하며 AI 아바타 생성은 후순위다. 내 정보의 관리사 요약에는 누적 돌봄 횟수를 포함한다.
3. `growth`는 `GrowthState`와 `GrowthEvent`를 저장한다. 관찰 보정과 완료된 날짜의 기온을 기준으로 재계산하고 단계 상승 알림을 보낸다. 물 주기 등의 단계 검증은 저장된 성장 상태를 우선한다. `GrowthStatusService`는 확인한 단계에서 같은 확인 요청을 반복하지 않으며 다음 주요 단계에서 다시 요청한다. NO_CHANGE는 보정하지 않아 요청이 유지된다. 관찰 값은 SPROUT, FLOWER, FRUIT, NO_CHANGE다.
4. `CareGardenReader`와 `CaretakerFarmReader`는 실제 팀 도메인 어댑터가 필요하다. 기본 구현은 빈 결과다. 실제 자료 없이 농장이나 작물 기준값을 생성하지 않는다. 일기 연결은 남아 있다. 기존 수확 API는 소유권 확인 후 저장된 성장 예측일을 우선 사용한다. 성장 예측일이 null이면 기존 날짜로 대신하지 않으며 수확 가능 여부는 실제 텃밭 데이터에서 가져온다.
5. 수동 스키마는 `src/main/resources/db/manual/voice_memo.sql`, `care_growth.sql`이다. prod 배포 전 적용 및 검증이 필요하다. 전체 연결 상태와 제한은 [ai-integration.md](ai-integration.md)를 기준으로 확인한다.
6. 예보나 단계 변경으로 기존 미완료 작업이 전부 숨겨지면, 기존 작업과 완료 기록을 유지한 채 남은 예산 안에서 관찰 작업을 하나 추가한다. 날짜별 담당자 잠금 안에서 재검사하며 같은 관찰 작업을 중복 생성하지 않는다. 모두 완료했거나 예산이 부족하면 추가하지 않는다.
7. 음성 분석 중 작업이 완료되어도 파일과 원문은 보존한다. 완료된 작업의 메모는 재시도 가능하다고 표시하지 않으며 미확정 메모를 뒤늦게 확정하거나 돌봄 기록에 자동 연결하지 않는다.
8. 작업 반복 방지 이력은 최소 14일과 안내 자료의 최대 반복 간격 중 긴 기간을 실제 완료 시각으로 조회한다. `care_task`의 `(garden_id, completed_at)` 인덱스를 사용한다. 예정일이 오래전인 작업도 최근 완료했다면 간격 검사에 포함된다. 이상 징후용 최근 7일 작업 조회와는 분리한다.

`VoiceMemo` 엔티티는 `voice_memo` 테이블에 작업 PK, 관리사 로그인 아이디, 음성 URL, 원문, 요약, 이상 징후 코드, 대체 처리 여부, 상태, 생성 및 확정 시각을 저장한다. prod DB 준비용 수동 SQL은 `src/main/resources/db/manual/voice_memo.sql`이며 아직 DB에 적용하지 않았다. 작업과 사용자 FK 및 파일 보상 삭제는 남아 있다.

## AI 핵심 로직

`ai/logic`은 DB와 모델 없이 실행할 수 있는 계산 코드다. `ai/service`는 기존 `LlmClient`, `SttClient`와 Jackson으로 입출력을 연결한다. 음성 정리는 위 음성 메모 API에 연결했고 작업 추천 및 성장 계산의 컨트롤러와 저장 서비스 연결은 남아 있다. 상세 연결 규칙은 [ai-integration.md](ai-integration.md)를 본다.

| 클래스 | 역할 |
| --- | --- |
| `TaskRules` | 단계별 재배 기준, 작업 이력, 비 예보와 관리사 잔여 시간에 따라 후보 검증. 근거가 없으면 관찰만 안내 |
| `TaskPlanningService` | LLM이 작업 종류를 선택하고 규칙을 적용. 오류 또는 모델 미연결 시 같은 규칙의 대체 결과 반환 |
| `GrowthCalculator` | 완료된 날짜의 기온으로 적산온도 재계산. 단계 확인을 기준으로 보정하고 누락 일수 표시. 단계 기준 수치는 호출자가 전달 |
| `VoiceMemoAnalysisService` | STT 후 JSON 요약과 이상 징후 코드 검증. LLM 실패 시 원문 유지, STT 실패는 C502로 전달 |
| `VoiceAnalysis.Issue` | 프론트에서 사용할 코드와 한글 문구 5개. 병명 진단이 아닌 관찰 분류 |

음성 메모 외의 작업 저장, 중복 생성 방지, 관리사 프로필, 사진 확인과 TTS 구현은 남아 있다. `local-ai/stt_server.py`는 같은 호스트에서만 쓰는 별도 작업 서버이며 공개 API가 아니다. 재배 자료 수집 및 검증, 실제 모델 설치와 녹음 시험도 필요하다.

## API

모든 응답은 `GlobalResponse<T>`로 감싸진다. 경로는 명세(`api-spec.md`)대로 접두어 없이 쓴다 (`/api/v1` 없음, 2026-11-11 결정).

| Method | Path | 권한 | 요청 | 응답 `data` | 설명 |
| --- | --- | --- | --- | --- | --- |
| POST | `/auth/signup` | 공개 | `SignUpRequestDto` | `AuthResponseDto` | 회원가입 후 바로 토큰 발급. 역할은 `USER`, `nextStep=ROLE_SELECT` |
| GET | `/auth/username-check?username={아이디}` | 공개 | 쿼리 `username` | `UsernameCheckResponseDto` | 아이디 중복 확인 |
| POST | `/auth/login` | 공개 | `LoginRequestDto` | `AuthResponseDto` | 로그인, `nextStep`으로 이동 화면 안내 |
| POST | `/auth/refresh` | 공개 | `RefreshRequestDto` | `TokenRefreshResponseDto` | RT로 AT, RT 재발급 (RT 로테이션) |
| POST | `/auth/logout` | USER, OWNER, CARETAKER | 헤더 `Authorization: Bearer <AT>` | `null` | AT, RT 블랙리스트 등록 및 RT 삭제 |
| GET | `/users/me` | USER, OWNER, CARETAKER | 없음 | `UserMeResponseDto` | 내 정보, 역할, `nextStep` |
| PUT | `/users/me/role` | USER, OWNER, CARETAKER | `RoleChangeRequestDto` | `RoleChangeResponseDto` | 역할 선택 및 변경. 이전 토큰을 무효로 만들고 새 토큰 발급 |
| GET | `/notifications?cursor=&limit=` | OWNER, CARETAKER | `CursorPageRequest` | `NotificationListResponseDto` | 내 알림 목록 (최신순)과 안 읽은 수 |
| PATCH | `/notifications/{notificationId}/read` | OWNER, CARETAKER | 경로 `notificationId` (`ntf_3` 또는 `all`) | `NotificationReadResponseDto` | 읽음 처리. `all`이면 모두 읽음. 이미 읽은 알림은 `readCount` 0 |
| GET | `/gardens/{gardenId}/harvest` | OWNER | 없음 | `HarvestResponseDto` | 수확 안내 (현재 단계, 예측 수확일, D-day, 함께 돌본 일수) |
| GET | `/gardens/{gardenId}/visit-slots?from=&to=` | OWNER | 쿼리 `from`, `to` (선택, `YYYY-MM-DD`) | `List<VisitSlotResponseDto>` | 주말 방문 슬롯과 예약 가능 여부. 생략하면 내일부터 2주 |
| POST | `/visits` | OWNER | `VisitRequestDto` | `VisitResponseDto` | 주말 방문 예약, 담당 관리사에게 알림 |
| DELETE | `/visits/{visitId}` | OWNER | 없음 | `null` | 방문 전날까지 취소, 담당 관리사에게 알림 |
| GET | `/gardens/{gardenId}/outings` | OWNER | 없음 | `List<OutingResponseDto>` | 농장에서 가까운 순으로 나들이 장소 최대 6곳 |
| GET | `/demo/state` | ADMIN + `X-Demo-Mode` | 없음 | `DemoStateResponseDto` | 시연 상태 (지금은 날씨만 채워짐) |
| PUT | `/demo/weather` | ADMIN + `X-Demo-Mode` | `DemoWeatherRequestDto` | `DemoWeatherResponseDto` | 시연 날씨 덮어쓰기 (SUNNY, RAIN, HOT) |
| POST | `/demo/reset` | ADMIN + `X-Demo-Mode` | 없음 | `DemoStateResponseDto` | 맑음, FLOWER, 가상 일수 0으로 초기화 |
| PUT | `/demo/stage` | ADMIN + `X-Demo-Mode` | `DemoStageRequestDto` (`stage`) | `DemoFastForwardResponseDto` | 시연 성장 단계 선택 (SEED, SPROUT, FLOWER, FRUIT) |
| POST | `/demo/fast-forward` | ADMIN + `X-Demo-Mode` | `DemoFastForwardRequestDto` (`days`) | `DemoFastForwardResponseDto` | 시연 시간 빨리 감기 |
| POST | `/internal/weather/sync` | SYSTEM (`X-Service-Key`) | `WeatherSyncRequestDto` (본문 생략 가능) | `WeatherSyncResponseDto` | 기상청 단기예보와 초단기실황 수집. 스케줄러가 매시 15분에 같은 로직을 돌린다 |
| POST | `/caretaker/tasks/{taskId}/voice-memos` | CARETAKER | multipart `audio` | `VoiceMemoResponseDto` | 음성 원문과 요약을 DRAFT로 저장. 실제 작업 조회 구현체가 없으면 C404 |
| POST | `/caretaker/voice-memos/{voiceMemoId}/confirm` | CARETAKER | `VoiceMemoConfirmRequestDto` 선택 | `VoiceMemoConfirmResponseDto` | 본인 초안 확정. 같은 확정 요청은 재시도 가능 |

텃밭 경로(`/gardens/{gardenId}/...`)와 `POST /visits`는 텃밭 도메인이 붙기 전까지 항상 `C404`다 ([알려진 이슈](#알려진-이슈--결정-대기)).

### 경로별 권한 (`SecurityConfiguration`)

`api-spec.md` 4장의 권한 열을 그대로 옮겼다. 아직 컨트롤러가 없는 도메인의 경로도 미리 매핑해 두었으므로, 새 도메인은 명세와 권한이 다를 때만 이 표와 코드를 고친다. 위에서부터 먼저 맞는 규칙이 적용된다.

| 순서 | 경로 | 권한 |
| --- | --- | --- |
| 1 | `/swagger-ui/**`, `/v3/api-docs/**`, `/swagger-resources/**`, `/webjars/**` | 공개 |
| 2 | GET `/uploads/**` | 공개 (로컬 저장소 파일) |
| 3 | `/internal/**` | SYSTEM (`X-Service-Key` 헤더) |
| 4 | `/demo/**` | ADMIN 토큰 + `X-Demo-Mode: true` 헤더 |
| 5 | POST `/auth/signup`, `/auth/login`, `/auth/refresh`, GET `/auth/username-check` | 공개 |
| 6 | `/auth/logout` | USER, OWNER, CARETAKER |
| 7 | `/users/me/badges` | OWNER |
| 8 | `/users/me/**` | USER, OWNER, CARETAKER |
| 9 | `/farms/*/plots/**` | USER, OWNER |
| 10 | `/farms/**`, `/crops/**` | USER, OWNER, CARETAKER |
| 11 | POST `/owners/me/profile` | USER, OWNER |
| 12 | `/owners/**` | OWNER |
| 13 | POST `/caretakers/me/profile` | USER, CARETAKER |
| 14 | `/caretakers/**` | CARETAKER |
| 15 | `/tts` | USER, CARETAKER |
| 16 | `/caretaker/**`, `/gardens/*/stage-checks` | CARETAKER |
| 17 | `/gardens/**`, `/care-logs/**`, `/events/**`, `/leagues/**`, `/quests/**`, `/quizzes/**`, `/visits/**` | OWNER |
| 18 | `/notifications/**` | OWNER, CARETAKER |
| 19 | 그 외 모든 경로 | 인증만 필요 |

ADMIN은 RoleHierarchy로 USER, OWNER, CARETAKER 권한을 갖는다. SYSTEM은 `UserRole`이 아니고 필터가 헤더를 확인해 부여하는 권한이라 ADMIN도 헤더가 있어야 한다. 시연 API는 ADMIN 토큰이 필요하고 헤더도 함께 있어야 한다.

### 내부 배치, 시연 API

| 경로 | 필터 | 통과 조건 | 실패 응답 | principal |
| --- | --- | --- | --- | --- |
| `/internal/**` | `InternalServiceKeyFilter` | `X-Service-Key` 헤더가 `internal.service-key`와 같음. 서버에 키가 비어 있으면 항상 거부 | 401 `C4014` | `system` (`ROLE_SYSTEM`) |
| `/demo/**` | `DemoModeFilter` | `demo.enabled=true`이고 `X-Demo-Mode: true` 헤더가 있음. 통과해도 권한을 주지 않으며 JWT의 ADMIN 권한이 따로 필요하다 | 403 `C4032` (토큰 없음 401 `C4011`, ADMIN 아님 403 `C403`) | JWT 사용자 (ADMIN) |

`/internal/**`에서는 JWT가 함께 와도 헤더 검증 결과가 인증을 대신한다. `/demo/**`는 헤더만으로는 통과할 수 없어서, 배포 후에도 ADMIN 계정 없이는 시연 데이터를 바꿀 수 없다. 가입 API로는 ADMIN을 만들 수 없으므로 시연 계정은 DB에서 직접 바꾼다 (`UPDATE user SET role = 'ADMIN' WHERE username = '...'`). `/demo`는 `DemoController`가 있고 (state, weather, reset), `/internal`은 `WeatherSyncController`(`/internal/weather/sync`)만 있다.

### DTO

| DTO | 필드 |
| --- | --- |
| `SignUpRequestDto` | `name` (`@NotBlank @Size(max=20)`), `username` (`@NotBlank`, 영문 소문자와 숫자 4~20자), `password` (`@NotBlank @Size(8~64)`) |
| `LoginRequestDto` | `username`, `password` (`@NotBlank`) |
| `RefreshRequestDto` | `refreshToken` (`@NotBlank`) |
| `RoleChangeRequestDto` | `role` (`@NotNull UserRole`, `OWNER`와 `CARETAKER`만 허용) |
| `AuthResponseDto` | `userId`, `accessToken`, `refreshToken`, `expiresIn` (초), `role`, `nextStep` (조립은 `AuthService.createAuthResponse` 한 곳에서만) |
| `TokenRefreshResponseDto` | `accessToken`, `refreshToken`, `expiresIn` (초) |
| `UsernameCheckResponseDto` | `username`, `available` |
| `UserMeResponseDto` | `userId`, `name`, `username`, `role`, `nextStep`, `owner` (`nickname`, `district`, `gardenId`), `caretaker` (`caretakerId`, `name`, `farmId`, `avatarUrl`) |
| `RoleChangeResponseDto` | `role`, `accessToken`, `refreshToken`, `nextStep` |
| `NotificationListResponseDto` | `items` (`NotificationItemDto` 목록), `unreadCount`, `nextCursor` |
| `NotificationItemDto` | `notificationId` (`ntf_3`), `type`, `title`, `body`, `target` (`screen`, `id`. 이동 대상이 없으면 `null`), `read`, `createdAt` |
| `NotificationReadResponseDto` | `readCount` (이번에 읽음 처리한 수), `unreadCount` (남은 수) |
| `HarvestResponseDto` | `stage` (텃밭의 현재 단계), `expectedDate`, `dDay` (지났으면 0, 수확일이 없으면 `null`), `harvestable`, `daysCaredTogether` (시작한 날을 1일로 셈), `careLogCount`, `caretakerName`, `message` |
| `VisitSlotResponseDto` | `slotId` (`slot_20261128_10`), `date`, `startTime`, `endTime` (`HH:mm`), `available` |
| `VisitRequestDto` | `gardenId`, `slotId` (`@NotBlank`), `headcount` (선택, `@Min(1)`, 생략 시 1), `memo` (선택, `@Size(max=200)`) |
| `VisitResponseDto` | `visitId` (`vst_5`), `date`, `startTime` (`HH:mm`), `status` (`RESERVED`, `CANCELED`) |
| `OutingResponseDto` | `type` (`EXPERIENCE_FARM`, `RESTAURANT`), `name`, `distanceKm` (농장 위치를 모르면 `null`), `address`, `description` |
| `WeatherSyncRequestDto` | `farmIds` (선택, `farm_3` 형식의 목록. 비어 있으면 등록된 모든 농장. 형식이 틀리면 C002) |
| `WeatherSyncResponseDto` | `syncedFarms` (실황과 예보를 모두 저장한 농장 수), `failedFarms` (하나라도 받지 못한 농장. `farm_3`, 기본 격자면 `grid_64_119`) |
| `DemoWeatherRequestDto` | `condition` (`@NotNull`, `SUNNY`, `RAIN`, `HOT`만 허용. 그 외는 C002) |
| `DemoWeatherResponseDto` | `condition`, `animation` (`sunny_happy`, `rain_umbrella`, `hot_sweat`), `weatherNotice` (작업 생성이 붙기 전까지 `null`), `regeneratedTasks` (작업 생성이 붙기 전까지 0) |
| `DemoStateResponseDto` | `weather` (덮어쓴 적이 없으면 `null`), `stage`, `stageProgress`, `fastForwardDays`, `todayTaskGardens` (뒤의 넷은 해당 도메인이 붙기 전까지 `null`) |
| `VoiceMemoResponseDto` | `voiceMemoId`, `transcript`, `summary`, `issues` (코드 목록), `status` (`DRAFT`) |
| `VoiceMemoConfirmRequestDto` | `summary` (선택, 1~1000자, 공백만 있는 값은 C002) |
| `VoiceMemoConfirmResponseDto` | `voiceMemoId`, `status` (`CONFIRMED`) |

`userId`는 `usr_` 접두어에 PK를 붙인 문자열이다 (예: `usr_12`, `User.getPublicId()`).

## 공통 모듈

### 외부 ID (`IdPrefix`)

명세의 접두어 ID 형식을 쓰기로 정했다 (2026-11-11). DB PK는 `Long`(IDENTITY) 그대로 두고, API로 주고받을 때만 `IdPrefix.XXX.format(id)`로 `접두어_숫자` 문자열을 만든다. 받을 때는 `IdPrefix.XXX.parse(publicId)`로 PK를 얻고 형식이 틀리면 `C002`다. 명세 예시의 `usr_minji`, `gdn_b14` 같은 값은 예시일 뿐이고 실제 값은 `usr_12`, `gdn_3`처럼 나간다.

| `IdPrefix` | 접두어 | `IdPrefix` | 접두어 |
| --- | --- | --- | --- |
| `USER` | `usr` | `CARE_LOG` | `cl` |
| `GARDEN` | `gdn` | `VOICE_MEMO` | `vm` |
| `FARM` | `farm` | `PHOTO` | `pht` |
| `CARETAKER` | `crt` | `EVENT` | `evt` |
| `TASK` | `tsk` | `VISIT` | `vst` |
| `NOTIFICATION` | `ntf` | | |

방문 슬롯 ID(`slot_20261128_10`)는 DB에 없는 계산 값이라 `IdPrefix`를 쓰지 않고 `VisitSlotPolicy`가 만들고 읽는다.

### 커서 페이지네이션

| 클래스 | 내용 |
| --- | --- |
| `CursorPageRequest` | 쿼리 `cursor`(생략 시 첫 페이지), `limit`(기본 20, 1~50, 위반 시 `C002`). `getFetchSize()`는 limit + 1, `getCursorId(IdPrefix)`는 커서를 PK로 변환 |
| `CursorPageResponse<T>` | `items`, `nextCursor`(마지막 페이지면 `null`). `of(limit + 1건 조회 결과, limit, 커서 추출 함수)`로 만들고 `map`으로 DTO 변환 |

`nextCursor`는 이번 페이지 마지막 항목의 외부 ID다. 사용 예시는 [development-guide.md](development-guide.md#커서-페이지네이션)를 본다.

### 파일 업로드

| 항목 | 내용 |
| --- | --- |
| 진입점 | `FileUploadService.uploadImage(MultipartFile, directory)`, `uploadAudio(MultipartFile, directory)`. 반환값은 업로드 URL. `validateAudio(MultipartFile)`로 저장 없이 음성을 먼저 검증할 수 있음 |
| 사진 검증 | 확장자 jpg, jpeg, png이고 파일 앞부분(매직 넘버)이 실제 JPEG, PNG여야 함. 10MB 이하 |
| 음성 검증 | 확장자 webm, m4a, mp4, wav이고 파일 앞부분이 실제 형식과 맞아야 함 (webm은 EBML 헤더, m4a와 mp4는 `ftyp` 박스, wav는 RIFF/WAVE). 헤더에서 길이를 읽으면 60초 이하 (wav는 `data` 크기와 초당 바이트 수, m4a와 mp4는 `mvhd` 박스). 길이를 읽을 수 없거나 0이면 (크롬 webm, 사파리 조각 mp4) 5MB 이하만 받는다. 60초 녹음 제한은 프론트도 건다 |
| 위반 시 | `C4001` (multipart 상한 초과도 `C4001`) |
| 저장 | 원본 이름 대신 `UUID.확장자`로 `FileStorage.store` 호출 |
| 저장소 | `FileStorage` 인터페이스. 현재 구현은 `LocalFileStorage` (`file.storage=local`): `file.upload-dir/{directory}/{파일}`에 저장하고 `file.base-url/uploads/{directory}/{파일}`을 돌려줌. S3는 구현체를 추가하고 `file.storage` 값으로 고른다 |
| multipart 상한 | 파일 20MB, 요청 25MB (`spring.servlet.multipart`) |

### 날짜와 시각 직렬화

- `LocalDate`는 `YYYY-MM-DD`, `OffsetDateTime`은 `2026-11-10T09:30:00+09:00` 형식으로 나간다 (`spring.jackson.time-zone=Asia/Seoul`, `write-dates-with-context-time-zone=true`).
- 다른 오프셋으로 들어온 시각도 +09:00으로 바꿔 읽고 쓴다.
- 엔티티의 `LocalDateTime`을 응답에 담을 때는 `DateTimes.toOffset(...)`으로 `OffsetDateTime`으로 바꾼다 (초 단위로 자름).

### 외부 API, AI 클라이언트

- `externalRestClient` (`RestClient`): 연결 타임아웃 `external.http.connect-timeout`(3초), 읽기 타임아웃 `external.http.read-timeout`(30초). 도메인 클라이언트는 `externalRestClient.mutate().baseUrl(...).build()`로 파생해서 쓴다.
- `LlmClient.complete(systemPrompt, userPrompt)`: 기본 빈은 `AiClientException`(`C502`)을 던진다. `AI_LOCAL_ENABLED=true`와 다운로드된 `AI_LOCAL_MODEL`을 지정하면 로컬 Ollama를 사용한다. `POST /api/chat`, JSON 출력, 비스트리밍 방식이다. loopback HTTP 주소만 허용하고 API 키를 전송하지 않는다. Ollama 서버도 `OLLAMA_NO_CLOUD=1`로 실행해야 한다.
- `SttClient.transcribe(audio, filename)`: `AI_LOCAL_STT_ENABLED=true`이면 `LocalWhisperSttClient`로 로컬 Python 서버의 `/transcribe`를 호출한다. 원본 파일 이름은 보내지 않는다. 최대 20MB, 서버 디코딩 길이 60초 제한. 공통 업로드가 길이를 읽지 못하는 파일은 기존 5MB 제한도 적용된다. 형식 및 길이 오류는 C4001, 무음과 모델 실패는 C502다. 기본 비활성화 상태는 C502다.
- `TtsClient.synthesize(text)`: 실제 구현체는 아직 없다. 기본 빈은 `AiClientException`(`C502`)을 던진다. 구현체를 `@Component`로 등록하면 기본 빈은 자동으로 빠진다.

### 스케줄러

`SchedulingConfiguration`이 `@EnableScheduling`을 켠다. `scheduler.enabled=false`면 `@Scheduled`가 돌지 않는다. 배치 작업 구조는 [development-guide.md](development-guide.md#배치-작업)를 본다. 등록된 배치는 날씨 수집(`WeatherSyncScheduler`, 매시 15분) 하나다.

### 알림 (`NotificationService.send`)

프론트가 웹이라 기기 푸시(FCM)는 보내지 않는다 (2026-11-11 결정). 알림은 DB에 저장하고 화면이 가져가는 방식이다.

1. 알림이 필요한 도메인은 `NotificationService.send(NotificationSendDto)` 하나만 호출한다. 사용 예시는 [development-guide.md](development-guide.md#알림-발송)를 본다.
2. 호출한 쪽의 트랜잭션에 참여해 `Notification`을 저장한다. 그 작업이 롤백되면 알림도 남지 않는다.
3. 화면은 `GET /notifications`로 목록과 `unreadCount`를 가져온다. 새 알림을 바로 보여주려면 화면에 들어올 때와 일정 간격으로 다시 조회한다 (서버가 먼저 밀어주는 통로는 없다).

| `NotificationType` | 받는 사람 | 의미 | 보내는 곳 |
| --- | --- | --- | --- |
| `CARE_DONE` | 주인 | 관리사가 작업을 끝냄 | 아직 없음 (작업 완료) |
| `HEART` | 관리사 | 주인이 감사 하트를 보냄 | 아직 없음 (하트) |
| `STAGE_UP` | 주인 | 작물 단계가 오름 | 아직 없음 (성장 시뮬레이션, 단계 확인) |
| `VISIT_RESERVED` | 관리사 | 주말 방문 예약 | `VisitService.reserve` |
| `VISIT_CANCELED` | 관리사 | 방문 예약 취소 | `VisitService.cancel` |
| `STREAK_RISK` | 주인 | 스트릭이 끊길 위험 | 아직 없음 (스트릭 배치) |
| `QUEST_DONE` | 주인 | 주간 퀘스트 달성 | 아직 없음 (퀘스트) |

### 수확, 방문 예약

**텃밭 접점 (`HarvestGardenReader`).** 텃밭, 농장, 관리사, 성장 시뮬레이션 도메인이 아직 없어서, 수확과 방문이 필요로 하는 값만 `HarvestGarden`으로 정의하고 `HarvestGardenReader.findGarden(gardenId)`로 받는다. 구현체는 아직 없고 기본 빈(`HarvestGardenConfiguration`)은 항상 비어 있는 결과를 주므로 관련 API는 `C404`다. 텃밭 도메인이 생기면 구현체를 `@Component`로 등록한다. `HarvestGarden`의 필드는 `gardenId`, `stage`, `ownerUsername`, `ownerNickname`, `characterName`, `farmId`, `farmLatitude`, `farmLongitude`, `caretakerUsername`, `caretakerName`, `startedOn`, `expectedHarvestDate`, `harvestable`, `careLogCount`다.

**방문 슬롯 규칙 (`VisitSlotPolicy`).** 슬롯은 테이블에 두지 않고 규칙으로 계산한다.

| 항목 | 규칙 |
| --- | --- |
| 요일 | 토요일, 일요일만 |
| 시간 | 10시, 11시, 14시, 15시에 시작하는 1시간 (관리사가 맞이할 수 있는 시간. 관리사별 가능 시간 데이터는 없어서 고정값) |
| 정원 | 한 농장의 한 슬롯에 2건까지 (`RESERVED`만 센다) |
| 예약 가능 | 내일 이후의 슬롯만. 오늘과 지난 날짜는 `available=false`, 예약하면 C002 |
| 슬롯 ID | `slot_{yyyyMMdd}_{HH}` (예: `slot_20261128_10`). 형식이 틀리거나 규칙에 없는 날짜, 시간이면 C002 |
| 조회 기간 | `from`, `to` 생략 시 내일부터 14일. `to`가 `from`보다 앞이거나 31일을 넘으면 C002 |

**예약과 취소.**

1. 예약은 내 텃밭인지 (`C404`, `C403`), 예약 가능한 슬롯인지 (C002), 그 텃밭에 같은 날 예약이 있는지 (`C4095`), 슬롯 정원이 찼는지 (`C4096`) 순서로 확인한다.
2. 같은 날 중복은 `Visit.reservedKey`(`텃밭ID:날짜`, 유니크)가 동시 요청까지 막는다. 취소하면 이 값을 비워 다시 예약할 수 있다.
3. 취소는 방문 전날까지만 된다 (당일부터 `C4005`). 내 예약이 아니면 `C403`, 이미 취소했으면 `C4095`다.
4. 예약과 취소 모두 담당 관리사에게 알림(`VISIT_RESERVED`, `VISIT_CANCELED`)을 보낸다. 담당 관리사가 없으면 알림 없이 처리한다.

**나들이 추천.** `Outing`을 농장 좌표와의 직선 거리(하버사인, 소수 첫째 자리 km) 순으로 최대 6곳 내려준다. `OutingSeeder`가 부팅할 때 `outing` 테이블이 비어 있으면 처인구의 실제 장소 7곳을 넣는다. 체험농장 3곳은 농어촌체험휴양마을 데이터(좌표 포함), 식당 4곳은 모범음식점 데이터에서 골랐다. 출처와 고른 기준은 [public-data.md](public-data.md)를 본다.

### 날씨

홈, 작업 생성, 성장 시뮬레이션은 저장소를 직접 보지 않고 `WeatherService`를 쓴다. 기상청 API 명세와 격자 표는 [public-data.md](public-data.md)에 있다.

**수집 배치 (`WeatherSyncService.sync`).** 스케줄러(매시 15분)와 `POST /internal/weather/sync`가 같은 메서드를 부른다.

1. `WeatherFarmReader.findFarms(farmIds)`로 수집할 농장과 격자를 얻는다. 농장 도메인이 아직 없어서 기본 빈이 설정(`weather.sync.default-grids`)의 격자를 준다. 이때는 요청의 `farmIds`를 가리지 않는다. 농장 도메인이 생기면 구현체를 `@Component`로 등록한다.
2. 여러 농장이 같은 격자에 있으면 한 번만 받는다.
3. 격자마다 단기예보(`getVilageFcst`)를 먼저 저장하고 초단기실황(`getUltraSrtNcst`)을 나중에 저장한다. 그래서 실황에 없는 강수확률과 하늘상태가 예보 값으로 남는다.
4. 발표시각은 `KmaBaseTime`이 고른다. 실황은 10분이 지난 가장 최근 정각, 단기예보는 10분이 지난 가장 최근 발표시각(02, 05, 08, 11, 14, 17, 20, 23시)이다.
5. `KmaClient`는 호출이 실패하거나 `resultCode`가 `00`이 아니면 `weather.kma.max-attempts`(기본 3)번까지 다시 시도하고, 끝까지 실패하면 `KmaApiException`을 던진다. 인증키가 로그에 남지 않도록 요청 주소는 기록하지 않는다.
6. 한 격자에서 실황이나 예보 하나가 실패해도 받은 쪽은 저장한다. 실패한 쪽은 앞서 저장된 값이 그대로 남아 조회에 쓰인다. 실패한 농장은 응답의 `failedFarms`에 담긴다.
7. 전체를 하나의 트랜잭션으로 묶지 않는다. 저장은 격자마다 `saveForecasts`의 트랜잭션에서 한다.
8. 인증키(`PUBLIC_DATA_SERVICE_KEY`)가 비어 있으면 호출하지 않고 모든 농장을 `failedFarms`로 돌려준다.

| 메서드 | 내용 |
| --- | --- |
| `getCurrentWeather(nx, ny)` | 현재 날씨 (`CurrentWeatherDto`: `condition`, `temperature`, `rainProbability`, `updatedAt`). 시연 날씨가 있으면 그 값이 우선하고, 없으면 지금 시각 이전의 가장 가까운 값, 그것도 없으면 가장 가까운 예보를 쓴다. 수집된 값이 없으면 비어 있다 |
| `getForecasts(nx, ny, from, to)` | 시간대별 예보. 시연 날씨는 반영하지 않는다 |
| `getDailyWeather(nx, ny, from, to)` | 일별 평균, 최고, 최저 기온 (적산온도 계산용) |
| `saveForecasts(nx, ny, List<WeatherForecastData>)` | 수집한 값을 저장한다. 격자와 시각이 같으면 덮어써 중복으로 쌓이지 않는다. 실황이 들어온 줄은 예보로 덮어쓰지 않고, 실황에 없는 강수확률과 하늘상태는 앞서 받은 예보 값을 남긴다. 값이 바뀐 날짜의 일별 집계도 다시 계산한다 |
| `aggregateDaily(nx, ny, date)` | 하루치 기온으로 `DailyWeather`를 다시 계산한다 |

**condition 규칙 (`WeatherConditionResolver`).** 위에서부터 먼저 맞는 것을 쓴다.

| 순서 | 조건 | condition |
| --- | --- | --- |
| 1 | 눈 (PTY 3, 7) | `COLD` |
| 2 | 비, 비/눈, 소나기, 빗방울 (PTY 1, 2, 4, 5, 6) | `RAIN` |
| 3 | 기온 30℃ 이상 | `HOT` |
| 4 | 기온 5℃ 이하 | `COLD` |
| 5 | 구름많음, 흐림 (SKY 3, 4) | `CLOUDY` |
| 6 | 그 외 | `SUNNY` |

강수확률은 condition에 쓰지 않는다 (지금 비가 오지 않는데 우산 캐릭터가 나오지 않도록). 기온 기준 30℃, 5℃는 임시로 정한 값이다.

**시연 날씨 (`DemoWeatherStore`).** `PUT /demo/weather`가 Redis `demo:weather`에 저장하고 `getCurrentWeather`가 먼저 본다. 시연 텃밭이 하나라서 격자를 가리지 않고 모든 조회에 적용한다. 기온과 강수확률은 상태별 고정값이다 (SUNNY 14.2℃ 10%, RAIN 12℃ 90%, HOT 31℃ 10%). `demo.enabled=false`면 값이 남아 있어도 무시한다. `POST /demo/reset`은 값을 지우지 않고 SUNNY로 둔다 (실제 날씨와 상관없이 시연이 같은 상태에서 시작하도록).

### 응답 형식

```json
// 성공
{ "data": { ... }, "error": { "code": null, "message": null } }

// 실패
{ "data": null, "error": { "code": "C4041", "message": "사용자를 찾을 수 없습니다." } }
```

## 권한

| `UserRole` | authority | 의미 |
| --- | --- | --- |
| `USER` | `ROLE_USER` | 가입 직후, 역할 선택 전 |
| `OWNER` | `ROLE_OWNER` | 텃밭 주인 (도시민) |
| `CARETAKER` | `ROLE_CARETAKER` | 텃밭 관리사 (어르신) |
| `ADMIN` | `ROLE_ADMIN` | 서비스 관리자 |

- **RoleHierarchy** (`SecurityConfiguration.roleHierarchy()`): `ROLE_ADMIN > ROLE_OWNER`, `ROLE_ADMIN > ROLE_CARETAKER`, `ROLE_ADMIN > ROLE_USER`. USER, OWNER, CARETAKER는 서로 포함하지 않는다. 그래서 명세의 "USER, OWNER"처럼 일부만 허용하는 경로를 `hasAnyRole`로 그대로 표현한다.
- **역할 결정:** 가입 직후는 `USER`. `PUT /users/me/role`로 `OWNER` 또는 `CARETAKER`를 고른다. `USER`, `ADMIN`으로는 바꿀 수 없다 (C002). ADMIN 계정은 가입 경로가 없어 DB에서 직접 `role`을 바꿔 만든다.
- **역할 변경:** 역할이 실제로 바뀌면 `profileRegistered`를 false로 되돌린다. 이전 AT, RT는 블랙리스트에 올리고 새 역할이 담긴 토큰을 발급한다.
- **역할 미선택 접근:** `USER`가 OWNER나 CARETAKER 전용 경로에 접근하면 403 `C4031`을 준다 (그 외 권한 부족은 `C403`).
- `hasRole("OWNER")`처럼 `ROLE_` 접두사 없이 쓴다.

### nextStep

`UserService.resolveNextStep(User)` 한 곳에서만 계산하고 signup, login, `/users/me`, 역할 변경이 함께 쓴다.

| 상태 | nextStep |
| --- | --- |
| `USER` | `ROLE_SELECT` |
| `OWNER`, `profileRegistered=false` | `PROFILE_OWNER` |
| `OWNER`, `profileRegistered=true` | `OWNER_HOME` |
| `CARETAKER`, `profileRegistered=false` | `PROFILE_CARE` |
| `CARETAKER`, `profileRegistered=true` | `CARE_TODAY` |
| `ADMIN` | `null` |

## 인증 흐름

```
[signup / login / 역할 변경]
  1. UserService.issueToken(user)
  2. JwtTokenProvider.generateToken()
    ├─ AT: subject=username, claim "authorities"="ROLE_XXX", 만료 1시간
    ├─ RT: subject=username, claim 없음, 만료 7일
    └─ Redis SET refresh:{username} = RT (TTL = RT 만료)

[인증된 요청]
  JwtAuthenticationFilter
    1. Authorization: Bearer <AT> 헤더에서 AT 추출
    2. validateToken (서명, 만료, 블랙리스트 검사)
    3. getAuthentication: claim "authorities" 사용, 없으면 DB에서 role 조회, 사용자도 없으면 권한 없음
    4. SecurityContext 등록 (principal username = 로그인 아이디)

[refresh]
  1. RT 검증
  2. Redis의 refresh:{username}과 일치 확인
  3. AT, RT 새로 발급 (이전 RT는 덮어써져 즉시 무효)

[logout]
  1. AT 검증
  2. Redis refresh:{username}의 RT를 블랙리스트 등록 후 삭제
  3. AT 블랙리스트 등록

[역할 변경 (PUT /users/me/role)]
  1. 역할 저장, 역할이 바뀌었으면 profileRegistered=false
  2. logout과 같은 방식으로 이전 AT, RT 무효화
  3. 새 역할로 AT, RT 발급
```

### Redis 키

| 키 | 값 | TTL |
| --- | --- | --- |
| `refresh:{username}` | Refresh Token | RT 만료 시간 |
| `blacklist:{token}` | `"logout"` | 해당 토큰의 남은 만료 시간 |
| `demo:weather` | `{condition}\|{설정 시각}` (예: `RAIN\|2026-11-10T09:30:00+09:00`) | 없음 (`POST /demo/reset`이 SUNNY로 덮어씀) |

## 엔티티

### `User` (`user` 테이블)

| 필드 | 타입 | 제약 |
| --- | --- | --- |
| `id` | Long | PK, IDENTITY |
| `username` | String | not null, unique, 20자. 로그인 아이디 |
| `name` | String | not null, 20자 |
| `password` | String | not null, BCrypt |
| `role` | `UserRole` | not null, `EnumType.STRING` |
| `profileRegistered` 갱신 | 관리사 등록 시 true, 역할 복원 시 프로필 존재 확인 | 실제 주인 프로필의 등록 연결은 남아 있다. local-demo 시드 계정은 등록 완료 상태다 |
| `registeredAt` | LocalDateTime | updatable=false, `AuthService.signUp`에서 직접 채움 |
| `modifiedAt` | LocalDateTime | `@UpdateTimestamp` |

`UserDetails` 구현, `getUsername()`은 로그인 아이디 반환. `getPublicId()`는 `usr_{id}`.

### `Notification` (`notification` 테이블)

| 필드 | 타입 | 제약 |
| --- | --- | --- |
| `id` | Long | PK, IDENTITY |
| `user` | `User` | `@ManyToOne(LAZY)`, `user_id` not null. 수신자. 인덱스 (`user_id`, `id`) |
| `type` | `NotificationType` | not null, `EnumType.STRING` |
| `title` | String | not null, 100자 |
| `body` | String | 500자 |
| `targetScreen` | String | 30자. 눌렀을 때 이동할 화면 코드 (예: `DT_DIARY`) |
| `targetId` | String | 50자. 그 화면에서 쓸 외부 ID (예: `cl_12`) |
| `createdAt` | LocalDateTime | not null, updatable=false |
| `readAt` | LocalDateTime | 읽은 시각. `null`이면 안 읽음 (읽음 여부 컬럼은 따로 없다) |

### `Visit` (`visit` 테이블)

| 필드 | 타입 | 제약 |
| --- | --- | --- |
| `id` | Long | PK, IDENTITY |
| `gardenId` | Long | not null. 텃밭 PK (엔티티 연관 없음) |
| `farmId` | Long | not null. 농장 PK, 슬롯 정원을 세는 단위. 인덱스 (`farm_id`, `visit_date`) |
| `user` | `User` | `@ManyToOne(LAZY)`, `user_id` not null. 예약한 주인 |
| `visitDate` | LocalDate | not null |
| `startTime`, `endTime` | LocalTime | not null |
| `headcount` | int | not null, 기본 1 |
| `memo` | String | 200자 |
| `status` | `VisitStatus` | not null, `RESERVED` / `CANCELED` |
| `reservedKey` | String | unique, 40자. `텃밭ID:날짜`. 취소하면 `null` |
| `createdAt` | LocalDateTime | not null, updatable=false |
| `canceledAt` | LocalDateTime | |

### `Outing` (`outing` 테이블)

| 필드 | 타입 | 제약 |
| --- | --- | --- |
| `id` | Long | PK, IDENTITY |
| `type` | `OutingType` | not null, `EXPERIENCE_FARM` / `RESTAURANT` |
| `name` | String | not null, 50자 |
| `address`, `description` | String | 200자 |
| `latitude`, `longitude` | double | not null |

### `WeatherForecast` (`weather_forecast` 테이블)

격자와 시각마다 한 줄이다 (유니크: `nx`, `ny`, `forecast_at`).

| 필드 | 타입 | 제약 |
| --- | --- | --- |
| `id` | Long | PK, IDENTITY |
| `nx`, `ny` | int | not null. 기상청 격자 좌표 |
| `forecastAt` | LocalDateTime | not null. 예보 또는 관측 대상 시각 |
| `temperature` | Double | 기온 (℃) |
| `rainProbability` | Integer | 강수확률 (%). 실황에는 없음 |
| `precipitationType` | Integer | 강수형태 코드 (기상청 PTY) |
| `skyStatus` | Integer | 하늘상태 코드 (기상청 SKY). 실황에는 없음 |
| `observed` | boolean | not null. true면 초단기실황, false면 단기예보 |
| `updatedAt` | LocalDateTime | not null |

### `DailyWeather` (`daily_weather` 테이블)

격자와 날짜마다 한 줄이다 (유니크: `nx`, `ny`, `weather_date`).

| 필드 | 타입 | 제약 |
| --- | --- | --- |
| `id` | Long | PK, IDENTITY |
| `nx`, `ny` | int | not null |
| `date` | LocalDate | not null, 컬럼 `weather_date` |
| `avgTemp`, `maxTemp`, `minTemp` | double | not null |
| `updatedAt` | LocalDateTime | not null |

## 에러 코드

| ErrorCode | code | HTTP | 메시지 | 사용처 |
| --- | --- | --- | --- | --- |
| `INVALID_REQUEST` | C001 | 400 | 잘못된 요청입니다. | 미사용 |
| `INVALID_PARAMETER` | C002 | 400 | 유효하지 않은 파라미터입니다. | `@Valid` 검증 실패, 읽을 수 없는 본문 (잘못된 enum 값 포함), 필수 쿼리 누락, 쿼리와 경로 값의 형식 오류 (날짜 등), `RoleNotAllowedException`, `InvalidIdException` (슬롯 ID 포함), `VisitSlotNotBookableException`, `DemoWeatherNotAllowedException` |
| `FILE_NOT_SUPPORTED` | C4001 | 400 | 지원하지 않는 파일입니다. | `FileNotSupportedException` (형식, 크기, 길이 위반), multipart 상한 초과 |
| `CANCEL_DEADLINE_PASSED` | C4005 | 400 | 취소 기한이 지났습니다. | `VisitCancelDeadlinePassedException` |
| `VERIFICATION_INVALID` | C4011 | 401 | 인증 정보가 유효하지 않습니다. | EntryPoint (미인증 접근), `AuthenticationInfoException` |
| `PASSWORD_INVALID` | C4012 | 401 | 비밀번호가 일치하지 않습니다. | `PasswordInvalidException` |
| `TOKEN_INVALID` | C4013 | 401 | 유효하지 않은 토큰입니다. | `TokenInvalidException` |
| `SERVICE_KEY_INVALID` | C4014 | 401 | 서비스 키가 유효하지 않습니다. | `InternalServiceKeyFilter` |
| `ACCESS_DENIED` | C403 | 403 | 승인되지 않은 사용자입니다. | AccessDeniedHandler (권한 부족), `GardenAccessDeniedException`, `VisitAccessDeniedException` |
| `ROLE_NOT_SELECTED` | C4031 | 403 | 역할을 먼저 선택해야 합니다. | AccessDeniedHandler (역할 선택 전 `USER`의 접근) |
| `DEMO_MODE_REQUIRED` | C4032 | 403 | 시연 모드에서만 사용할 수 있습니다. | `DemoModeFilter` (헤더 없음 또는 비활성화) |
| `USER_DATA_NOT_FOUND` | C4041 | 404 | 사용자를 찾을 수 없습니다. | `UserNotFoundException` |
| `DATA_NOT_FOUND` | C404 | 404 | 정보를 불러올 수 없습니다. | 매핑되지 않은 경로 (`NoResourceFoundException`), `NotificationNotFoundException`, `GardenNotFoundException`, `VisitNotFoundException`, `VoiceMemoException` (메모 또는 담당 작업 없음) |
| `USERNAME_DUPLICATION` | C4092 | 409 | 이미 사용 중인 아이디입니다. | `UsernameDuplicationException` |
| `ALREADY_PROCESSED` | C4095 | 409 | 이미 처리된 요청입니다. | `VisitAlreadyReservedException` (같은 날 예약 있음), `VisitAlreadyCanceledException`. 응원, 하트 등 다른 도메인도 이 코드를 쓴다 |
| `VISIT_SLOT_FULL` | C4096 | 409 | 예약이 마감된 시간입니다. | `VisitSlotFullException` |
| `UNKNOWN_ERROR` | C500 | 500 | 오류가 발생하였습니다. | 처리되지 않은 모든 예외 |
| `FILE_UPLOAD_FAILED` | C5001 | 500 | 파일 저장에 실패했습니다. | `FileUploadFailedException` |
| `EXTERNAL_API_ERROR` | C502 | 502 | 외부 서비스 호출에 실패했습니다. | `AiClientException`, `KmaApiException` (수집 배치 안에서 잡으므로 응답으로 나가지는 않는다) |

## 설정

| 파일 | 내용 |
| --- | --- |
| `application.yml` | 공통: `.env` import(`optional:file:.env[.properties]`), 활성 프로필(`SPRING_PROFILES_ACTIVE`, 기본 `local`), JPA open-in-view off, multipart 상한, Jackson 시간대, JWT 만료 시간, 파일 저장소 종류, 외부 호출 타임아웃, 스케줄러, 기상청 연결과 수집 격자, AI 설정, Swagger 경로 |
| `application-local.yml` | 모든 값에 개발용 기본값, `ddl-auto: update`, SQL 로그 on |
| `application-prod.yml` | 기본값 없음 (누락 시 부팅 실패), `ddl-auto: validate`, SQL 로그 off |
| `.env.example` | 환경변수 목록과 로컬 기본값. `.env`로 복사해서 쓰고, `.env`는 `.gitignore`에 포함 |
| `application.properties` | `spring.application.name`만 있음. `application.yml`과 중복, 삭제해도 무방 |

### 환경변수

값은 OS 환경변수, 로컬 `.env` 파일, yml 기본값 순으로 우선한다. `.env`에 `KEY=`처럼 빈 값을 두면 yml 기본값이 무시되니 쓰지 않을 값은 줄을 지우거나 주석으로 둔다.

| 변수 | local 기본값 | 설명 |
| --- | --- | --- |
| `SPRING_PROFILES_ACTIVE` | `local` | `local` / `prod` |
| `DB_HOST`, `DB_PORT`, `DB_NAME` | `localhost`, `3306`, `growwithus-server` | MariaDB |
| `DB_USERNAME`, `DB_PASSWORD` | `root`, `root` | |
| `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD` | `localhost`, `6379`, (없음) | |
| `JWT_SECRET_KEY` | 개발용 더미 | Base64 인코딩된 256비트 이상. **배포 전 반드시 교체** |
| `JWT_ACCESS_TOKEN_EXPIRE_TIME` | `3600000` (1시간) | ms |
| `JWT_REFRESH_TOKEN_EXPIRE_TIME` | `604800000` (7일) | ms |
| `CORS_ALLOWED_ORIGINS` | `*` | 콤마 구분, `allowedOriginPatterns`로 적용 |
| `INTERNAL_SERVICE_KEY` | `local-internal-service-key` | `/internal/**`의 `X-Service-Key` 값. **배포 전 반드시 교체** |
| `DEMO_ENABLED` | `true` | `/demo/**` 사용 여부. prod에서 `false`로 두면 시연 API가 모두 막힌다 |
| `SCHEDULER_ENABLED` | `true` | `@Scheduled` 사용 여부 (prod에도 같은 기본값) |
| `FILE_STORAGE` | `local` | 파일 저장소 구현체 (prod에도 같은 기본값) |
| `FILE_UPLOAD_DIR` | `./uploads` | 로컬 저장소 폴더. `.gitignore`에 포함 |
| `FILE_BASE_URL` | `http://localhost:8080` | 업로드 URL 앞에 붙는 서버 주소 |
| `EXTERNAL_HTTP_CONNECT_TIMEOUT`, `EXTERNAL_HTTP_READ_TIMEOUT` | `3s`, `30s` | 외부 API 호출 타임아웃 (prod에도 같은 기본값) |
| `PUBLIC_DATA_SERVICE_KEY` | (없음) | 공공데이터포털 인증키 (기상청 단기예보). 포털의 "일반 인증키"를 그대로 넣는다. 비워 두면 부팅은 되고 날씨 수집을 건너뛴다 (prod에도 같은 기본값) |
| `KMA_MAX_ATTEMPTS`, `KMA_RETRY_DELAY` | `3`, `1s` | 기상청 호출 전체 시도 횟수와 다시 시도하기 전 대기 시간 (prod에도 같은 기본값) |
| `WEATHER_DEFAULT_GRIDS` | `64:119` | 농장 도메인이 붙기 전까지 수집할 기상청 격자. `nx:ny`를 콤마로 잇는다. 기본값은 처인구 대표 격자 (prod에도 같은 기본값) |
| `LLM_API_KEY`, `LLM_BASE_URL`, `LLM_MODEL` | (없음) | LLM 연결 설정. 비워 두어도 부팅된다 |
| `AI_LOCAL_ENABLED` | `false` | 로컬 Ollama 사용 여부. 유료 API로 자동 전환하지 않음 |
| `AI_LOCAL_BASE_URL` | `http://127.0.0.1:11434` | loopback HTTP 주소만 허용 |
| `AI_LOCAL_MODEL` | (없음) | 미리 다운로드한 로컬 모델. 활성화 시 필수, cloud 이름 거부 |
| `AI_LOCAL_STT_ENABLED` | `false` | 로컬 음성 인식 작업 서버 사용 여부 |
| `AI_LOCAL_STT_BASE_URL` | `http://127.0.0.1:8765` | 음성 인식 작업 서버, loopback HTTP 주소만 허용 |
| `STT_API_KEY`, `STT_BASE_URL`, `STT_MODEL` | (없음) | STT 연결 설정 |
| `TTS_API_KEY`, `TTS_BASE_URL`, `TTS_MODEL`, `TTS_VOICE` | (없음) | TTS 연결 설정 |

prod에서 기본값이 없어 반드시 넣어야 하는 값: DB, Redis, `JWT_SECRET_KEY`, `CORS_ALLOWED_ORIGINS`, `INTERNAL_SERVICE_KEY`, `DEMO_ENABLED`, `FILE_UPLOAD_DIR`, `FILE_BASE_URL`.

## 로컬 실행

```bash
cp .env.example .env   # 필요하면 값 수정
docker run -d -p 3306:3306 -e MARIADB_ROOT_PASSWORD=root -e MARIADB_DATABASE=growwithus-server mariadb:11
docker run -d -p 6379:6379 redis:7
./gradlew bootRun
```

- 컴파일만: `./gradlew compileJava`
- DB 없이 빌드: `./gradlew build -x test` (`contextLoads` 테스트가 실제 MariaDB를 요구)
- DB 없이 도는 단위 테스트만: `./gradlew test --tests 'community.likelion.growwithusserver.common.*'` (도메인 단위 테스트는 `common` 자리에 `notification`, `harvest`, `weather`, `demo`)
- Swagger: http://localhost:8080/swagger-ui.html

## 알려진 이슈 / 결정 대기

| 항목 | 현재 상태 | 영향 |
| --- | --- | --- |
| `profileRegistered` 갱신 | 관리사 등록 시 true, 역할 복원 시 프로필 존재 확인 | 실제 주인 프로필의 등록 연결은 남아 있다. local-demo 시드 계정은 등록 완료 상태다 |
| `/users/me`의 프로필 | 관리사는 실제 프로필과 돌봄 수, 주인은 OwnerSummaryReader로 조회 | 기본 주인 조회는 빈 결과다. local-demo 전용 조회와 별개로 운영 어댑터가 필요하다 |
| 기존 로컬 DB의 `user` 테이블 | `ddl-auto: update`는 예전 `email` 컬럼(not null)을 지우지 않는다 | 가입 시 insert가 실패한다. 로컬 DB의 `user`, `product` 테이블을 직접 drop하고 다시 띄운다 |
| prod `ddl-auto: validate` | 스키마 자동 생성 안 함 | prod DB에 테이블을 미리 만들어야 부팅됨 (마이그레이션 도구 없음) |
| `@CreatedDate` 미동작 | `@EnableJpaAuditing` 없음 | 감사 필드는 서비스에서 `LocalDateTime.now()`로 직접 채운다 |
| 검증 실패 메시지 | 클라이언트에는 C002 고정 메시지만 반환 | 어떤 필드가 틀렸는지는 서버 로그에만 남음 |
| 테스트 | `contextLoads`, `common` 단위 테스트 (페이지네이션, ID, 파일 검증, 필터, 날짜 직렬화), `notification`, `harvest`, `weather`, `demo` 서비스 단위 테스트 (저장소는 Mockito로 대체) | 실제 DB를 쓰는 저장소 쿼리 테스트는 없음 |
| AI 실행 환경 | LLM과 STT는 선택적 로컬 구현, TTS는 기본 빈만 있음 | 모델과 faster-whisper를 별도 준비해야 하며 실제 모델 실행은 미검증. TTS는 여전히 C502. 배포 서버에서 노트북의 localhost에 접근할 수 없음 |
| 시연 계정 | local-demo가 전용 DB에 관리사, 주인, ADMIN을 시드 | 일반 가입으로 ADMIN을 만들지 않는다. 운영에서 local-demo를 켜지 않는다 |
| 음성 메모 API | 작업 조회는 구현, 실제 텃밭 조회 어댑터가 없음 | 농장과 텃밭 도메인 연결 및 MariaDB 잠금 검사가 필요. STT 실패 시 파일 보존과 재시도 구현, 초안 롤백 파일 정리는 구현, 커밋 결과 불확실 시 보존. 실제 DB 검사 필요 |
| 브라우저 녹음 길이 | 공통 업로드 모듈은 webm과 조각 mp4 길이를 읽지 못함 | 로컬 STT를 활성화하면 작업 서버에서 디코딩된 전체 길이를 60초 이하로 다시 검사한다. 실제 브라우저 파일 시험은 남아 있음 |
| 텃밭 조회 구현체 | 기본 조회는 빈 결과, local-demo만 가상 텃밭 제공 | 실제 서비스에는 팀 텃밭 조회 어댑터가 필요하다. 시연 작물과 기온은 운영 자료가 아니다 |
| 방문 슬롯 정원의 동시성 | VisitCapacityGuard가 날짜 행을 잠근 뒤 정원을 검사한다 | 같은 텃밭의 같은 날 예약은 유니크 키로 보호한다 |
| 관리사별 방문 가능 시간 | 데이터가 없어 주말 고정 시간(10, 11, 14, 15시)을 쓴다 | 관리사가 시간을 고르게 하려면 프로필에 가능 시간을 추가하고 `VisitSlotPolicy`를 고친다 |
| 나들이 추천 데이터 | 실제 장소 7곳을 시드로 넣는다. 식당은 원본에 좌표가 없어 좌표를 확인한 4곳만 넣었고, 그중 2곳(국수59, 백암제일식당)은 가게가 있는 길의 좌표다 | 거리가 100m쯤 어긋날 수 있다. 예시 6곳이 이미 들어간 DB에는 시드가 다시 들어가지 않으므로 `outing` 테이블을 비우고 다시 띄운다. 식당을 더 넣으려면 주소를 좌표로 바꾸는 방법이 필요하다 |
| 날씨 수집 격자 | 농장 도메인이 없어 `WeatherFarmReader` 구현체가 없음. 설정의 기본 격자(`64:119`)만 수집한다 | 농장마다 격자가 다르면 그 농장의 날씨가 비어 있다. 농장 도메인에서 농장 좌표로 격자를 구해 (변환식은 [public-data.md](public-data.md)) 구현체를 등록한다. 그 전에는 `WEATHER_DEFAULT_GRIDS`에 격자를 더한다 |
| 날씨 수집 인증키 | `PUBLIC_DATA_SERVICE_KEY`가 비어 있으면 수집을 건너뜀 | `weather_forecast`가 비어 `getCurrentWeather`는 시연 날씨가 있을 때만 값을 준다. 로컬 `.env`와 배포 환경에 키를 넣어야 한다 |
| 시연 API | 날씨, 누적 일수, 빨리 감기, 성장과 작업 회차 초기화 구현 | 운영 스키마 변경 필요. 상단 시연 도메인 설명 참고 |
| 파일 저장소 | 로컬 디스크만 구현 | 서버를 여러 대 띄우거나 재배포로 디스크가 초기화되면 파일이 사라진다. 필요하면 S3 구현체를 추가한다 |

## 가족 일기와 로컬 시연 환경

1. `diary` 패키지의 `DiaryListener`는 `CareCompleted`, `GrowthChanged` 이벤트를 원래 트랜잭션 안에서 수신한다. `DiaryEntry`에 원문, 확정 정리문, 사진, 단계와 원본 ID를 저장한다. 일기 저장 실패는 원래 완료 처리까지 롤백한다. `entry_type, source_id` 유니크 제약으로 원본을 한 번만 기록한다.
2. `DiaryService`는 실제 텃밭 소유권 확인 후 `GET /gardens/{gardenId}/diary`를 반환한다. ALL, PHOTO, STAGE 필터, ID 내림차순 dry_ 커서, 1~50개 제한을 사용한다. 현재 페이지 돌봄의 하트 상태는 CareLog를 묶어서 조회한다. REST_DAY, 하트 보내기, 기존 기록의 이관은 미구현이다.
3. 새 테이블은 `src/main/resources/db/manual/diary.sql`에 있다. 운영 DB에는 사전 검토와 적용이 필요하다. local-demo는 전용 MariaDB에서 ddl-auto=update로 준비한다.
4. `demo/local`은 local-demo 프로필에만 등록된다. 가상 B14 텃밭, 농장, 주인 요약, 합성 기온을 제공한다. 사용자와 돌봄 기록은 실제 저장소를 쓴다. 단일 시연 텃밭의 변경 잠금은 전용 주인 사용자 행으로 직렬화한다. 운영용 텃밭 행 잠금의 대체 구현이 아니다.
5. `LocalDemoSeeder`는 환경변수의 비밀번호로 전용 사용자 세 명과 관리사 프로필을 생성한다. 기존 계정의 역할이나 비밀번호가 다르면 덮어쓰지 않고 중단한다. 초기 성장 상승은 가족 일기도 만든다. 주인 등록 API의 첫 일기 자동 연결을 대신하지 않는다.
6. `compose.local-demo.json`은 MariaDB, Redis, Ollama, faster-whisper, Spring을 구성한다. 모델 설치 작업이 완료된 뒤 서비스를 시작하며 backend와 stt는 ollama의 네트워크 공간을 사용한다. 기존 loopback 주소 검증을 완화하지 않는다. 호스트에는 127.0.0.1:8080만 공개하고 모델, DB, Redis 포트는 공개하지 않는다.
7. `scripts/start-local-demo.ps1`은 무작위 설정을 한 번만 만들고 기존 설정과 볼륨을 유지한다. `scripts/smoke_local_demo.py`는 실행된 서버를 검사한다. 기본 검사는 음성과 완료를 not_tested로 표시하며, 실제 음성을 지정했을 때만 해당 흐름을 검사한다.
8. `/local-demo`와 스크립트만 로그인 없이 볼 수 있다. 실제 API와 `/local-demo/status`는 토큰이 필요하다. 전용 HTML과 JS는 static 폴더 밖에 있어 프로필을 끄면 제공되지 않는다. 이 콘솔은 기능 검사 화면이며 팀 프론트를 대체하지 않는다.
9. STT 모델의 필수 파일은 model.bin, config.json, tokenizer.json이다. 공식 base 모델에 없는 preprocessor_config.json은 선택 파일로 처리한다. 추론 시 자동 다운로드와 유료 API 전환은 없다. Qwen2.5 3B는 텍스트 전용이므로 기본 구성의 사진 검증은 null이다. TTS 공급자는 여전히 준비되지 않았다.
10. Java 핵심 JUnit 77개, STT Python 23개, 시연 도구 Python 9개를 통과했다. 실제 Docker, 전체 Gradle 빌드, DB 쿼리와 잠금, 브라우저 녹음, 모델 추론은 작성 환경에서 검증하지 못했다. 상세 실행 절차는 [local-demo.md](local-demo.md)를 따른다.

## 모델 평가와 시연 화면 보완

1. evaluateLocalAi는 명시적으로 실행하는 JavaExec 작업이다. testClasses의 합성 원문 12개로 운영과 같은 VoiceMemoAnalysisService를 호출한다. 기본 test와 build에서는 모델을 호출하지 않는다. 모델과 주소는 기존 AI_LOCAL_MODEL, AI_LOCAL_BASE_URL을 사용하며 loopback과 cloud 이름 거부 정책도 같다.
2. 평가기는 태그의 micro precision, recall, F1, 정확한 집합 일치, 대체 경로 수, p50과 p95 지연을 보고한다. 대체 결과는 모델 성공으로 세지 않는다. 결과가 하나라도 불일치하면 보고서를 저장하고 종료 코드 1을 반환한다. STT와 의미적 요약 품질은 평가하지 않으며 실제 점수는 아직 없다.
3. Compose의 ai-evaluation은 evaluation 프로필을 선택해야 실행된다. 모델 서버와 네트워크 공간을 공유하고 호스트에는 별도 포트를 열지 않는다. 결과만 build/reports에 기록하며 사용자 DB와 음성 파일을 수정하지 않는다.
4. 시연 콘솔은 계정별 토큰을 메모리에서 관리한다. 401이면 계정별 갱신 요청을 공유하고 원래 요청을 한 번만 다시 보낸다. 403과 네트워크 오류는 자동 재전송하지 않는다. 새 음성을 선택한 상태에서 이전 메모로 완료하지 못하게 검사한다.
5. 원문에서 추출된 이상 징후를 한글 문구로 표시하며 수정 확정 시 오래된 태그를 지운다. 읽어주기는 브라우저 speechSynthesis이고 서버 TTS와 별개다. 마이크 권한 대기 중 작업이 바뀌면 받은 스트림을 종료한다. 기본 자동 녹음은 59초로 인코딩 여유를 둔다.
6. 음성 처리 유효 시간은 HTTP 읽기 제한의 2배 + 30초이고 최소 2분이다. 기본 30초 설정은 2분, local-demo의 90초 설정은 3분 30초다. STT 다음 LLM을 기다리는 정상 요청을 먼저 만료시키지 않도록 한다. 재시도와 늦은 결과의 토큰 검사는 유지한다.
7. 핵심 JUnit 77개, Python 32개, Node 시연 로직 10개가 통과했다. Node는 DOM과 fetch 대역 검사이며 실제 마이크와 브라우저 화면 검사와 다르다. 운영 DB, 모델, 전체 Gradle 빌드는 미검증이다. 팀 전달 기준과 남은 항목은 [ai-handoff.md](ai-handoff.md)를 따른다.

## 방문 예약 동시 요청 보완

1. VisitBookingDay는 farm_id와 visit_date의 유니크 행이다. 예약이 없는 날에도 직렬화할 대상을 만들며, 기존 예약 자료를 이관하거나 정원 수를 복사하지 않는다. 수동 스키마는 db/manual/visit_booking_day.sql이다.
2. VisitService.reserve는 기존 소유권, 슬롯, 중복 검증 후 VisitCapacityGuard를 호출한다. 같은 트랜잭션 안에서 날짜 행을 upsert하고 잠근 뒤, 현재 RESERVED 방문 ID를 FOR UPDATE로 조회해 두 가족 정원을 확인한다. 실제 예약과 알림 저장이 끝날 때까지 잠금을 유지한다. 이전 스냅샷의 일반 count 결과에 의존하지 않는다.
3. 취소는 VisitRepository.findByIdForUpdate로 예약 행을 잠근 후 소유권, 취소 상태, 기한을 확인한다. 두 취소가 같은 예약을 읽고 알림을 각각 보내는 경로를 막는다. 이미 취소된 예약은 기존 C4095 응답을 유지한다.
4. 관련 DB는 InnoDB와 실제 트랜잭션을 사용해야 한다. 정원 보장을 검증하려면 같은 농장의 서로 다른 텃밭으로 마지막 자리의 경쟁 요청, 새 날짜 동시 생성, 취소와 예약 경쟁을 실행해야 한다. 실제 DB 검사는 아직 수행하지 않았다. 기존 날짜 유니크 제약은 그대로 유지한다.
5. 로컬 시연 화면에서 가족 토큰으로 방문 시간 조회, 1명 예약, 이번 화면의 예약 취소를 호출한다. 성공 응답의 예약 ID를 메모리에 보관하며 새로고침 후 예약 이력 조회는 제공하지 않는다. 취소 응답을 놓친 뒤 재시도해 C4095를 받으면 이미 취소된 화면으로 복구한다. 예약 생성 응답 유실의 멱등 처리는 별도 남은 항목이다.
6. 시연 수확 조회의 돌봄 수는 CareLogRepository.countByGardenId로 실제 완료 수를 반환한다. 시연용 고정 0을 쓰지 않는다.
7. 핵심 JUnit 77개, Python 32개, Node 화면 로직 10개가 통과했다. 예약 정원 경계 검사 3개는 저장소 대역을 사용한다. UserService와 보안 의존성까지 필요한 VisitServiceTest는 이 환경의 부분 클래스패스 검사에 포함되지 않았다. 전체 Gradle 빌드와 실제 DB 검증이 필요하다.

MariaDB 잠금 참고: https://mariadb.com/docs/server/reference/sql-statements/data-manipulation/selecting-data/for-update

## 음성 초안 롤백과 파일 보존

1. FileStorage에 deleteAudio를 추가하고 LocalFileStorage가 자신의 base URL과 voice-memos 아래의 UUID 음성 파일만 처리한다. 다른 디렉터리, 외부 URL, 경로 이동, 심볼릭 링크와 디렉터리 삭제를 거부한다. 재시도 읽기에도 같은 경로 검증을 사용한다.
2. VoiceMemoStore.createPending은 초안 검증과 DB 저장 전에 FileUploadService.deleteAudioOnRollback으로 Spring 트랜잭션 완료 콜백을 등록한다. 확실한 ROLLED_BACK일 때만 새 음성을 삭제한다. 정상 커밋과 UNKNOWN 결과에서는 보존하며 처리 결과 저장과 재시도에는 삭제 콜백을 등록하지 않는다.
3. 삭제 실패는 로그에 남기고 원래 비즈니스 오류를 유지한다. 실제 트랜잭션이 없는 직접 호출에서는 삭제하지 않는다. 파일 복사 도중의 부분 파일이나 사진 DB 저장 실패의 정리는 이 변경 범위가 아니다.
4. Python STT 작업 서버는 PyAV InvalidDataError를 잘못된 음성으로 구분해 HTTP 400을 반환한다. Java는 INVALID_AUDIO로 저장하며 같은 깨진 파일의 재시도를 권하지 않는다. 일반 모델 내부 오류는 503을 유지한다. 실제 코덱 시험은 아직 하지 않았다.
5. 핵심 JUnit 77개, STT Python 23개, 도구 Python 9개, Node 화면 로직 10개가 통과했다. Spring 트랜잭션 완료 콜백과 로컬 파일 삭제는 실제 라이브러리 및 임시 파일로 검사했으며 DB 커밋 자체는 대역이다. 모델, HTTP 인증과 MariaDB의 전체 통합 검사는 남아 있다.
6. AI 역할 설명, 2분 시연 준비와 예상 질문 답변은 [ai-presentation.md](ai-presentation.md)에 정리했다. 자체 학습, 모델 성능, 사진 판별, 서버 TTS의 미구현 범위를 구분하며 실제 측정 전 점수나 응답 시간을 주장하지 않는다.

## 사용자 PC의 실행 준비 도구

1. 사용자 fetch와 push 뒤 origin/feat/seungyoon-ai가 f16edaf와 일치하는 것을 확인했다. origin/develop은 기준 커밋 0f025dc와 같고 main과 develop에는 병합하지 않았다. 이후 설치 도구 변경은 별도 커밋이다.
2. RUN-LOCAL-DEMO.cmd는 setup-local-demo.ps1을 실행한다. 지정한 저장소의 깨끗한 feat/seungyoon-ai일 때만 명시적 브랜치 push를 시도하고 설치와 로컬 실행을 이어간다. 새 체크아웃, 병합, 강제 push나 볼륨 삭제를 수행하지 않는다.
3. 설치는 공식 Docker 다운로드와 Authenticode의 유효한 Docker 서명을 확인한 뒤 per-user 모드로 수행한다. 기존 Docker가 있으면 설치하지 않는다. PATH 변경은 현재 프로세스에만 적용하며 시스템 재시작은 요청하지 않는다. WSL이나 운영체제 승인 단계는 사용자가 완료해야 한다.
4. CheckOnly는 다운로드, 설치, Git 쓰기와 서비스 실행을 하지 않는다. PowerShell의 구문과 가짜 서명, 브랜치, 원격, dirty 상태, 업로드 실패, 엔진 준비 상태를 검사했다. 실제 설치는 이 작성 환경에서 실행하지 않았다. WSL 상태 조회도 E_ACCESSDENIED로 차단되어 있다.
5. 세부 절차는 local-demo.md를 따른다. 설치 도구 준비가 실제 모델, DB, 전체 빌드와 시연의 성공을 의미하지 않는다. 코드 검사 결과 77개와 Python 32개, Node 10개는 이전과 같다.


## 실행 검사 결과 파일

smoke_local_demo.py는 실제 실행 시 build/reports/local-demo-smoke.json을 원자적으로 교체한다. 시작은 running, 성공은 passed와 실제 검사 결과, 실패는 failed와 예외 종류만 저장한다. 이전 성공을 새 실패로 잘못 판단하지 않도록 시각을 함께 기록하며, 비밀번호와 토큰, 발언, 예외 원문은 저장하지 않는다. 기본 검사의 voice와 completion은 not_tested를 유지한다. 이 보고서 기능 검사 2개가 추가되어 Python은 STT 23개와 도구 9개가 통과했다. 실제 서버 보고서는 아직 생성되지 않았다.


STT의 Docker build context는 local-ai 폴더이므로 해당 폴더에도 .dockerignore를 둔다. 호스트 가상환경, 모델, 캐시와 환경변수 파일을 빌드 컨텍스트로 보내지 않는다. 실제 모델 파일은 전용 Docker 볼륨의 명시적 초기화 작업으로 준비한다.


## Team domain integration

# 현재 구조

> 코드의 현재 상태를 그대로 기록한 문서다. 기능을 추가하거나 바꾸면 이 문서도 함께 갱신한다 ([development-guide.md](development-guide.md#문서-갱신-규칙)).
>
> 마지막 갱신: 2026-11-13, 농장과 칸, 주인 프로필과 텃밭, 홈과 응원과 하트, 성장 일기, 진화 이벤트, 리그와 퀘스트와 배지와 퀴즈, 스트릭과 리그 정산 배치 추가 (#3, #4, #9, #10, #11). DB 격리 수준을 READ COMMITTED로 변경

## 한눈에 보기

| 항목 | 내용 |
| --- | --- |
| 언어 / 런타임 | Java 21 |
| 프레임워크 | Spring Boot 4.1.2 (webmvc, data-jpa, security, validation, data-redis) |
| 빌드 | Gradle (Groovy DSL, `build.gradle`) |
| DB | MariaDB (`MariaDBDialect`), 트랜잭션 격리 수준 READ COMMITTED ([이유](#동시-요청과-잠금)) |
| 캐시 / 토큰 저장소 | Redis |
| 인증 | JWT (jjwt 0.12.6, HS256), Stateless |
| API 문서 | springdoc-openapi 3.0.3 (`/swagger-ui.html`) |
| 베이스 패키지 | `community.likelion.growwithusserver` |
| 시간대 | JVM 기본 `Asia/Seoul` (`GrowwithusServerApplication.main`에서 `TimeZone.setDefault`), Jackson도 `Asia/Seoul` |
| 외부 호출 | `RestClient` (JDK `HttpClient`, 연결 3초, 읽기 30초 타임아웃) |
| 스케줄러 | `@EnableScheduling` (`scheduler.enabled`로 끌 수 있음) |
| 앱 클래스 | `GrowwithusServerApplication` |

## 패키지 구조

```
community.likelion.growwithusserver/
├── GrowwithusServerApplication.java  # main()에서 JVM 기본 시간대를 Asia/Seoul로 고정
├── common/                         # 공통 모듈 (도메인에 의존하지 않음, 예외: JwtTokenProvider가 UserRepository 참조)
│   ├── ai/
│   │   ├── LlmClient, SttClient, TtsClient  # AI 호출 공통 인터페이스
│   │   ├── AiProperties            # ai.llm, ai.stt, ai.tts 설정 (apiKey, baseUrl, model, voice)
│   │   ├── AiClientConfiguration   # 구현체가 없을 때 쓰는 기본 빈 (항상 AiClientException)
│   │   └── AiClientException       # C502
│   ├── file/
│   │   ├── FileUploadService       # uploadImage / uploadAudio: 검증 후 저장, URL 반환
│   │   ├── FileStorage             # 저장소 추상화 (store)
│   │   ├── LocalFileStorage        # 로컬 디스크 구현 (file.storage=local)
│   │   ├── FileWebConfiguration    # 로컬 저장 파일을 /uploads/**로 제공
│   │   ├── AudioDurationReader     # wav, m4a(mp4) 헤더에서 재생 길이 읽기
│   │   └── exception/              # FileNotSupportedException (C4001), FileUploadFailedException (C5001)
│   ├── http/
│   │   └── RestClientConfiguration # externalRestClient 빈 (타임아웃 설정)
│   ├── id/
│   │   ├── IdPrefix                # 외부 ID 형식 (usr_12): format / parse
│   │   └── InvalidIdException      # C002
│   ├── pagination/
│   │   ├── CursorPageRequest       # 쿼리 cursor, limit (기본 20, 1~50)
│   │   └── CursorPageResponse<T>   # {items, nextCursor}
│   ├── scheduler/
│   │   └── SchedulingConfiguration # @EnableScheduling
│   ├── time/
│   │   └── DateTimes               # LocalDateTime을 +09:00 OffsetDateTime으로 변환
│   ├── redis/
│   │   ├── RedisConfiguration      # RedisTemplate<String, Object>, 키와 값 모두 String 직렬화
│   │   └── RedisService            # setValues / getValues / deleteValues / hasKey
│   ├── response/
│   │   ├── GlobalResponse<T>       # 모든 응답 래퍼 {data, error}
│   │   ├── GlobalErrorResponse     # {code, message}
│   │   ├── ErrorCode               # 에러 코드 enum
│   │   ├── GlobalException         # 모든 비즈니스 예외의 부모 (abstract)
│   │   └── GlobalExceptionHandler  # @RestControllerAdvice
│   ├── security/
│   │   ├── SecurityConfiguration   # 필터 체인, 경로 권한, CORS, RoleHierarchy, PasswordEncoder
│   │   ├── JwtTokenProvider        # 토큰 발급, 검증, 블랙리스트, Redis RT 저장
│   │   ├── JwtAuthenticationFilter # Authorization 헤더를 읽어 SecurityContext에 등록
│   │   ├── InternalServiceKeyFilter # /internal/**: X-Service-Key 검증 후 ROLE_SYSTEM 부여
│   │   ├── DemoModeFilter          # /demo/**: X-Demo-Mode: true 검증 (권한은 부여하지 않음)
│   │   ├── SecurityErrorWriter     # 필터 단계의 GlobalResponse 에러 응답 작성
│   │   ├── JwtAuthenticationEntryPoint  # 401 응답 (C4011)
│   │   ├── JwtAccessDeniedHandler  # 403 응답 (C403, 역할 미선택 사용자는 C4031)
│   │   ├── SecurityUtil            # getUsername(): 현재 사용자 로그인 아이디
│   │   ├── AuthenticationToken     # {accessToken, refreshToken, expiresIn}
│   │   └── exception/
│   │       ├── TokenInvalidException        # C4013
│   │       └── AuthenticationInfoException  # C4011, SecurityUtil.getUsername() 실패 시
│   └── swagger/
│       └── SwaggerConfiguration    # OpenAPI 정보 + bearerAuth 스킴
├── user/                           # 회원 / 인증 도메인
│   ├── controller/    AuthController (/auth), UserController (/users)
│   ├── service/       AuthService (가입, 로그인, 재발급, 로그아웃), UserService (사용자 조회, nextStep, 토큰 발급, 역할)
│   └── data/
│       ├── dto/request/   SignUpRequestDto, LoginRequestDto, RefreshRequestDto, RoleChangeRequestDto
│       ├── dto/response/  AuthResponseDto, TokenRefreshResponseDto, UsernameCheckResponseDto, UserMeResponseDto, RoleChangeResponseDto
│       ├── entity/        User (implements UserDetails)
│       ├── enums/         UserRole, NextStep
│       ├── exception/     UsernameDuplicationException, PasswordInvalidException, UserNotFoundException, RoleNotAllowedException
│       └── repository/    UserRepository
│   └── profile/       OwnerSummaryReader (주인 프로필, 텃밭 도메인과의 접점), OwnerSummaryConfiguration (구현체가 없을 때의 기본 빈)
├── farm/                           # 주말농장, 칸, 작물 기준 데이터
│   ├── controller/    FarmController (/farms, /farms/{farmId}/plots/{plotCode}, /crops), FarmSyncController (/internal/farms)
│   ├── service/       FarmService (목록, 칸 미리보기, 작물), FarmSyncService (공공데이터 파일 동기화), FarmSeeder (작물, 농장, 시연 농장 시드), FarmWeatherFarmReader (날씨 수집에 농장 격자 제공), KmaGrid (좌표를 기상청 격자로), YonginTown (읍면동 대표 좌표)
│   ├── scheduler/     FarmSyncScheduler (매주 월 03:00)
│   ├── caretaker/     FarmCaretakerReader (관리사 프로필 도메인과의 접점), FarmCaretaker, FarmCaretakerConfiguration (기본 빈)
│   └── data/
│       ├── dto/           FarmItemDto, PlotPreviewResponseDto, CropResponseDto, FarmSyncResponseDto
│       ├── entity/        Farm, Plot, Crop
│       ├── enums/         District, GrowthStage
│       ├── exception/     FarmNotFoundException, PlotNotFoundException, PlotAlreadyAssignedException
│       └── repository/    FarmRepository, PlotRepository, CropRepository
├── garden/                         # 주인 프로필, 텃밭, 홈, 응원, 감사 하트, XP
│   ├── controller/    OwnerProfileController (/owners/me/profile), GardenHomeController (/gardens/{gardenId}/home, cheers), CareLogHeartController (/care-logs/{careLogId}/hearts)
│   ├── service/       OwnerProfileService (프로필 등록과 텃밭 시작, 수정), GardenService (텃밭 조회, 소유 확인, 잠금), GardenHomeService (홈, 응원), CareLogHeartService (하트), XpService (XP 지급), CharacterAnimation (기분과 날씨로 애니메이션 결정), GardenHarvestReader (수확 도메인에 텃밭 제공), GardenOwnerSummaryReader (/users/me에 주인 요약 제공), GardenDemoSeeder (시연 계정과 텃밭 시드)
│   ├── care/          GardenCareLogReader (돌봄 기록 도메인과의 접점), GardenCareLog, GardenCareLogConfiguration (기본 빈)
│   └── data/
│       ├── dto/           OwnerProfileRequestDto, OwnerProfileCreateResponseDto, OwnerProfileUpdateRequestDto, OwnerProfileResponseDto, GardenHomeResponseDto, CheerResponseDto, HeartResponseDto
│       ├── entity/        OwnerProfile, Garden, XpHistory, CareLogHeart
│       ├── enums/         CharacterMood, XpReason
│       ├── exception/     ProfileAlreadyExistsException, OwnerProfileNotFoundException, OwnerRoleRequiredException, OwnerDistrictNotAllowedException, CheerAlreadyDoneException, HeartAlreadySentException, CareLogNotFoundException
│       └── repository/    OwnerProfileRepository, GardenRepository, XpHistoryRepository, CareLogHeartRepository
├── diary/                          # 성장 일기 (테이블 없음, 돌봄 기록과 진화 이벤트를 날짜순으로 엮는다)
│   ├── controller/    DiaryController (/gardens/{gardenId}/diary)
│   ├── service/       DiaryService
│   └── data/          dto/ DiaryItemDto, enums/ DiaryFilter, DiaryItemType, exception/ DiaryCursorInvalidException
├── evolution/                      # 진화 이벤트 (단계 변화와 보상)
│   ├── controller/    EvolutionEventController (/gardens/{gardenId}/events, /events/{eventId}/ack)
│   ├── service/       EvolutionEventService (advanceStage, 미확인 목록, 확인)
│   └── data/          dto/ EvolutionEventResponseDto, EventAckResponseDto, entity/ EvolutionEvent, exception/ EvolutionEventNotFoundException, EvolutionEventAlreadyAckedException, repository/ EvolutionEventRepository
├── game/                           # 리그, 주간 퀘스트, 배지, 스트릭 배치, 퀴즈
│   ├── controller/    LeagueController (/leagues), QuestController (/quests), BadgeController (/users/me/badges), QuizController (/gardens/{gardenId}/quizzes/today, /quizzes/{quizId}/answers), GameBatchController (/internal/streaks, /internal/leagues)
│   ├── service/       LeagueService (이번 주 리그, 순위), LeagueRanking (순위 계산), LeagueCreator (리그 생성), LeagueSettleService와 LeagueSettler (정산 배치), QuestService (퀘스트 조회, 보상, 진행도 기록), WeeklyQuestCreator (주간 퀘스트 생성), BadgeService (배지 지급, 목록), BadgeSeeder (배지 시드), StreakService (스트릭 배치), QuizService, QuizBank (문제 은행), GameWeek (한 주의 시작과 끝)
│   ├── scheduler/     GameScheduler (스트릭 매일 00:05, 정산 매주 월 00:10)
│   └── data/
│       ├── dto/           LeagueResponseDto, QuestResponseDto, QuestClaimResponseDto, BadgeResponseDto, QuizResponseDto, QuizAnswerRequestDto, QuizAnswerResponseDto, StreakCheckRequestDto, StreakCheckResponseDto, LeagueSettleRequestDto, LeagueSettleResponseDto
│       ├── entity/        League, LeagueMember, Quest, UserQuest, Badge, UserBadge, GardenQuiz
│       ├── enums/         QuestType, QuestStatus, BadgeCode
│       ├── exception/     QuestNotFoundException, QuestNotCompletedException, QuestAlreadyClaimedException, QuizNotFoundException, QuizAlreadyAnsweredException, QuizChoiceInvalidException
│       └── repository/    LeagueRepository, LeagueMemberRepository, QuestRepository, UserQuestRepository, BadgeRepository, UserBadgeRepository, GardenQuizRepository
├── notification/                   # 알림 저장, 조회 (기기 푸시 없음)
│   ├── controller/    NotificationController (/notifications)
│   ├── service/       NotificationService (send, 목록, 읽음)
│   └── data/
│       ├── dto/           NotificationSendDto, NotificationItemDto, NotificationListResponseDto, NotificationReadResponseDto
│       ├── entity/        Notification
│       ├── enums/         NotificationType
│       ├── exception/     NotificationNotFoundException
│       └── repository/    NotificationRepository
├── harvest/                        # 수확 안내, 주말 방문 예약, 나들이 추천
│   ├── controller/    HarvestController (/gardens/{gardenId}/harvest, visit-slots, outings), VisitController (/visits)
│   ├── service/       HarvestService, VisitService, HarvestGardenService (텃밭 조회와 소유 확인), VisitSlotPolicy (슬롯 규칙), OutingSeeder (시드), KoreanParticle
│   ├── garden/        HarvestGardenReader (텃밭 도메인과의 접점), HarvestGarden, HarvestGardenConfiguration (구현체가 없을 때의 기본 빈). 구현체는 garden의 GardenHarvestReader
│   └── data/
│       ├── dto/           HarvestResponseDto, VisitSlotResponseDto, VisitRequestDto, VisitResponseDto, OutingResponseDto
│       ├── entity/        Visit, Outing
│       ├── enums/         VisitStatus, OutingType
│       ├── exception/     GardenNotFoundException, GardenAccessDeniedException, VisitNotFoundException, VisitAccessDeniedException, VisitAlreadyReservedException, VisitAlreadyCanceledException, VisitSlotFullException, VisitSlotNotBookableException, VisitCancelDeadlinePassedException
│       └── repository/    VisitRepository, OutingRepository
├── weather/                        # 기상청 날씨 수집, 저장, 조회
│   ├── controller/    WeatherSyncController (/internal/weather)
│   ├── service/       WeatherService (현재 날씨, 예보, 일별 집계, 저장), WeatherSyncService (수집 배치), WeatherConditionResolver (condition 규칙), DemoWeatherStore (시연 날씨 덮어쓰기)
│   ├── scheduler/     WeatherSyncScheduler (매시 15분)
│   ├── client/        KmaClient (기상청 호출, 재시도), KmaProperties (weather.kma 설정), KmaBaseTime (발표시각 계산), KmaItemConverter (응답을 시각별 한 건으로 묶기), KmaResponse
│   ├── farm/          WeatherFarmReader (농장 도메인과의 접점), WeatherFarm, WeatherFarmConfiguration (구현체가 없을 때 설정의 기본 격자를 주는 기본 빈). 구현체는 farm의 FarmWeatherFarmReader
│   └── data/
│       ├── dto/           CurrentWeatherDto, WeatherForecastData, WeatherSyncRequestDto, WeatherSyncResponseDto
│       ├── entity/        WeatherForecast, DailyWeather
│       ├── enums/         WeatherCondition
│       ├── exception/     KmaApiException
│       └── repository/    WeatherForecastRepository, DailyWeatherRepository
└── demo/                           # 시연 컨트롤 (지금은 날씨만)
    ├── controller/    DemoController (/demo)
    ├── service/       DemoService
    └── data/
        ├── dto/           DemoWeatherRequestDto, DemoWeatherResponseDto, DemoStateResponseDto
        └── exception/     DemoWeatherNotAllowedException
```

## API

모든 응답은 `GlobalResponse<T>`로 감싸진다. 경로는 명세(`api-spec.md`)대로 접두어 없이 쓴다 (`/api/v1` 없음, 2026-11-11 결정).

| Method | Path | 권한 | 요청 | 응답 `data` | 설명 |
| --- | --- | --- | --- | --- | --- |
| POST | `/auth/signup` | 공개 | `SignUpRequestDto` | `AuthResponseDto` | 회원가입 후 바로 토큰 발급. 역할은 `USER`, `nextStep=ROLE_SELECT` |
| GET | `/auth/username-check?username={아이디}` | 공개 | 쿼리 `username` | `UsernameCheckResponseDto` | 아이디 중복 확인 |
| POST | `/auth/login` | 공개 | `LoginRequestDto` | `AuthResponseDto` | 로그인, `nextStep`으로 이동 화면 안내 |
| POST | `/auth/refresh` | 공개 | `RefreshRequestDto` | `TokenRefreshResponseDto` | RT로 AT, RT 재발급 (RT 로테이션) |
| POST | `/auth/logout` | USER, OWNER, CARETAKER | 헤더 `Authorization: Bearer <AT>` | `null` | AT, RT 블랙리스트 등록 및 RT 삭제 |
| GET | `/users/me` | USER, OWNER, CARETAKER | 없음 | `UserMeResponseDto` | 내 정보, 역할, `nextStep` |
| PUT | `/users/me/role` | USER, OWNER, CARETAKER | `RoleChangeRequestDto` | `RoleChangeResponseDto` | 역할 선택 및 변경. 이전 토큰을 무효로 만들고 새 토큰 발급 |
| GET | `/users/me/badges` | OWNER | 없음 | `List<BadgeResponseDto>` | 내가 얻은 배지 (얻은 순서) |
| GET | `/farms?district=&from=&cursor=&limit=` | USER, OWNER, CARETAKER | 쿼리 `district`, `from` (선택, `District`), `CursorPageRequest` | `CursorPageResponse<FarmItemDto>` | 주말농장 목록 (등록순). `from`을 주면 그 구의 대표 좌표에서의 거리를 함께 준다 |
| GET | `/farms/{farmId}/plots/{plotCode}` | USER, OWNER | 없음 | `PlotPreviewResponseDto` | 칸 번호로 작물과 담당 관리사 미리보기. 이미 분양된 칸은 `C4093` |
| GET | `/crops` | USER, OWNER, CARETAKER | 없음 | `List<CropResponseDto>` | 작물과 단계별 적산온도 기준 |
| POST | `/owners/me/profile` | USER, OWNER | `OwnerProfileRequestDto` | `OwnerProfileCreateResponseDto` | 주인 프로필 등록과 텃밭 시작 (칸 분양, 텃밭 생성). 역할을 먼저 골라야 한다 (`USER`는 `C4031`) |
| PATCH | `/owners/me/profile` | OWNER | `OwnerProfileUpdateRequestDto` | `OwnerProfileResponseDto` | 닉네임, 사는 구 수정 (보낸 필드만) |
| GET | `/gardens/{gardenId}/home` | OWNER | 없음 | `GardenHomeResponseDto` | 홈 한 번에 조회 (캐릭터, 날씨, 스트릭, XP, D-day, 관리사, 최근 돌봄, 미확인 이벤트 여부) |
| POST | `/gardens/{gardenId}/cheers` | OWNER | 없음 | `CheerResponseDto` | 오늘의 응원. 하루 1회, XP 10, 스트릭 갱신 |
| POST | `/care-logs/{careLogId}/hearts` | OWNER | 없음 | `HeartResponseDto` | 감사 하트. 돌봄 기록당 1회, XP 5, 관리사에게 알림 |
| GET | `/gardens/{gardenId}/diary?filter=&cursor=&limit=` | OWNER | 쿼리 `filter` (`ALL` 기본, `PHOTO`, `STAGE`), `CursorPageRequest` (cursor는 `YYYY-MM-DD`) | `CursorPageResponse<DiaryItemDto>` | 성장 일기 (최신순) |
| GET | `/gardens/{gardenId}/events` | OWNER | 없음 | `List<EvolutionEventResponseDto>` | 미확인 진화 이벤트 (오래된 순) |
| POST | `/events/{eventId}/ack` | OWNER | 없음 | `EventAckResponseDto` | 진화 이벤트 확인과 보상 (XP, 배지). 한 번만 |
| GET | `/leagues/current` | OWNER | 없음 | `LeagueResponseDto` | 이번 주 리그 (같은 농장 텃밭)와 내 순위 |
| GET | `/quests/weekly` | OWNER | 없음 | `List<QuestResponseDto>` | 이번 주 퀘스트와 내 진행도 |
| POST | `/quests/{questId}/claim` | OWNER | 없음 | `QuestClaimResponseDto` | 달성한 퀘스트의 보상 받기. 미달성 `C4004`, 이미 받음 `C4095` |
| GET | `/gardens/{gardenId}/quizzes/today` | OWNER | 없음 | `QuizResponseDto` | [확장] 오늘의 텃밭 퀴즈 (텃밭마다 하루 한 문제) |
| POST | `/quizzes/{quizId}/answers` | OWNER | `QuizAnswerRequestDto` | `QuizAnswerResponseDto` | [확장] 퀴즈 답 제출. 한 번만, 맞히면 XP 10 |
| GET | `/notifications?cursor=&limit=` | OWNER, CARETAKER | `CursorPageRequest` | `NotificationListResponseDto` | 내 알림 목록 (최신순)과 안 읽은 수 |
| PATCH | `/notifications/{notificationId}/read` | OWNER, CARETAKER | 경로 `notificationId` (`ntf_3` 또는 `all`) | `NotificationReadResponseDto` | 읽음 처리. `all`이면 모두 읽음. 이미 읽은 알림은 `readCount` 0 |
| GET | `/gardens/{gardenId}/harvest` | OWNER | 없음 | `HarvestResponseDto` | 수확 안내 (현재 단계, 예측 수확일, D-day, 함께 돌본 일수) |
| GET | `/gardens/{gardenId}/visit-slots?from=&to=` | OWNER | 쿼리 `from`, `to` (선택, `YYYY-MM-DD`) | `List<VisitSlotResponseDto>` | 주말 방문 슬롯과 예약 가능 여부. 생략하면 내일부터 2주 |
| POST | `/visits` | OWNER | `VisitRequestDto` | `VisitResponseDto` | 주말 방문 예약, 담당 관리사에게 알림 |
| DELETE | `/visits/{visitId}` | OWNER | 없음 | `null` | 방문 전날까지 취소, 담당 관리사에게 알림 |
| GET | `/gardens/{gardenId}/outings` | OWNER | 없음 | `List<OutingResponseDto>` | 농장에서 가까운 순으로 나들이 장소 최대 6곳 |
| GET | `/demo/state` | ADMIN + `X-Demo-Mode` | 없음 | `DemoStateResponseDto` | 시연 상태 (지금은 날씨만 채워짐) |
| PUT | `/demo/weather` | ADMIN + `X-Demo-Mode` | `FarmItemDto` | `farmId` (`farm_3`), `name`, `district`, `address`, `latitude`, `longitude`, `distanceKm` (`from`이 없으면 `null`) |
| `PlotPreviewResponseDto` | `farmId`, `plotCode`, `available` (성공 응답에서는 항상 true), `crop` (`cropId`, `name`, `characterName`), `caretaker` (`caretakerId`, `name`, `avatarUrl`. 배정 전이면 `null`), `monthlyFee` (가정치) |
| `CropResponseDto` | `cropId` (`crop_1`), `name`, `characterName`, `baseTemp`, `stages` (`stage`, `level`, `requiredGdd`), `schedule` |
| `FarmSyncResponseDto` | `totalFarms`, `addedFarms`, `updatedFarms`, `skippedFarms` (읍면동을 찾지 못한 농장 이름) |
| `OwnerProfileRequestDto` | `nickname` (`@NotBlank @Size(max=20)`), `district` (`@NotNull`, `SUJI`와 `GIHEUNG`만 허용), `farmId` (`@NotBlank`), `plotCode` (`@NotBlank @Size(max=20)`) |
| `OwnerProfileCreateResponseDto` | `gardenId`, `characterName`, `stage`, `nextStep` |
| `OwnerProfileUpdateRequestDto` | `nickname` (선택, 20자 이하, 공백만은 안 됨), `district` (선택, `SUJI`와 `GIHEUNG`만 허용) |
| `OwnerProfileResponseDto` | `nickname`, `district`, `gardenId` |
| `GardenHomeResponseDto` | `gardenId`, `farmName`, `plotCode`, `nickname` (주인 프로필 별명, 없으면 사용자 이름), `character` (`name`, `stage`, `level`, `mood`, `animation`, `message`), `weather` (`CurrentWeatherDto`, 수집된 날씨가 없으면 `null`), `streak` (`days`, `cheeredToday`), `xp` (`total`, `today`), `harvest` (`expectedDate`, `dDay`. 계산 전이면 둘 다 `null`), `caretaker` (배정 전이면 `null`), `recentCareLog` (`careLogId`, `summary`, `photoUrl`, `createdAt`, `hearted`. 없으면 `null`), `hasUnreadEvent` |
| `CheerResponseDto` | `streakDays`, `xpGained`, `totalXp`, `mood` |
| `HeartResponseDto` | `careLogId`, `hearted`, `xpGained` |
| `DiaryItemDto` | `type` (`CARE_LOG`, `STAGE_CHANGE`, `REST_DAY`), `date`. 종류에 따라 `careLogId`, `taskTypes`, `summary`, `photoUrl`, `caretakerName`, `hearted` (CARE_LOG), `eventId`, `fromStage`, `toStage` (STAGE_CHANGE), `message` (REST_DAY). 해당하지 않는 필드는 `null` |
| `EvolutionEventResponseDto` | `eventId` (`evt_3`), `fromStage`, `toStage`, `occurredAt`, `reward` (`xp`, `badgeCode`. 배지가 없으면 `null`) |
| `EventAckResponseDto` | `eventId`, `xpGained`, `totalXp`, `badge` (`code`, `name`. 배지가 없으면 `null`) |
| `LeagueResponseDto` | `leagueId` (`lg_7`), `weekStart` (월요일), `weekEnd` (일요일), `myRank`, `members` (`rank`, `gardenId`, `nickname`, `weeklyXp`, `me`. 순위순) |
| `QuestResponseDto` | `questId` (`qst_21`), `title`, `progress` (목표까지만 센다), `goal`, `rewardXp`, `status` (`IN_PROGRESS`, `COMPLETED`, `CLAIMED`) |
| `QuestClaimResponseDto` | `questId`, `xpGained`, `totalXp`, `status` (`CLAIMED`) |
| `BadgeResponseDto` | `code`, `name`, `description`, `acquiredAt` |
| `QuizResponseDto` | `quizId` (`qz_31`), `question`, `choices`, `answered` |
| `QuizAnswerRequestDto` | `choiceIndex` (`@NotNull`, 0부터. 없는 보기면 C002) |
| `QuizAnswerResponseDto` | `correct`, `explanation`, `xpGained` (틀리면 0) |
| `StreakCheckRequestDto` | `date` (선택. 응원했는지 확인할 날, 생략 시 어제) |
| `StreakCheckResponseDto` | `broken` (스트릭이 끊긴 텃밭 수) |
| `LeagueSettleRequestDto` | `weekStart` (선택. 정산할 주에 속한 날, 생략 시 지난주. 월요일이 아니면 그 주의 월요일로 맞춘다) |
| `LeagueSettleResponseDto` | `settledLeagues` (이번 실행으로 정산한 리그 수), `createdQuests` (새 주에 만든 퀘스트 수) |
| `DemoWeatherRequestDto` | `DemoWeatherResponseDto` | 시연 날씨 덮어쓰기 (SUNNY, RAIN, HOT) |
| POST | `/demo/reset` | ADMIN + `X-Demo-Mode` | 없음 | `DemoStateResponseDto` | 맑음, FLOWER, 가상 일수 0으로 초기화 |
| PUT | `/demo/stage` | ADMIN + `X-Demo-Mode` | `DemoStageRequestDto` (`stage`) | `DemoFastForwardResponseDto` | 시연 성장 단계 선택 (SEED, SPROUT, FLOWER, FRUIT) |
| POST | `/demo/fast-forward` | ADMIN + `X-Demo-Mode` | `DemoFastForwardRequestDto` (`days`) | `DemoFastForwardResponseDto` | 시연 시간 빨리 감기 |
| POST | `/internal/weather/sync` | SYSTEM (`X-Service-Key`) | `WeatherSyncRequestDto` (본문 생략 가능) | `WeatherSyncResponseDto` | 기상청 단기예보와 초단기실황 수집. 스케줄러가 매시 15분에 같은 로직을 돌린다 |
| POST | `/internal/farms/sync` | SYSTEM (`X-Service-Key`) | 없음 | `FarmSyncResponseDto` | 공공데이터 주말농장 파일 동기화. 스케줄러는 매주 월 03:00 |
| POST | `/internal/streaks/check` | SYSTEM (`X-Service-Key`) | `StreakCheckRequestDto` (본문 생략 가능) | `StreakCheckResponseDto` | 전날 응원이 없는 텃밭의 스트릭 끊김 처리. 스케줄러는 매일 00:05 |
| POST | `/internal/leagues/settle` | SYSTEM (`X-Service-Key`) | `LeagueSettleRequestDto` (본문 생략 가능) | `LeagueSettleResponseDto` | 지난주 리그 정산, 새 주의 리그와 퀘스트 생성. 스케줄러는 매주 월 00:10 |

아직 없는 API: 관리사 프로필과 작업 (`/caretakers/**`, `/caretaker/**`, `/tts`, `/gardens/*/stage-checks`), `POST /internal/ai/daily-tasks`, `POST /internal/ai/growth-simulation`. 돌봄 기록 도메인이 없어서 `POST /care-logs/{careLogId}/hearts`는 항상 `C404`다 ([알려진 이슈](#알려진-이슈--결정-대기)).

### 경로별 권한 (`SecurityConfiguration`)

`api-spec.md` 4장의 권한 열을 그대로 옮겼다. 아직 컨트롤러가 없는 도메인의 경로도 미리 매핑해 두었으므로, 새 도메인은 명세와 권한이 다를 때만 이 표와 코드를 고친다. 위에서부터 먼저 맞는 규칙이 적용된다.

| 순서 | 경로 | 권한 |
| --- | --- | --- |
| 1 | `/swagger-ui/**`, `/v3/api-docs/**`, `/swagger-resources/**`, `/webjars/**` | 공개 |
| 2 | GET `/uploads/**` | 공개 (로컬 저장소 파일) |
| 3 | `/internal/**` | SYSTEM (`X-Service-Key` 헤더) |
| 4 | `/demo/**` | ADMIN 토큰 + `X-Demo-Mode: true` 헤더 |
| 5 | POST `/auth/signup`, `/auth/login`, `/auth/refresh`, GET `/auth/username-check` | 공개 |
| 6 | `/auth/logout` | USER, OWNER, CARETAKER |
| 7 | `/users/me/badges` | OWNER |
| 8 | `/users/me/**` | USER, OWNER, CARETAKER |
| 9 | `/farms/*/plots/**` | USER, OWNER |
| 10 | `/farms/**`, `/crops/**` | USER, OWNER, CARETAKER |
| 11 | POST `/owners/me/profile` | USER, OWNER |
| 12 | `/owners/**` | OWNER |
| 13 | POST `/caretakers/me/profile` | USER, CARETAKER |
| 14 | `/caretakers/**` | CARETAKER |
| 15 | `/tts` | USER, CARETAKER |
| 16 | `/caretaker/**`, `/gardens/*/stage-checks` | CARETAKER |
| 17 | `/gardens/**`, `/care-logs/**`, `/events/**`, `/leagues/**`, `/quests/**`, `/quizzes/**`, `/visits/**` | OWNER |
| 18 | `/notifications/**` | OWNER, CARETAKER |
| 19 | 그 외 모든 경로 | 인증만 필요 |

ADMIN은 RoleHierarchy로 USER, OWNER, CARETAKER 권한을 갖는다. SYSTEM은 `UserRole`이 아니고 필터가 헤더를 확인해 부여하는 권한이라 ADMIN도 헤더가 있어야 한다. 시연 API는 ADMIN 토큰이 필요하고 헤더도 함께 있어야 한다.

### 내부 배치, 시연 API

| 경로 | 필터 | 통과 조건 | 실패 응답 | principal |
| --- | --- | --- | --- | --- |
| `/internal/**` | `InternalServiceKeyFilter` | `X-Service-Key` 헤더가 `internal.service-key`와 같음. 서버에 키가 비어 있으면 항상 거부 | 401 `C4014` | `system` (`ROLE_SYSTEM`) |
| `/demo/**` | `DemoModeFilter` | `demo.enabled=true`이고 `X-Demo-Mode: true` 헤더가 있음. 통과해도 권한을 주지 않으며 JWT의 ADMIN 권한이 따로 필요하다 | 403 `C4032` (토큰 없음 401 `C4011`, ADMIN 아님 403 `C403`) | JWT 사용자 (ADMIN) |

`/internal/**`에서는 JWT가 함께 와도 헤더 검증 결과가 인증을 대신한다. `/demo/**`는 헤더만으로는 통과할 수 없어서, 배포 후에도 ADMIN 계정 없이는 시연 데이터를 바꿀 수 없다. 가입 API로는 ADMIN을 만들 수 없으므로 시연 계정은 DB에서 직접 바꾼다 (`UPDATE user SET role = 'ADMIN' WHERE username = '...'`). `/demo`는 `DemoController`가 있고 (state, weather, reset), `/internal`은 `WeatherSyncController`(`/internal/weather/sync`), `FarmSyncController`(`/internal/farms/sync`), `GameBatchController`(`/internal/streaks/check`, `/internal/leagues/settle`)가 있다.

### DTO

| DTO | 필드 |
| --- | --- |
| `SignUpRequestDto` | `name` (`@NotBlank @Size(max=20)`), `username` (`@NotBlank`, 영문 소문자와 숫자 4~20자), `password` (`@NotBlank @Size(8~64)`) |
| `LoginRequestDto` | `username`, `password` (`@NotBlank`) |
| `RefreshRequestDto` | `refreshToken` (`@NotBlank`) |
| `RoleChangeRequestDto` | `role` (`@NotNull UserRole`, `OWNER`와 `CARETAKER`만 허용) |
| `AuthResponseDto` | `userId`, `accessToken`, `refreshToken`, `expiresIn` (초), `role`, `nextStep` (조립은 `AuthService.createAuthResponse` 한 곳에서만) |
| `TokenRefreshResponseDto` | `accessToken`, `refreshToken`, `expiresIn` (초) |
| `UsernameCheckResponseDto` | `username`, `available` |
| `UserMeResponseDto` | `userId`, `name`, `username`, `role`, `nextStep`, `owner` (`nickname`, `district`, `gardenId`. 주인 프로필이 없으면 `null`), `caretaker` (`caretakerId`, `name`, `farmId`, `avatarUrl`. 관리사 프로필 도메인이 없어 항상 `null`) |
| `RoleChangeResponseDto` | `role`, `accessToken`, `refreshToken`, `nextStep` |
| `NotificationListResponseDto` | `items` (`NotificationItemDto` 목록), `unreadCount`, `nextCursor` |
| `NotificationItemDto` | `notificationId` (`ntf_3`), `type`, `title`, `body`, `target` (`screen`, `id`. 이동 대상이 없으면 `null`), `read`, `createdAt` |
| `NotificationReadResponseDto` | `readCount` (이번에 읽음 처리한 수), `unreadCount` (남은 수) |
| `HarvestResponseDto` | `stage` (텃밭의 현재 단계), `expectedDate`, `dDay` (지났으면 0, 수확일이 없으면 `null`), `harvestable`, `daysCaredTogether` (시작한 날을 1일로 셈), `careLogCount`, `caretakerName`, `message` |
| `VisitSlotResponseDto` | `slotId` (`slot_20261128_10`), `date`, `startTime`, `endTime` (`HH:mm`), `available` |
| `VisitRequestDto` | `gardenId`, `slotId` (`@NotBlank`), `headcount` (선택, `@Min(1)`, 생략 시 1), `memo` (선택, `@Size(max=200)`) |
| `VisitResponseDto` | `visitId` (`vst_5`), `date`, `startTime` (`HH:mm`), `status` (`RESERVED`, `CANCELED`) |
| `OutingResponseDto` | `type` (`EXPERIENCE_FARM`, `RESTAURANT`), `name`, `distanceKm` (농장 위치를 모르면 `null`), `address`, `description` |
| `WeatherSyncRequestDto` | `farmIds` (선택, `farm_3` 형식의 목록. 비어 있으면 등록된 모든 농장. 형식이 틀리면 C002) |
| `WeatherSyncResponseDto` | `syncedFarms` (실황과 예보를 모두 저장한 농장 수), `failedFarms` (하나라도 받지 못한 농장. `farm_3`, 기본 격자면 `grid_64_119`) |
| `DemoWeatherRequestDto` | `condition` (`@NotNull`, `SUNNY`, `RAIN`, `HOT`만 허용. 그 외는 C002) |
| `DemoWeatherResponseDto` | `condition`, `animation` (`sunny_happy`, `rain_umbrella`, `hot_sweat`), `weatherNotice` (작업 생성이 붙기 전까지 `null`), `regeneratedTasks` (작업 생성이 붙기 전까지 0) |
| `DemoStateResponseDto` | `weather` (덮어쓴 적이 없으면 `null`), `stage`, `stageProgress`, `fastForwardDays`, `todayTaskGardens` (뒤의 넷은 해당 도메인이 붙기 전까지 `null`) |

`userId`는 `usr_` 접두어에 PK를 붙인 문자열이다 (예: `usr_12`, `User.getPublicId()`).

## 공통 모듈

### 외부 ID (`IdPrefix`)

명세의 접두어 ID 형식을 쓰기로 정했다 (2026-11-11). DB PK는 `Long`(IDENTITY) 그대로 두고, API로 주고받을 때만 `IdPrefix.XXX.format(id)`로 `접두어_숫자` 문자열을 만든다. 받을 때는 `IdPrefix.XXX.parse(publicId)`로 PK를 얻고 형식이 틀리면 `C002`다. 명세 예시의 `usr_minji`, `gdn_b14` 같은 값은 예시일 뿐이고 실제 값은 `usr_12`, `gdn_3`처럼 나간다.

| `IdPrefix` | 접두어 | `IdPrefix` | 접두어 |
| --- | --- | --- | --- |
| `USER` | `usr` | `CARE_LOG` | `cl` |
| `GARDEN` | `gdn` | `VOICE_MEMO` | `vm` |
| `FARM` | `farm` | `PHOTO` | `pht` |
| `CARETAKER` | `crt` | `EVENT` | `evt` |
| `TASK` | `tsk` | `VISIT` | `vst` |
| `NOTIFICATION` | `ntf` | `CROP` | `crop` |
| `LEAGUE` | `lg` | `QUEST` | `qst` |
| `QUIZ` | `qz` | | |

방문 슬롯 ID(`slot_20261128_10`)는 DB에 없는 계산 값이라 `IdPrefix`를 쓰지 않고 `VisitSlotPolicy`가 만들고 읽는다.

### 커서 페이지네이션

| 클래스 | 내용 |
| --- | --- |
| `CursorPageRequest` | 쿼리 `cursor`(생략 시 첫 페이지), `limit`(기본 20, 1~50, 위반 시 `C002`). `getFetchSize()`는 limit + 1, `getCursorId(IdPrefix)`는 커서를 PK로 변환 |
| `CursorPageResponse<T>` | `items`, `nextCursor`(마지막 페이지면 `null`). `of(limit + 1건 조회 결과, limit, 커서 추출 함수)`로 만들고 `map`으로 DTO 변환 |

`nextCursor`는 이번 페이지 마지막 항목의 외부 ID다. 사용 예시는 [development-guide.md](development-guide.md#커서-페이지네이션)를 본다.

### 파일 업로드

| 항목 | 내용 |
| --- | --- |
| 진입점 | `FileUploadService.uploadImage(MultipartFile, directory)`, `uploadAudio(MultipartFile, directory)`. 반환값은 업로드 URL |
| 사진 검증 | 확장자 jpg, jpeg, png이고 파일 앞부분(매직 넘버)이 실제 JPEG, PNG여야 함. 10MB 이하 |
| 음성 검증 | 확장자 webm, m4a, mp4, wav이고 파일 앞부분이 실제 형식과 맞아야 함 (webm은 EBML 헤더, m4a와 mp4는 `ftyp` 박스, wav는 RIFF/WAVE). 헤더에서 길이를 읽으면 60초 이하 (wav는 `data` 크기와 초당 바이트 수, m4a와 mp4는 `mvhd` 박스). 길이를 읽을 수 없거나 0이면 (크롬 webm, 사파리 조각 mp4) 5MB 이하만 받는다. 60초 녹음 제한은 프론트도 건다 |
| 위반 시 | `C4001` (multipart 상한 초과도 `C4001`) |
| 저장 | 원본 이름 대신 `UUID.확장자`로 `FileStorage.store` 호출 |
| 저장소 | `FileStorage` 인터페이스. 현재 구현은 `LocalFileStorage` (`file.storage=local`): `file.upload-dir/{directory}/{파일}`에 저장하고 `file.base-url/uploads/{directory}/{파일}`을 돌려줌. S3는 구현체를 추가하고 `file.storage` 값으로 고른다 |
| multipart 상한 | 파일 20MB, 요청 25MB (`spring.servlet.multipart`) |

### 날짜와 시각 직렬화

- `LocalDate`는 `YYYY-MM-DD`, `OffsetDateTime`은 `2026-11-10T09:30:00+09:00` 형식으로 나간다 (`spring.jackson.time-zone=Asia/Seoul`, `write-dates-with-context-time-zone=true`).
- 다른 오프셋으로 들어온 시각도 +09:00으로 바꿔 읽고 쓴다.
- 엔티티의 `LocalDateTime`을 응답에 담을 때는 `DateTimes.toOffset(...)`으로 `OffsetDateTime`으로 바꾼다 (초 단위로 자름).

### 외부 API, AI 클라이언트

- `externalRestClient` (`RestClient`): 연결 타임아웃 `external.http.connect-timeout`(3초), 읽기 타임아웃 `external.http.read-timeout`(30초). 도메인 클라이언트는 `externalRestClient.mutate().baseUrl(...).build()`로 파생해서 쓴다.
- `LlmClient.complete(systemPrompt, userPrompt)`, `SttClient.transcribe(audio, filename)`, `TtsClient.synthesize(text)`: 인터페이스만 있다. 제공자가 정해지지 않아 구현체는 아직 없고, 기본 빈은 호출 즉시 `AiClientException`(`C502`)을 던진다. 구현체를 `@Component`로 등록하면 기본 빈은 자동으로 빠진다.

### 스케줄러

`SchedulingConfiguration`이 `@EnableScheduling`을 켠다. `scheduler.enabled=false`면 `@Scheduled`가 돌지 않는다. 배치 작업 구조는 [development-guide.md](development-guide.md#배치-작업)를 본다. 등록된 배치는 아래와 같다.

| 스케줄러 | 주기 | 서비스 메서드 | 내부 API |
| --- | --- | --- | --- |
| `WeatherSyncScheduler` | 매시 15분 | `WeatherSyncService.sync` | `POST /internal/weather/sync` |
| `FarmSyncScheduler` | 매주 월 03:00 | `FarmSyncService.sync` | `POST /internal/farms/sync` |
| `GameScheduler.checkStreaks` | 매일 00:05 | `StreakService.check` | `POST /internal/streaks/check` |
| `GameScheduler.settleLeagues` | 매주 월 00:10 | `LeagueSettleService.settle` | `POST /internal/leagues/settle` |

### 알림 (`NotificationService.send`)

프론트가 웹이라 기기 푸시(FCM)는 보내지 않는다 (2026-11-11 결정). 알림은 DB에 저장하고 화면이 가져가는 방식이다.

1. 알림이 필요한 도메인은 `NotificationService.send(NotificationSendDto)` 하나만 호출한다. 사용 예시는 [development-guide.md](development-guide.md#알림-발송)를 본다.
2. 호출한 쪽의 트랜잭션에 참여해 `Notification`을 저장한다. 그 작업이 롤백되면 알림도 남지 않는다.
3. 화면은 `GET /notifications`로 목록과 `unreadCount`를 가져온다. 새 알림을 바로 보여주려면 화면에 들어올 때와 일정 간격으로 다시 조회한다 (서버가 먼저 밀어주는 통로는 없다).

| `NotificationType` | 받는 사람 | 의미 | 보내는 곳 |
| --- | --- | --- | --- |
| `CARE_DONE` | 주인 | 관리사가 작업을 끝냄 | 아직 없음 (작업 완료) |
| `HEART` | 관리사 | 주인이 감사 하트를 보냄 | `CareLogHeartService.sendHeart` |
| `STAGE_UP` | 주인 | 작물 단계가 오름 | `EvolutionEventService.advanceStage` (부르는 쪽인 성장 시뮬레이션, 단계 확인은 아직 없음) |
| `VISIT_RESERVED` | 관리사 | 주말 방문 예약 | `VisitService.reserve` |
| `VISIT_CANCELED` | 관리사 | 방문 예약 취소 | `VisitService.cancel` |
| `STREAK_RISK` | 주인 | 스트릭이 끊길 위험 | 아직 없음 (스트릭 배치) |
| `QUEST_DONE` | 주인 | 주간 퀘스트 달성 | `QuestService.record` (목표를 채우는 순간) |

### 수확, 방문 예약

**텃밭 접점 (`HarvestGardenReader`).** 수확과 방문이 필요로 하는 값만 `HarvestGarden`으로 정의하고 `HarvestGardenReader.findGarden(gardenId)`로 받는다. 구현체는 텃밭 도메인의 `GardenHarvestReader`다. `HarvestGarden`의 필드는 `gardenId`, `stage`, `ownerUsername`, `ownerNickname`, `characterName`, `farmId`, `farmLatitude`, `farmLongitude`, `caretakerUsername`, `caretakerName`, `startedOn`, `expectedHarvestDate`, `harvestable`, `careLogCount`다. `careLogCount`는 돌봄 기록 도메인이 생기기 전까지 0이다.

**방문 슬롯 규칙 (`VisitSlotPolicy`).** 슬롯은 테이블에 두지 않고 규칙으로 계산한다.

| 항목 | 규칙 |
| --- | --- |
| 요일 | 토요일, 일요일만 |
| 시간 | 10시, 11시, 14시, 15시에 시작하는 1시간 (관리사가 맞이할 수 있는 시간. 관리사별 가능 시간 데이터는 없어서 고정값) |
| 정원 | 한 농장의 한 슬롯에 2건까지 (`RESERVED`만 센다) |
| 예약 가능 | 내일 이후의 슬롯만. 오늘과 지난 날짜는 `available=false`, 예약하면 C002 |
| 슬롯 ID | `slot_{yyyyMMdd}_{HH}` (예: `slot_20261128_10`). 형식이 틀리거나 규칙에 없는 날짜, 시간이면 C002 |
| 조회 기간 | `from`, `to` 생략 시 내일부터 14일. `to`가 `from`보다 앞이거나 31일을 넘으면 C002 |

**예약과 취소.**

1. 예약은 내 텃밭인지 (`C404`, `C403`), 예약 가능한 슬롯인지 (C002), 그 텃밭에 같은 날 예약이 있는지 (`C4095`), 슬롯 정원이 찼는지 (`C4096`) 순서로 확인한다.
2. 같은 날 중복은 `Visit.reservedKey`(`텃밭ID:날짜`, 유니크)가 동시 요청까지 막는다. 취소하면 이 값을 비워 다시 예약할 수 있다.
3. 취소는 방문 전날까지만 된다 (당일부터 `C4005`). 내 예약이 아니면 `C403`, 이미 취소했으면 `C4095`다.
4. 예약과 취소 모두 담당 관리사에게 알림(`VISIT_RESERVED`, `VISIT_CANCELED`)을 보낸다. 담당 관리사가 없으면 알림 없이 처리한다.

**나들이 추천.** `Outing`을 농장 좌표와의 직선 거리(하버사인, 소수 첫째 자리 km) 순으로 최대 6곳 내려준다. `OutingSeeder`가 부팅할 때 `outing` 테이블이 비어 있으면 처인구의 실제 장소 7곳을 넣는다. 체험농장 3곳은 농어촌체험휴양마을 데이터(좌표 포함), 식당 4곳은 모범음식점 데이터에서 골랐다. 출처와 고른 기준은 [public-data.md](public-data.md)를 본다.

### 날씨

홈, 작업 생성, 성장 시뮬레이션은 저장소를 직접 보지 않고 `WeatherService`를 쓴다. 기상청 API 명세와 격자 표는 [public-data.md](public-data.md)에 있다.

**수집 배치 (`WeatherSyncService.sync`).** 스케줄러(매시 15분)와 `POST /internal/weather/sync`가 같은 메서드를 부른다.

1. `WeatherFarmReader.findFarms(farmIds)`로 수집할 농장과 격자를 얻는다. 구현체는 농장 도메인의 `FarmWeatherFarmReader`이고 등록된 농장의 격자(`Farm.nx`, `Farm.ny`)를 준다.
2. 여러 농장이 같은 격자에 있으면 한 번만 받는다.
3. 격자마다 단기예보(`getVilageFcst`)를 먼저 저장하고 초단기실황(`getUltraSrtNcst`)을 나중에 저장한다. 그래서 실황에 없는 강수확률과 하늘상태가 예보 값으로 남는다.
4. 발표시각은 `KmaBaseTime`이 고른다. 실황은 10분이 지난 가장 최근 정각, 단기예보는 10분이 지난 가장 최근 발표시각(02, 05, 08, 11, 14, 17, 20, 23시)이다.
5. `KmaClient`는 호출이 실패하거나 `resultCode`가 `00`이 아니면 `weather.kma.max-attempts`(기본 3)번까지 다시 시도하고, 끝까지 실패하면 `KmaApiException`을 던진다. 인증키가 로그에 남지 않도록 요청 주소는 기록하지 않는다.
6. 한 격자에서 실황이나 예보 하나가 실패해도 받은 쪽은 저장한다. 실패한 쪽은 앞서 저장된 값이 그대로 남아 조회에 쓰인다. 실패한 농장은 응답의 `failedFarms`에 담긴다.
7. 전체를 하나의 트랜잭션으로 묶지 않는다. 저장은 격자마다 `saveForecasts`의 트랜잭션에서 한다.
8. 인증키(`PUBLIC_DATA_SERVICE_KEY`)가 비어 있으면 호출하지 않고 모든 농장을 `failedFarms`로 돌려준다.

| 메서드 | 내용 |
| --- | --- |
| `getCurrentWeather(nx, ny)` | 현재 날씨 (`CurrentWeatherDto`: `condition`, `temperature`, `rainProbability`, `updatedAt`). 시연 날씨가 있으면 그 값이 우선하고, 없으면 지금 시각 이전의 가장 가까운 값, 그것도 없으면 가장 가까운 예보를 쓴다. 수집된 값이 없으면 비어 있다 |
| `getForecasts(nx, ny, from, to)` | 시간대별 예보. 시연 날씨는 반영하지 않는다 |
| `getDailyWeather(nx, ny, from, to)` | 일별 평균, 최고, 최저 기온 (적산온도 계산용) |
| `saveForecasts(nx, ny, List<WeatherForecastData>)` | 수집한 값을 저장한다. 격자와 시각이 같으면 덮어써 중복으로 쌓이지 않는다. 실황이 들어온 줄은 예보로 덮어쓰지 않고, 실황에 없는 강수확률과 하늘상태는 앞서 받은 예보 값을 남긴다. 값이 바뀐 날짜의 일별 집계도 다시 계산한다 |
| `aggregateDaily(nx, ny, date)` | 하루치 기온으로 `DailyWeather`를 다시 계산한다 |

**condition 규칙 (`WeatherConditionResolver`).** 위에서부터 먼저 맞는 것을 쓴다.

| 순서 | 조건 | condition |
| --- | --- | --- |
| 1 | 눈 (PTY 3, 7) | `COLD` |
| 2 | 비, 비/눈, 소나기, 빗방울 (PTY 1, 2, 4, 5, 6) | `RAIN` |
| 3 | 기온 30℃ 이상 | `HOT` |
| 4 | 기온 5℃ 이하 | `COLD` |
| 5 | 구름많음, 흐림 (SKY 3, 4) | `CLOUDY` |
| 6 | 그 외 | `SUNNY` |

강수확률은 condition에 쓰지 않는다 (지금 비가 오지 않는데 우산 캐릭터가 나오지 않도록). 기온 기준 30℃, 5℃는 임시로 정한 값이다.

**시연 날씨 (`DemoWeatherStore`).** `PUT /demo/weather`가 Redis `demo:weather`에 저장하고 `getCurrentWeather`가 먼저 본다. 시연 텃밭이 하나라서 격자를 가리지 않고 모든 조회에 적용한다. 기온과 강수확률은 상태별 고정값이다 (SUNNY 14.2℃ 10%, RAIN 12℃ 90%, HOT 31℃ 10%). `demo.enabled=false`면 값이 남아 있어도 무시한다. `POST /demo/reset`은 값을 지우지 않고 SUNNY로 둔다 (실제 날씨와 상관없이 시연이 같은 상태에서 시작하도록).

### 응답 형식

```json
// 성공
{ "data": { ... }, "error": { "code": null, "message": null } }

// 실패
{ "data": null, "error": { "code": "C4041", "message": "사용자를 찾을 수 없습니다." } }
```

## 텃밭과 게임

### 도메인 사이의 접점

아직 없는 도메인이 필요한 곳은 인터페이스로 받아 두었다. 기본 빈은 비어 있는 결과를 주고, 그 도메인이 구현체를 `@Component`로 등록하면 기본 빈이 빠진다 (`@ConditionalOnMissingBean`).

| 인터페이스 | 쓰는 곳 | 구현체 |
| --- | --- | --- |
| `HarvestGardenReader` (harvest) | 수확, 방문 | `GardenHarvestReader` (garden) |
| `WeatherFarmReader` (weather) | 날씨 수집 | `FarmWeatherFarmReader` (farm) |
| `OwnerSummaryReader` (user) | `/users/me`의 `owner` | `GardenOwnerSummaryReader` (garden) |
| `FarmCaretakerReader` (farm) | 칸 미리보기, 홈, 일기의 관리사 프로필 (`caretakerId`, `avatarUrl`) | 없음. 관리사 프로필 도메인(#5)이 등록한다. 그 전에는 이름만 나간다 |
| `GardenCareLogReader` (garden) | 홈의 최근 돌봄, 하트, 일기, 수확의 돌봄 횟수 | 없음. 돌봄 기록 도메인(#7)이 등록한다. 그 전에는 돌봄 기록이 없는 것으로 동작한다 |

### 농장, 칸, 작물

1. `Farm`은 공공데이터 "경기도 용인시 주말농장" 파일(`src/main/resources/data/weekend-farms.csv`, 8곳)에서 온다. 오픈 API가 아니라 한 번 올라온 파일이라 저장소에 넣어 두고 `FarmSyncService.sync`가 읽는다. 같은 농장은 `externalId`로 찾아 바뀐 값만 고친다. 출처는 [public-data.md](public-data.md)를 본다.
2. 원본에 좌표가 없어서 주소의 읍면동 대표 좌표(`YonginTown`)를 쓰고, 그 좌표로 기상청 격자(`KmaGrid`)를 구해 `nx`, `ny`에 넣는다.
3. `FarmSeeder`가 부팅할 때 작물(방울토마토, 기준온도 10℃)을 넣고, 농장이 하나도 없으면 한 번 동기화한다. `demo.enabled=true`면 시연 농장(`demo:theme`, 처인 테마 주말농장)과 칸 B-13, B-14, B-15를 넣는다.
4. `Plot`은 농장의 칸이다. 칸마다 작물과 담당 관리사가 정해져 있고, 주인이 프로필을 등록하면 `assigned=true`가 된다. 실제 농장의 칸 데이터는 없어서 지금은 시연 농장에만 칸이 있다.

### 주인 프로필과 텃밭 시작

`POST /owners/me/profile` 한 번으로 프로필을 만들고 칸을 분양받아 텃밭을 시작한다 (`OwnerProfileService.register`).

1. 역할 선택 전(`USER`)이면 `C4031`, 이미 프로필이 있으면 `C4094`, 구가 `SUJI`, `GIHEUNG`이 아니면 `C002`, 없는 농장이나 칸이면 `C404`다.
2. 칸을 잠그고(`PlotRepository.findPlotForUpdate`) 이미 분양됐으면 `C4093`이다. 같은 칸에 동시에 들어온 요청은 하나만 성공한다.
3. `OwnerProfile`과 `Garden`을 만들고 `User.profileRegistered`를 true로 바꾼다. 텃밭은 칸의 작물과 담당 관리사를 옮겨 받고 `SEED` 단계, 기분 `HAPPY`로 시작한다.
4. 주인 한 명에 텃밭은 하나다 (`garden.owner_user_id` 유니크).

### 홈, 응원, 하트, XP

1. 홈의 캐릭터 애니메이션은 `CharacterAnimation`이 기분과 날씨로 정한다. 기분이 `SAD`면 날씨와 상관없이 `sad_droop`이고, 그 밖에는 `rain_umbrella`, `hot_sweat`, `sunny_happy` 중 하나다.
2. 응원은 하루 1회다 (`Garden.lastCheeredOn`). 어제도 응원했으면 스트릭이 1 늘고 아니면 1부터 다시 센다. XP 10을 주고 기분을 `HAPPY`로 바꾼다. 스트릭이 7일 이상이면 `STREAK_7` 배지를 준다.
3. 하트는 돌봄 기록 하나에 1회다 (`care_log_heart.care_log_id` 유니크). XP 5를 주고 돌본 관리사에게 `HEART` 알림을 보낸다.
4. XP는 `XpService.grant(garden, reason, amount)`로만 준다. 텃밭의 `totalXp`를 올리고 `xp_history`에 기록을 남긴다. 오늘 얻은 XP와 리그의 주간 XP는 이 기록에서 계산한다. 사유(`XpReason`)는 `CHEER`, `HEART`, `EVENT`, `QUEST`, `QUIZ`다.

### 진화 이벤트

1. 작물 단계를 올리는 곳(성장 시뮬레이션, 관리사의 단계 확인, 시연의 빨리 감기)은 `EvolutionEventService.advanceStage(gardenId, newStage)`를 부른다. 텃밭의 단계를 바꾸고 `EvolutionEvent`를 만들고 주인에게 `STAGE_UP` 알림을 보낸다. 새 단계가 지금보다 높지 않으면 아무것도 하지 않는다. 부르는 쪽은 아직 없다.
2. 보상은 단계에 따라 `SPROUT` 30 XP와 `FIRST_SPROUT`, `FLOWER` 50 XP와 `FIRST_FLOWER`, `FRUIT` 100 XP와 `FIRST_FRUIT`다.
3. 주인이 `POST /events/{eventId}/ack`로 확인할 때 보상이 나간다. 이벤트 행과 텃밭을 잠그므로 동시에 눌러도 한 번만 나간다 (`C4095`).

### 성장 일기

테이블이 없고 `DiaryService`가 돌봄 기록과 진화 이벤트를 날짜 단위로 엮는다.

1. 텃밭을 시작한 날부터 오늘까지를 최신순으로 준다. 하루 안에서는 시각이 늦은 것이 먼저다.
2. 아무것도 없던 날은 `REST_DAY` 한 건으로 채운다 (`ALL` 필터만). 오늘과 시작한 날은 채우지 않는다.
3. 커서는 날짜(`YYYY-MM-DD`)다. 하루를 페이지 사이에서 나누지 않으므로 한 페이지가 `limit`보다 조금 많을 수 있다. 형식이 틀리면 `C002`다.

### 리그, 퀘스트, 배지, 스트릭

한 주는 월요일에 시작해 일요일에 끝난다 (`GameWeek`).

**리그.** 같은 농장의 텃밭끼리 한 주에 리그 하나다 (`league`, 유니크: 농장과 주).

1. 진행 중인 주의 순위는 저장하지 않고 `xp_history`의 그 주 합계로 그때그때 계산한다 (`LeagueService.getStandings`). 주간 XP가 같으면 순위도 같고 다음 순위는 그만큼 건너뛴다 (1, 1, 3).
2. 그 주의 리그가 없으면 조회할 때 만든다. 닉네임은 주인 프로필에서 가져온다.
3. 정산 배치(`LeagueSettleService.settle`, 매주 월 00:10)는 지난주 순위를 `league_member`에 남기고, 주간 XP가 0보다 큰 1등에게 `LEAGUE_FIRST` 배지를 주고, 새 주의 리그와 퀘스트를 만든다. 이미 정산한 리그는 건너뛰므로 다시 실행해도 된다. 리그 하나가 한 트랜잭션이다 (`LeagueSettler`).

**주간 퀘스트.** 한 주에 종류(`QuestType`)마다 퀘스트 하나가 있고 모든 주인이 같은 퀘스트를 받는다 (`quest`, 유니크: 주와 종류).

| `QuestType` | 제목 | 목표 | 보상 XP | 진행도가 오르는 곳 |
| --- | --- | --- | --- | --- |
| `CHEER` | 응원 5번 하기 | 5 | 50 | `GardenHomeService.cheer` |
| `HEART` | 감사 하트 3번 보내기 | 3 | 30 | `CareLogHeartService.sendHeart` |
| `EVENT` | 달라진 모습 확인하기 | 1 | 20 | `EvolutionEventService.ack` |

1. 진행도는 `QuestService.record(user, type)`로 올린다. 호출한 쪽의 트랜잭션에 참여하고, 목표를 채우는 순간 `QUEST_DONE` 알림을 보낸다. 목표를 채운 뒤에는 더 세지 않는다.
2. 그 주의 퀘스트가 없으면 처음 필요할 때 만든다 (정산 배치가 미리 만들어 두므로 보통은 이미 있다).
3. 보상은 `POST /quests/{questId}/claim`으로 받는다. 텃밭을 잠그므로 동시에 눌러도 한 번만 나간다. 지난주에 달성하고 받지 않은 보상도 받을 수 있다.

**리그와 퀘스트를 만드는 트랜잭션.** `LeagueCreator`, `WeeklyQuestCreator`는 `REQUIRES_NEW`로 따로 커밋한다. 여러 요청이 동시에 처음 만들면 한쪽이 유니크 제약에 걸리는데, 그 실패가 부른 쪽의 트랜잭션까지 되돌리지 않게 하려는 것이다. 부른 쪽은 실패하면 한 번 더 읽는다.

**배지.** 종류는 `BadgeCode`에 있고 `BadgeSeeder`가 부팅할 때 `badge` 테이블에 넣는다. 지급은 `BadgeService.grant(user, code)`로만 하고 같은 배지는 한 번만 준다.

| `BadgeCode` | 이름 | 받는 때 |
| --- | --- | --- |
| `FIRST_SPROUT`, `FIRST_FLOWER`, `FIRST_FRUIT` | 첫 새싹, 첫 꽃, 첫 열매 | 그 단계의 진화 이벤트를 확인할 때 |
| `STREAK_7` | 일주일 응원 | 응원으로 스트릭이 7일이 될 때 |
| `LEAGUE_FIRST` | 이웃 텃밭 1등 | 리그 정산에서 1등일 때 |

**스트릭 배치.** `StreakService.check`(매일 00:05)는 어제 응원하지 않은 텃밭 중 스트릭이 남아 있는 텃밭의 `streakDays`를 0으로, 기분을 `SAD`로 바꾼다. 다시 응원하면 기분이 `HAPPY`로 돌아온다.

**[확장] 오늘의 퀴즈.** 문제는 `QuizBank`에 있다 (7문제). 텃밭마다 그날 처음 조회할 때 문제가 정해져 `garden_quiz`에 저장되고 (유니크: 텃밭과 날짜), 날마다 다음 문제로 넘어간다. 답은 한 번만 낼 수 있고 (`C4095`) 맞히면 XP 10을 준다. 남의 텃밭의 퀴즈에 답하면 `C403`이다.

### 동시 요청과 잠금

XP, 스트릭, 보상처럼 값을 읽고 고치는 API는 텃밭 행을 잠그고(`select ... for update`) 그 뒤에 상태를 읽어 판단한다. 그래서 같은 주인의 요청이 동시에 들어와도 차례로 처리되고 보상은 한 번만 나간다.

| API | 잠그는 것 |
| --- | --- |
| 응원, 오늘의 퀴즈 조회 | 텃밭 (`GardenService.getOwnedGardenForUpdate`) |
| 하트, 퀘스트 보상, 퀴즈 답 | 내 텃밭 (`GardenService.findMyGardenForUpdate`. 텃밭 ID를 모르는 API라 주인의 텃밭 PK를 먼저 찾고 잠근다) |
| 이벤트 확인 | 이벤트, 텃밭 순서 |
| 프로필 등록 | 칸 |
| 리그 정산 | 리그 |

격리 수준은 READ COMMITTED다 (`spring.datasource.hikari.transaction-isolation`). MariaDB 11.6부터 기본인 `innodb_snapshot_isolation`에서는 REPEATABLE READ 트랜잭션이 무언가를 읽은 뒤에 행을 잠그거나 고칠 때, 그 사이 다른 요청이 그 행을 바꿨으면 "Record has changed since last read"로 실패한다 (응답은 `C500`). READ COMMITTED에서는 잠금을 얻은 뒤 가장 최근에 커밋된 값을 읽으므로 뒤에 온 요청이 `C4095`처럼 뜻이 맞는 응답을 받는다. 잠그는 조회 하나에 잠그지 않는 서브쿼리를 섞으면 READ COMMITTED에서도 같은 오류가 나고, 주인(user) 행까지 함께 잠그면 배지 지급과 교착이 생긴다. 그래서 PK를 먼저 읽고 텃밭 행만 잠근다.

## 권한

| `UserRole` | authority | 의미 |
| --- | --- | --- |
| `USER` | `ROLE_USER` | 가입 직후, 역할 선택 전 |
| `OWNER` | `ROLE_OWNER` | 텃밭 주인 (도시민) |
| `CARETAKER` | `ROLE_CARETAKER` | 텃밭 관리사 (어르신) |
| `ADMIN` | `ROLE_ADMIN` | 서비스 관리자 |

- **RoleHierarchy** (`SecurityConfiguration.roleHierarchy()`): `ROLE_ADMIN > ROLE_OWNER`, `ROLE_ADMIN > ROLE_CARETAKER`, `ROLE_ADMIN > ROLE_USER`. USER, OWNER, CARETAKER는 서로 포함하지 않는다. 그래서 명세의 "USER, OWNER"처럼 일부만 허용하는 경로를 `hasAnyRole`로 그대로 표현한다.
- **역할 결정:** 가입 직후는 `USER`. `PUT /users/me/role`로 `OWNER` 또는 `CARETAKER`를 고른다. `USER`, `ADMIN`으로는 바꿀 수 없다 (C002). ADMIN 계정은 가입 경로가 없어 DB에서 직접 `role`을 바꿔 만든다.
- **역할 변경:** 역할이 실제로 바뀌면 `profileRegistered`를 새 역할의 프로필이 있는지에 맞춘다 (OWNER로 돌아왔고 주인 프로필이 있으면 true, 그 밖에는 false). 이전 AT, RT는 블랙리스트에 올리고 새 역할이 담긴 토큰을 발급한다.
- **역할 미선택 접근:** `USER`가 OWNER나 CARETAKER 전용 경로에 접근하면 403 `C4031`을 준다 (그 외 권한 부족은 `C403`).
- `hasRole("OWNER")`처럼 `ROLE_` 접두사 없이 쓴다.

### nextStep

`UserService.resolveNextStep(User)` 한 곳에서만 계산하고 signup, login, `/users/me`, 역할 변경이 함께 쓴다.

| 상태 | nextStep |
| --- | --- |
| `USER` | `ROLE_SELECT` |
| `OWNER`, `profileRegistered=false` | `PROFILE_OWNER` |
| `OWNER`, `profileRegistered=true` | `OWNER_HOME` |
| `CARETAKER`, `profileRegistered=false` | `PROFILE_CARE` |
| `CARETAKER`, `profileRegistered=true` | `CARE_TODAY` |
| `ADMIN` | `null` |

## 인증 흐름

```
[signup / login / 역할 변경]
  1. UserService.issueToken(user)
  2. JwtTokenProvider.generateToken()
    ├─ AT: subject=username, claim "authorities"="ROLE_XXX", 만료 1시간
    ├─ RT: subject=username, claim 없음, 만료 7일
    └─ Redis SET refresh:{username} = RT (TTL = RT 만료)

[인증된 요청]
  JwtAuthenticationFilter
    1. Authorization: Bearer <AT> 헤더에서 AT 추출
    2. validateToken (서명, 만료, 블랙리스트 검사)
    3. getAuthentication: claim "authorities" 사용, 없으면 DB에서 role 조회, 사용자도 없으면 권한 없음
    4. SecurityContext 등록 (principal username = 로그인 아이디)

[refresh]
  1. RT 검증
  2. Redis의 refresh:{username}과 일치 확인
  3. AT, RT 새로 발급 (이전 RT는 덮어써져 즉시 무효)

[logout]
  1. AT 검증
  2. Redis refresh:{username}의 RT를 블랙리스트 등록 후 삭제
  3. AT 블랙리스트 등록

[역할 변경 (PUT /users/me/role)]
  1. 역할 저장, 역할이 바뀌었으면 profileRegistered=false
  2. logout과 같은 방식으로 이전 AT, RT 무효화
  3. 새 역할로 AT, RT 발급
```

### Redis 키

| 키 | 값 | TTL |
| --- | --- | --- |
| `refresh:{username}` | Refresh Token | RT 만료 시간 |
| `blacklist:{token}` | `"logout"` | 해당 토큰의 남은 만료 시간 |
| `demo:weather` | `{condition}\|{설정 시각}` (예: `RAIN\|2026-11-10T09:30:00+09:00`) | 없음 (`POST /demo/reset`이 SUNNY로 덮어씀) |

## 엔티티

### `User` (`user` 테이블)

| 필드 | 타입 | 제약 |
| --- | --- | --- |
| `id` | Long | PK, IDENTITY |
| `username` | String | not null, unique, 20자. 로그인 아이디 |
| `name` | String | not null, 20자 |
| `password` | String | not null, BCrypt |
| `role` | `UserRole` | not null, `EnumType.STRING` |
| `profileRegistered` | boolean | not null, 기본 false. 현재 역할의 프로필 등록 여부 |
| `registeredAt` | LocalDateTime | updatable=false, `AuthService.signUp`에서 직접 채움 |
| `modifiedAt` | LocalDateTime | `@UpdateTimestamp` |

`UserDetails` 구현, `getUsername()`은 로그인 아이디 반환. `getPublicId()`는 `usr_{id}`.

### `Notification` (`notification` 테이블)

| 필드 | 타입 | 제약 |
| --- | --- | --- |
| `id` | Long | PK, IDENTITY |
| `user` | `User` | `@ManyToOne(LAZY)`, `user_id` not null. 수신자. 인덱스 (`user_id`, `id`) |
| `type` | `NotificationType` | not null, `EnumType.STRING` |
| `title` | String | not null, 100자 |
| `body` | String | 500자 |
| `targetScreen` | String | 30자. 눌렀을 때 이동할 화면 코드 (예: `DT_DIARY`) |
| `targetId` | String | 50자. 그 화면에서 쓸 외부 ID (예: `cl_12`) |
| `createdAt` | LocalDateTime | not null, updatable=false |
| `readAt` | LocalDateTime | 읽은 시각. `null`이면 안 읽음 (읽음 여부 컬럼은 따로 없다) |

### `Visit` (`visit` 테이블)

| 필드 | 타입 | 제약 |
| --- | --- | --- |
| `id` | Long | PK, IDENTITY |
| `gardenId` | Long | not null. 텃밭 PK (엔티티 연관 없음) |
| `farmId` | Long | not null. 농장 PK, 슬롯 정원을 세는 단위. 인덱스 (`farm_id`, `visit_date`) |
| `user` | `User` | `@ManyToOne(LAZY)`, `user_id` not null. 예약한 주인 |
| `visitDate` | LocalDate | not null |
| `startTime`, `endTime` | LocalTime | not null |
| `headcount` | int | not null, 기본 1 |
| `memo` | String | 200자 |
| `status` | `VisitStatus` | not null, `RESERVED` / `CANCELED` |
| `reservedKey` | String | unique, 40자. `텃밭ID:날짜`. 취소하면 `null` |
| `createdAt` | LocalDateTime | not null, updatable=false |
| `canceledAt` | LocalDateTime | |

### `Outing` (`outing` 테이블)

| 필드 | 타입 | 제약 |
| --- | --- | --- |
| `id` | Long | PK, IDENTITY |
| `type` | `OutingType` | not null, `EXPERIENCE_FARM` / `RESTAURANT` |
| `name` | String | not null, 50자 |
| `address`, `description` | String | 200자 |
| `latitude`, `longitude` | double | not null |

### `WeatherForecast` (`weather_forecast` 테이블)

격자와 시각마다 한 줄이다 (유니크: `nx`, `ny`, `forecast_at`).

| 필드 | 타입 | 제약 |
| --- | --- | --- |
| `id` | Long | PK, IDENTITY |
| `nx`, `ny` | int | not null. 기상청 격자 좌표 |
| `forecastAt` | LocalDateTime | not null. 예보 또는 관측 대상 시각 |
| `temperature` | Double | 기온 (℃) |
| `rainProbability` | Integer | 강수확률 (%). 실황에는 없음 |
| `precipitationType` | Integer | 강수형태 코드 (기상청 PTY) |
| `skyStatus` | Integer | 하늘상태 코드 (기상청 SKY). 실황에는 없음 |
| `observed` | boolean | not null. true면 초단기실황, false면 단기예보 |
| `updatedAt` | LocalDateTime | not null |

### `DailyWeather` (`daily_weather` 테이블)

격자와 날짜마다 한 줄이다 (유니크: `nx`, `ny`, `weather_date`).

| 필드 | 타입 | 제약 |
| --- | --- | --- |
| `id` | Long | PK, IDENTITY |
| `nx`, `ny` | int | not null |
| `date` | LocalDate | not null, 컬럼 `weather_date` |
| `avgTemp`, `maxTemp`, `minTemp` | double | not null |
| `updatedAt` | LocalDateTime | not null |

### `Farm`, `Plot`, `Crop` (`farm`, `plot`, `crop` 테이블)

| 엔티티 | 필드 |
| --- | --- |
| `Farm` | `id`, `name` (50자), `address` (200자), `latitude`, `longitude`, `district` (`District`), `nx`, `ny` (기상청 격자), `externalId` (unique, 100자. 예: `yongin-weekend-farm:양달농원`, 시연 농장은 `demo:theme`), `createdAt`, `updatedAt` |
| `Plot` | `id`, `farm` (`@ManyToOne`), `plotCode` (20자. 유니크: `farm_id`, `plot_code`), `crop` (`@ManyToOne`), `caretaker` (`User`, 배정 전이면 `null`), `assigned` (기본 false) |
| `Crop` | `id`, `name` (unique, 30자), `characterName` (20자), `baseTemp`, `sproutGdd`, `flowerGdd`, `fruitGdd` (각 단계에 들어서는 누적 유효적산온도), `schedule` (300자), `source` (200자) |

### `OwnerProfile`, `Garden`, `XpHistory`, `CareLogHeart` (`owner_profile`, `garden`, `xp_history`, `care_log_heart` 테이블)

| 엔티티 | 필드 |
| --- | --- |
| `OwnerProfile` | `id`, `user` (`@OneToOne`, unique), `nickname` (20자), `district`, `createdAt`, `updatedAt` |
| `Garden` | `id`, `owner` (`@OneToOne User`, unique), `plot` (`@OneToOne`, unique), `crop`, `caretaker` (`User`, 배정 전이면 `null`), `characterName` (20자), `mood` (`CharacterMood`: `HAPPY`, `CALM`, `SAD`), `startedOn`, `stage` (`GrowthStage`: `SEED`, `SPROUT`, `FLOWER`, `FRUIT`), `accumulatedGdd`, `expectedHarvestDate` (계산 전이면 `null`), `streakDays`, `lastCheeredOn`, `totalXp`, `createdAt` |
| `XpHistory` | `id`, `garden`, `reason` (`XpReason`), `amount`, `createdAt`. 인덱스 (`garden_id`, `created_at`) |
| `CareLogHeart` | `id`, `careLogId` (unique, 엔티티 연관 없음), `gardenId`, `user` (보낸 주인), `caretakerUsername` (받은 관리사), `createdAt`. 인덱스 (`caretaker_username`, `created_at`) |

### `EvolutionEvent` (`evolution_event` 테이블)

| 필드 | 타입 | 제약 |
| --- | --- | --- |
| `id` | Long | PK, IDENTITY |
| `garden` | `Garden` | `@ManyToOne(LAZY)`, not null. 인덱스 (`garden_id`, `acked`) |
| `fromStage`, `toStage` | `GrowthStage` | not null |
| `occurredAt` | LocalDateTime | not null, updatable=false |
| `rewardXp` | int | not null. 확인하면 받는 XP |
| `rewardBadgeCode` | String | 30자. 확인하면 받는 배지 (`BadgeCode` 이름). 없으면 `null` |
| `acked` | boolean | not null, 기본 false |
| `ackedAt` | LocalDateTime | |

### 게임 (`league`, `league_member`, `quest`, `user_quest`, `badge`, `user_badge`, `garden_quiz` 테이블)

| 엔티티 | 필드 |
| --- | --- |
| `League` | `id`, `farm`, `weekStart` (월요일), `weekEnd` (일요일), `settled` (기본 false), `settledAt`. 유니크: `farm_id`, `week_start` |
| `LeagueMember` | `id`, `league`, `garden`, `weeklyXp`, `rank` (컬럼 `final_rank`). 정산이 끝난 리그의 최종 순위만 담는다. 유니크: `league_id`, `garden_id` |
| `Quest` | `id`, `weekStart`, `type` (`QuestType`), `title` (50자), `goal`, `rewardXp`. 유니크: `week_start`, `type` |
| `UserQuest` | `id`, `quest`, `user`, `progress` (기본 0), `claimed` (기본 false), `claimedAt`. 처음 진행도가 오를 때 생긴다. 유니크: `quest_id`, `user_id` |
| `Badge` | `id`, `code` (unique, 30자), `name` (30자), `description` (100자) |
| `UserBadge` | `id`, `user`, `badge`, `acquiredAt`. 유니크: `user_id`, `badge_id` |
| `GardenQuiz` | `id`, `garden`, `quizDate`, `bankIndex` (`QuizBank`에서의 순번), `choiceIndex` (안 풀었으면 `null`), `correct`, `answeredAt`. 유니크: `garden_id`, `quiz_date` |

## 에러 코드

| ErrorCode | code | HTTP | 메시지 | 사용처 |
| --- | --- | --- | --- | --- |
| `INVALID_REQUEST` | C001 | 400 | 잘못된 요청입니다. | 미사용 |
| `INVALID_PARAMETER` | C002 | 400 | 유효하지 않은 파라미터입니다. | `@Valid` 검증 실패, 읽을 수 없는 본문 (잘못된 enum 값 포함), 필수 쿼리 누락, 쿼리와 경로 값의 형식 오류 (날짜 등), `RoleNotAllowedException`, `InvalidIdException` (슬롯 ID 포함), `VisitSlotNotBookableException`, `DemoWeatherNotAllowedException`, `OwnerDistrictNotAllowedException`, `DiaryCursorInvalidException`, `QuizChoiceInvalidException` |
| `FILE_NOT_SUPPORTED` | C4001 | 400 | 지원하지 않는 파일입니다. | `FileNotSupportedException` (형식, 크기, 길이 위반), multipart 상한 초과 |
| `NOT_COMPLETED` | C4004 | 400 | 아직 완료되지 않았습니다. | `QuestNotCompletedException` |
| `CANCEL_DEADLINE_PASSED` | C4005 | 400 | 취소 기한이 지났습니다. | `VisitCancelDeadlinePassedException` |
| `VERIFICATION_INVALID` | C4011 | 401 | 인증 정보가 유효하지 않습니다. | EntryPoint (미인증 접근), `AuthenticationInfoException` |
| `PASSWORD_INVALID` | C4012 | 401 | 비밀번호가 일치하지 않습니다. | `PasswordInvalidException` |
| `TOKEN_INVALID` | C4013 | 401 | 유효하지 않은 토큰입니다. | `TokenInvalidException` |
| `SERVICE_KEY_INVALID` | C4014 | 401 | 서비스 키가 유효하지 않습니다. | `InternalServiceKeyFilter` |
| `ACCESS_DENIED` | C403 | 403 | 승인되지 않은 사용자입니다. | AccessDeniedHandler (권한 부족), `GardenAccessDeniedException` (남의 텃밭, 돌봄 기록, 이벤트, 퀴즈), `VisitAccessDeniedException` |
| `ROLE_NOT_SELECTED` | C4031 | 403 | 역할을 먼저 선택해야 합니다. | AccessDeniedHandler (역할 선택 전 `USER`의 접근), `OwnerRoleRequiredException` (역할 선택 전 `USER`의 주인 프로필 등록) |
| `DEMO_MODE_REQUIRED` | C4032 | 403 | 시연 모드에서만 사용할 수 있습니다. | `DemoModeFilter` (헤더 없음 또는 비활성화) |
| `USER_DATA_NOT_FOUND` | C4041 | 404 | 사용자를 찾을 수 없습니다. | `UserNotFoundException` |
| `DATA_NOT_FOUND` | C404 | 404 | 정보를 불러올 수 없습니다. | 매핑되지 않은 경로 (`NoResourceFoundException`), `NotificationNotFoundException`, `GardenNotFoundException`, `VisitNotFoundException`, `FarmNotFoundException`, `PlotNotFoundException`, `OwnerProfileNotFoundException`, `CareLogNotFoundException`, `EvolutionEventNotFoundException`, `QuestNotFoundException`, `QuizNotFoundException` |
| `USERNAME_DUPLICATION` | C4092 | 409 | 이미 사용 중인 아이디입니다. | `UsernameDuplicationException` |
| `PLOT_ALREADY_ASSIGNED` | C4093 | 409 | 이미 분양된 칸입니다. | `PlotAlreadyAssignedException` |
| `PROFILE_ALREADY_EXISTS` | C4094 | 409 | 이미 프로필이 등록되어 있어요. | `ProfileAlreadyExistsException` |
| `ALREADY_PROCESSED` | C4095 | 409 | 이미 처리된 요청입니다. | `VisitAlreadyReservedException` (같은 날 예약 있음), `VisitAlreadyCanceledException`, `CheerAlreadyDoneException`, `HeartAlreadySentException`, `EvolutionEventAlreadyAckedException`, `QuestAlreadyClaimedException`, `QuizAlreadyAnsweredException` |
| `VISIT_SLOT_FULL` | C4096 | 409 | 예약이 마감된 시간입니다. | `VisitSlotFullException` |
| `UNKNOWN_ERROR` | C500 | 500 | 오류가 발생하였습니다. | 처리되지 않은 모든 예외 |
| `FILE_UPLOAD_FAILED` | C5001 | 500 | 파일 저장에 실패했습니다. | `FileUploadFailedException` |
| `EXTERNAL_API_ERROR` | C502 | 502 | 외부 서비스 호출에 실패했습니다. | `AiClientException`, `KmaApiException` (수집 배치 안에서 잡으므로 응답으로 나가지는 않는다) |

## 설정

| 파일 | 내용 |
| --- | --- |
| `application.yml` | 공통: `.env` import(`optional:file:.env[.properties]`), 활성 프로필(`SPRING_PROFILES_ACTIVE`, 기본 `local`), 트랜잭션 격리 수준(READ COMMITTED), JPA open-in-view off, multipart 상한, Jackson 시간대, JWT 만료 시간, 파일 저장소 종류, 외부 호출 타임아웃, 스케줄러, 기상청 연결과 수집 격자, AI 설정, Swagger 경로 |
| `application-local.yml` | 모든 값에 개발용 기본값, `ddl-auto: update`, SQL 로그 on |
| `application-prod.yml` | 기본값 없음 (누락 시 부팅 실패), `ddl-auto: validate`, SQL 로그 off |
| `.env.example` | 환경변수 목록과 로컬 기본값. `.env`로 복사해서 쓰고, `.env`는 `.gitignore`에 포함 |
| `application.properties` | `spring.application.name`만 있음. `application.yml`과 중복, 삭제해도 무방 |

### 환경변수

값은 OS 환경변수, 로컬 `.env` 파일, yml 기본값 순으로 우선한다. `.env`에 `KEY=`처럼 빈 값을 두면 yml 기본값이 무시되니 쓰지 않을 값은 줄을 지우거나 주석으로 둔다.

| 변수 | local 기본값 | 설명 |
| --- | --- | --- |
| `SPRING_PROFILES_ACTIVE` | `local` | `local` / `prod` |
| `DB_HOST`, `DB_PORT`, `DB_NAME` | `localhost`, `3306`, `growwithus-server` | MariaDB |
| `DB_USERNAME`, `DB_PASSWORD` | `root`, `root` | |
| `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD` | `localhost`, `6379`, (없음) | |
| `JWT_SECRET_KEY` | 개발용 더미 | Base64 인코딩된 256비트 이상. **배포 전 반드시 교체** |
| `JWT_ACCESS_TOKEN_EXPIRE_TIME` | `3600000` (1시간) | ms |
| `JWT_REFRESH_TOKEN_EXPIRE_TIME` | `604800000` (7일) | ms |
| `CORS_ALLOWED_ORIGINS` | `*` | 콤마 구분, `allowedOriginPatterns`로 적용 |
| `INTERNAL_SERVICE_KEY` | `local-internal-service-key` | `/internal/**`의 `X-Service-Key` 값. **배포 전 반드시 교체** |
| `DEMO_ENABLED` | `true` | `/demo/**` 사용 여부. prod에서 `false`로 두면 시연 API가 모두 막힌다 |
| `DEMO_SEED_PASSWORD` | (없음) | 시연 계정(`minji`, `soonja`)의 비밀번호. `demo.enabled=true`이고 8자 이상이면 부팅할 때 시연 계정과 텃밭(B-14, 꽃 단계, 스트릭 12일, XP 1240)을 만든다. 비워 두면 만들지 않는다 |
| `SCHEDULER_ENABLED` | `true` | `@Scheduled` 사용 여부 (prod에도 같은 기본값) |
| `FILE_STORAGE` | `local` | 파일 저장소 구현체 (prod에도 같은 기본값) |
| `FILE_UPLOAD_DIR` | `./uploads` | 로컬 저장소 폴더. `.gitignore`에 포함 |
| `FILE_BASE_URL` | `http://localhost:8080` | 업로드 URL 앞에 붙는 서버 주소 |
| `EXTERNAL_HTTP_CONNECT_TIMEOUT`, `EXTERNAL_HTTP_READ_TIMEOUT` | `3s`, `30s` | 외부 API 호출 타임아웃 (prod에도 같은 기본값) |
| `PUBLIC_DATA_SERVICE_KEY` | (없음) | 공공데이터포털 인증키 (기상청 단기예보). 포털의 "일반 인증키"를 그대로 넣는다. 비워 두면 부팅은 되고 날씨 수집을 건너뛴다 (prod에도 같은 기본값) |
| `KMA_MAX_ATTEMPTS`, `KMA_RETRY_DELAY` | `3`, `1s` | 기상청 호출 전체 시도 횟수와 다시 시도하기 전 대기 시간 (prod에도 같은 기본값) |
| `WEATHER_DEFAULT_GRIDS` | `64:119` | 농장 도메인이 붙기 전까지 수집할 기상청 격자. `nx:ny`를 콤마로 잇는다. 기본값은 처인구 대표 격자 (prod에도 같은 기본값) |
| `LLM_API_KEY`, `LLM_BASE_URL`, `LLM_MODEL` | (없음) | LLM 연결 설정. 비워 두어도 부팅된다 |
| `STT_API_KEY`, `STT_BASE_URL`, `STT_MODEL` | (없음) | STT 연결 설정 |
| `TTS_API_KEY`, `TTS_BASE_URL`, `TTS_MODEL`, `TTS_VOICE` | (없음) | TTS 연결 설정 |

prod에서 기본값이 없어 반드시 넣어야 하는 값: DB, Redis, `JWT_SECRET_KEY`, `CORS_ALLOWED_ORIGINS`, `INTERNAL_SERVICE_KEY`, `DEMO_ENABLED`, `FILE_UPLOAD_DIR`, `FILE_BASE_URL`.

## 로컬 실행

```bash
cp .env.example .env   # 필요하면 값 수정
docker run -d -p 3306:3306 -e MARIADB_ROOT_PASSWORD=root -e MARIADB_DATABASE=growwithus-server mariadb:11
docker run -d -p 6379:6379 redis:7
./gradlew bootRun
```

- 컴파일만: `./gradlew compileJava`
- DB 없이 빌드: `./gradlew build -x test` (`contextLoads` 테스트가 실제 MariaDB를 요구)
- DB 없이 도는 단위 테스트만: `./gradlew test --tests 'community.likelion.growwithusserver.common.*'` (도메인 단위 테스트는 `common` 자리에 `notification`, `harvest`, `weather`, `demo`, `farm`, `garden`, `diary`, `evolution`, `game`)
- Swagger: http://localhost:8080/swagger-ui.html

## 알려진 이슈 / 결정 대기

| 항목 | 현재 상태 | 영향 |
| --- | --- | --- |
| `profileRegistered` 갱신 | 주인 프로필을 등록하면 true가 되고, 역할을 바꿨다가 OWNER로 돌아오면 기존 주인 프로필이 있을 때 true로 복원된다. 관리사 프로필 도메인은 아직 없다 | 관리사는 `nextStep`이 `PROFILE_CARE`에서 멈춘다. 관리사 프로필 등록 API를 만들 때 true로 바꾼다 |
| `/users/me`의 `caretaker` | 항상 `null` (`owner`는 채워진다) | 관리사 프로필 도메인을 만들 때 `UserService.getMe`에서 채운다 |
| 기존 로컬 DB의 `user` 테이블 | `ddl-auto: update`는 예전 `email` 컬럼(not null)을 지우지 않는다 | 가입 시 insert가 실패한다. 로컬 DB의 `user`, `product` 테이블을 직접 drop하고 다시 띄운다 |
| prod `ddl-auto: validate` | 스키마 자동 생성 안 함 | prod DB에 테이블을 미리 만들어야 부팅됨 (마이그레이션 도구 없음). 이번에 추가된 테이블: `farm`, `plot`, `crop`, `owner_profile`, `garden`, `xp_history`, `care_log_heart`, `evolution_event`, `badge`, `user_badge`, `league`, `league_member`, `quest`, `user_quest`, `garden_quiz` |
| `@CreatedDate` 미동작 | `@EnableJpaAuditing` 없음 | 감사 필드는 서비스에서 `LocalDateTime.now()`로 직접 채운다 |
| 검증 실패 메시지 | 클라이언트에는 C002 고정 메시지만 반환 | 어떤 필드가 틀렸는지는 서버 로그에만 남음 |
| 테스트 | `contextLoads`, `common` 단위 테스트 (페이지네이션, ID, 파일 검증, 필터, 날짜 직렬화), `notification`, `harvest`, `weather`, `demo`, `farm`, `garden`, `diary`, `evolution`, `game` 서비스 단위 테스트 (저장소는 Mockito로 대체) | 실제 DB를 쓰는 저장소 쿼리 테스트는 없음. 잠금과 동시 요청은 로컬 MariaDB에 서버를 띄워 손으로 확인했다 |
| AI 클라이언트 구현체 | 제공자 미정, 인터페이스와 기본 빈만 있음 | `LlmClient`, `SttClient`, `TtsClient` 호출은 항상 `C502`. 제공자가 정해지면 구현체를 추가한다 |
| 시연 계정 | 가입 API로는 ADMIN을 만들 수 없음 | 시연 전에 DB에서 시연 계정의 `role`을 `ADMIN`으로 직접 바꾼다. 시연이 끝나면 prod `DEMO_ENABLED=false` |
| 브라우저 녹음 길이 | webm과 사파리 조각 mp4는 서버가 길이를 읽지 못함 | 5MB 이하로만 확인한다. 60초 제한은 프론트 녹음 제한에 기대며, STT 제공자가 정해지면 STT 결과 길이로 다시 확인할 수 있다 |
| 돌봄 기록, 관리사 프로필 접점 | 실제 도메인 구현체 연결 완료 | 홈, 일기, 감사 하트 연결을 격리 환경 실제 HTTP로 검증 |
| 단계를 올리는 곳 | GrowthService와 GardenGrowthProjection에서 EvolutionEventService에 연결 | 시연도 같은 이벤트와 보상 경로 사용 |
| 실제 농장의 칸 | 공공데이터에 칸 정보가 없어 시연 농장에만 칸이 있음 | 실제 농장 8곳은 목록에는 나오지만 분양받을 수 없다 (`C404`). 칸을 넣는 관리 기능이나 시드가 필요하다 |
| 농장 좌표 | 원본에 좌표가 없어 읍면동 대표 좌표를 쓴다 | 같은 읍면동의 농장은 좌표와 거리가 같게 나온다 |
| 방문 슬롯 정원의 동시성 | VisitCapacityGuard가 날짜 행을 잠근 뒤 정원을 검사한다 | 같은 텃밭의 같은 날 예약은 유니크 키로 보호한다 |
| 관리사별 방문 가능 시간 | 데이터가 없어 주말 고정 시간(10, 11, 14, 15시)을 쓴다 | 관리사가 시간을 고르게 하려면 프로필에 가능 시간을 추가하고 `VisitSlotPolicy`를 고친다 |
| 나들이 추천 데이터 | 실제 장소 7곳을 시드로 넣는다. 식당은 원본에 좌표가 없어 좌표를 확인한 4곳만 넣었고, 그중 2곳(국수59, 백암제일식당)은 가게가 있는 길의 좌표다 | 거리가 100m쯤 어긋날 수 있다. 예시 6곳이 이미 들어간 DB에는 시드가 다시 들어가지 않으므로 `outing` 테이블을 비우고 다시 띄운다. 식당을 더 넣으려면 주소를 좌표로 바꾸는 방법이 필요하다 |
| 날씨 수집 격자 | 등록된 농장의 격자를 수집한다 (`FarmWeatherFarmReader`) | 농장이 하나도 없으면 수집할 격자가 없다. `WEATHER_DEFAULT_GRIDS`는 구현체가 없을 때의 기본 빈만 쓰므로 지금은 쓰이지 않는다 |
| 날씨 수집 인증키 | `PUBLIC_DATA_SERVICE_KEY`가 비어 있으면 수집을 건너뜀 | `weather_forecast`가 비어 `getCurrentWeather`는 시연 날씨가 있을 때만 값을 준다. 로컬 `.env`와 배포 환경에 키를 넣어야 한다 |
| 시연 API | 날씨, 누적 일수, 빨리 감기, 성장과 작업 회차 초기화 구현 | 운영 스키마 변경 필요. 상단 시연 도메인 설명 참고 |
| 파일 저장소 | 로컬 디스크만 구현 | 서버를 여러 대 띄우거나 재배포로 디스크가 초기화되면 파일이 사라진다. 필요하면 S3 구현체를 추가한다 |

### 이슈별 남은 작업

다른 도메인이 생겨야 마저 할 수 있는 항목이다. 해당 도메인을 만들 때 함께 붙인다.

| 이슈 | 남은 항목 | 필요한 것 |
| --- | --- | --- |
| #14 | 방문 시드 구현 완료 | DEMO_SEED_VISIT_ENABLED=true로 선택 실행. 실제 관리사 방문 가능 시간은 운영 자료가 필요하다 |
| #16 | 코드 구현 및 격리 환경 HTTP 검증 완료 | 운영 스키마 적용과 실제 프론트 리허설 필요. 과거 기록을 삭제하는 전체 DB 재적재는 하지 않는다 |
| #11 | 리그, 마이 화면 디자인 반영 | 디자인이 아직 없어 응답 구조를 명세 기준으로 만들었다. 디자인이 나오면 필드를 맞춘다 |
# 2026-11-13 AI 후속 보완

성장 배치는 명세의 POST /internal/ai/growth-simulation을 지원하며 기존 /internal/ai/simulate-growth도 호환 경로로 유지한다. 성장 조회는 먼저 GrowthService의 DB 트랜잭션을 끝낸 다음 GrowthExplanationService에서 LLM 설명을 생성한다. 모델 오류, 잘못된 JSON과 300자 초과 응답은 기존 설명으로 대체한다. 성공 응답은 계산된 사실을 키로 최대 128개까지 캐시한다. 합성 날씨와 활성 DB 트랜잭션에서는 모델을 호출하지 않는다. 모델 설명은 관찰이나 수확 확정을 대신하지 않는다.

음성 모델의 예상하지 못한 런타임 실패도 원본 파일과 메모를 유지하고 재시도 상태로 저장한다. STT 원문이 있으면 LLM 실패로 구분하고 재시도 시 음성을 다시 인식하지 않는다. DB 저장 실패는 모델 실패로 감추지 않는다.

TTS 구현체 LocalTtsClient는 선택적으로 127.0.0.1:8766의 로컬 작업자를 호출한다. 작업자는 eSpeak NG 한국어 합성과 FFmpeg MP3 변환을 사용하며 외부 음성 서비스에 텍스트를 보내지 않는다. 신경망 음성이 아닌 기본 읽어주기이므로 실제 청취 품질 확인이 필요하다. Docker 이미지 빌드에서 실제 합성과 MP3 디코딩 검사를 실행한다. 작업자가 꺼져 있거나 미설정이면 기존 오류와 프론트 읽어주기 대체 동작을 유지한다.

운영 활성화는 AI_LOCAL_TTS_ENABLED=true와 AI_LOCAL_TTS_BASE_URL 설정이 필요하다. compose.ai-workers.json에 독립 tts 작업자를 추가했고 compose.local-demo.json은 같은 네트워크 공간에서 연결한다. 새 DB 스키마는 없다. 사진 비전은 출력 토큰을 제한하고 미설정, 모델 오류와 잘못된 결과를 판정 불가로 처리한다. 실제 비전 모델 설치와 운영 사양 검증은 별도다.

최신 시연 가상 성장 상태도 내부 simulated 표시로 구분하여 LLM 설명에서 제외한다. 이 표시는 JSON 응답에 노출하지 않으며 기존 시연 안내 문구를 유지한다.
