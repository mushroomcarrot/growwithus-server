# 현재 구조

> 코드의 현재 상태를 그대로 기록한 문서다. 기능을 추가하거나 바꾸면 이 문서도 함께 갱신한다 ([development-guide.md](development-guide.md#문서-갱신-규칙)).
>
> 마지막 갱신: 2026-11-13, 로컬 AI 품질 평가, 시연 토큰 갱신과 읽어주기, 느린 모델의 재시도 유효 시간 보정 추가. 실제 모델과 DB 부팅, 팀 도메인 통합은 남아 있음

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
3. `growth`는 `GrowthState`와 `GrowthEvent`를 저장한다. 관찰 보정과 완료된 날짜의 기온을 기준으로 재계산하고 단계 상승 알림을 보낸다. 물 주기 등의 단계 검증은 저장된 성장 상태를 우선한다. `GrowthStatusService`는 확인한 단계에서 같은 확인 요청을 반복하지 않으며 다음 주요 단계에서 다시 요청한다. NO_CHANGE는 보정하지 않아 요청이 유지된다.
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
| GET | `/gardens/{gardenId}/harvest` | OWNER | 없음 | `HarvestResponseDto` | 수확 안내 (예측 수확일, D-day, 함께 돌본 일수) |
| GET | `/gardens/{gardenId}/visit-slots?from=&to=` | OWNER | 쿼리 `from`, `to` (선택, `YYYY-MM-DD`) | `List<VisitSlotResponseDto>` | 주말 방문 슬롯과 예약 가능 여부. 생략하면 내일부터 2주 |
| POST | `/visits` | OWNER | `VisitRequestDto` | `VisitResponseDto` | 주말 방문 예약, 담당 관리사에게 알림 |
| DELETE | `/visits/{visitId}` | OWNER | 없음 | `null` | 방문 전날까지 취소, 담당 관리사에게 알림 |
| GET | `/gardens/{gardenId}/outings` | OWNER | 없음 | `List<OutingResponseDto>` | 농장에서 가까운 순으로 나들이 장소 최대 6곳 |
| GET | `/demo/state` | ADMIN + `X-Demo-Mode` | 없음 | `DemoStateResponseDto` | 시연 상태 (지금은 날씨만 채워짐) |
| PUT | `/demo/weather` | ADMIN + `X-Demo-Mode` | `DemoWeatherRequestDto` | `DemoWeatherResponseDto` | 시연 날씨 덮어쓰기 (SUNNY, RAIN, HOT) |
| POST | `/demo/reset` | ADMIN + `X-Demo-Mode` | 없음 | `DemoStateResponseDto` | 시연 날씨를 SUNNY로 되돌림 |
| POST | `/internal/weather/sync` | SYSTEM (`X-Service-Key`) | `WeatherSyncRequestDto` (본문 생략 가능) | `WeatherSyncResponseDto` | 기상청 단기예보와 초단기실황 수집. 스케줄러가 매시 15분에 같은 로직을 돌린다 |
| POST | `/caretaker/tasks/{taskId}/voice-memos` | CARETAKER | multipart `audio` | `VoiceMemoResponseDto` | 음성 원문과 요약을 DRAFT로 저장. 실제 작업 조회 구현체가 없으면 C404 |
| POST | `/caretaker/voice-memos/{voiceMemoId}/confirm` | CARETAKER | `VoiceMemoConfirmRequestDto` 선택 | `VoiceMemoConfirmResponseDto` | 본인 초안 확정. 같은 확정 요청은 재시도 가능 |

텃밭 경로(`/gardens/{gardenId}/...`)와 `POST /visits`는 텃밭 도메인이 붙기 전까지 항상 `C404`다 ([알려진 이슈](#알려진-이슈--결정-대기)). `POST /demo/fast-forward`는 아직 없다.

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
| `HarvestResponseDto` | `expectedDate`, `dDay` (지났으면 0, 수확일이 없으면 `null`), `harvestable`, `daysCaredTogether` (시작한 날을 1일로 셈), `careLogCount`, `caretakerName`, `message` |
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

**텃밭 접점 (`HarvestGardenReader`).** 텃밭, 농장, 관리사, 성장 시뮬레이션 도메인이 아직 없어서, 수확과 방문이 필요로 하는 값만 `HarvestGarden`으로 정의하고 `HarvestGardenReader.findGarden(gardenId)`로 받는다. 구현체는 아직 없고 기본 빈(`HarvestGardenConfiguration`)은 항상 비어 있는 결과를 주므로 관련 API는 `C404`다. 텃밭 도메인이 생기면 구현체를 `@Component`로 등록한다. `HarvestGarden`의 필드는 `gardenId`, `ownerUsername`, `ownerNickname`, `characterName`, `farmId`, `farmLatitude`, `farmLongitude`, `caretakerUsername`, `caretakerName`, `startedOn`, `expectedHarvestDate`, `harvestable`, `careLogCount`다.

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
| 음성 메모 API | 작업 조회는 구현, 실제 텃밭 조회 어댑터가 없음 | 농장과 텃밭 도메인 연결 및 MariaDB 잠금 검사가 필요. STT 실패 시 파일 보존과 재시도 구현, DB 실패 시 파일 보상 삭제는 남아 있음 |
| 브라우저 녹음 길이 | 공통 업로드 모듈은 webm과 조각 mp4 길이를 읽지 못함 | 로컬 STT를 활성화하면 작업 서버에서 디코딩된 전체 길이를 60초 이하로 다시 검사한다. 실제 브라우저 파일 시험은 남아 있음 |
| 텃밭 조회 구현체 | 기본 조회는 빈 결과, local-demo만 가상 텃밭 제공 | 실제 서비스에는 팀 텃밭 조회 어댑터가 필요하다. 시연 작물과 기온은 운영 자료가 아니다 |
| 방문 슬롯 정원의 동시성 | 정원은 조회 후 저장으로 확인 | 같은 슬롯에 동시에 예약이 들어오면 정원을 1건 넘길 수 있다. 같은 텃밭의 같은 날 중복은 유니크 제약으로 막힌다 |
| 관리사별 방문 가능 시간 | 데이터가 없어 주말 고정 시간(10, 11, 14, 15시)을 쓴다 | 관리사가 시간을 고르게 하려면 프로필에 가능 시간을 추가하고 `VisitSlotPolicy`를 고친다 |
| 나들이 추천 데이터 | 실제 장소 7곳을 시드로 넣는다. 식당은 원본에 좌표가 없어 좌표를 확인한 4곳만 넣었고, 그중 2곳(국수59, 백암제일식당)은 가게가 있는 길의 좌표다 | 거리가 100m쯤 어긋날 수 있다. 예시 6곳이 이미 들어간 DB에는 시드가 다시 들어가지 않으므로 `outing` 테이블을 비우고 다시 띄운다. 식당을 더 넣으려면 주소를 좌표로 바꾸는 방법이 필요하다 |
| 날씨 수집 격자 | 농장 도메인이 없어 `WeatherFarmReader` 구현체가 없음. 설정의 기본 격자(`64:119`)만 수집한다 | 농장마다 격자가 다르면 그 농장의 날씨가 비어 있다. 농장 도메인에서 농장 좌표로 격자를 구해 (변환식은 [public-data.md](public-data.md)) 구현체를 등록한다. 그 전에는 `WEATHER_DEFAULT_GRIDS`에 격자를 더한다 |
| 날씨 수집 인증키 | `PUBLIC_DATA_SERVICE_KEY`가 비어 있으면 수집을 건너뜀 | `weather_forecast`가 비어 `getCurrentWeather`는 시연 날씨가 있을 때만 값을 준다. 로컬 `.env`와 배포 환경에 키를 넣어야 한다 |
| 시연 API | 날씨 덮어쓰기만 동작 | `PUT /demo/weather`의 `weatherNotice`, `regeneratedTasks`와 `GET /demo/state`의 단계, 할 일 수는 비어 있다. `POST /demo/fast-forward`와 시드 재적재는 없다. 작업 생성, 성장 시뮬레이션, 진화 이벤트, 텃밭 도메인이 생기면 `DemoService`에 붙인다 |
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
10. Java 핵심 JUnit 65개, STT Python 21개, 시연 도구 Python 7개를 통과했다. 실제 Docker, 전체 Gradle 빌드, DB 쿼리와 잠금, 브라우저 녹음, 모델 추론은 작성 환경에서 검증하지 못했다. 상세 실행 절차는 [local-demo.md](local-demo.md)를 따른다.

## 모델 평가와 시연 화면 보완

1. evaluateLocalAi는 명시적으로 실행하는 JavaExec 작업이다. testClasses의 합성 원문 12개로 운영과 같은 VoiceMemoAnalysisService를 호출한다. 기본 test와 build에서는 모델을 호출하지 않는다. 모델과 주소는 기존 AI_LOCAL_MODEL, AI_LOCAL_BASE_URL을 사용하며 loopback과 cloud 이름 거부 정책도 같다.
2. 평가기는 태그의 micro precision, recall, F1, 정확한 집합 일치, 대체 경로 수, p50과 p95 지연을 보고한다. 대체 결과는 모델 성공으로 세지 않는다. 결과가 하나라도 불일치하면 보고서를 저장하고 종료 코드 1을 반환한다. STT와 의미적 요약 품질은 평가하지 않으며 실제 점수는 아직 없다.
3. Compose의 ai-evaluation은 evaluation 프로필을 선택해야 실행된다. 모델 서버와 네트워크 공간을 공유하고 호스트에는 별도 포트를 열지 않는다. 결과만 build/reports에 기록하며 사용자 DB와 음성 파일을 수정하지 않는다.
4. 시연 콘솔은 계정별 토큰을 메모리에서 관리한다. 401이면 계정별 갱신 요청을 공유하고 원래 요청을 한 번만 다시 보낸다. 403과 네트워크 오류는 자동 재전송하지 않는다. 새 음성을 선택한 상태에서 이전 메모로 완료하지 못하게 검사한다.
5. 원문에서 추출된 이상 징후를 한글 문구로 표시하며 수정 확정 시 오래된 태그를 지운다. 읽어주기는 브라우저 speechSynthesis이고 서버 TTS와 별개다. 마이크 권한 대기 중 작업이 바뀌면 받은 스트림을 종료한다. 기본 자동 녹음은 59초로 인코딩 여유를 둔다.
6. 음성 처리 유효 시간은 HTTP 읽기 제한의 2배 + 30초이고 최소 2분이다. 기본 30초 설정은 2분, local-demo의 90초 설정은 3분 30초다. STT 다음 LLM을 기다리는 정상 요청을 먼저 만료시키지 않도록 한다. 재시도와 늦은 결과의 토큰 검사는 유지한다.
7. 핵심 JUnit 65개, Python 28개, Node 시연 로직 8개가 통과했다. Node는 DOM과 fetch 대역 검사이며 실제 마이크와 브라우저 화면 검사와 다르다. 운영 DB, 모델, 전체 Gradle 빌드는 미검증이다. 팀 전달 기준과 남은 항목은 [ai-handoff.md](ai-handoff.md)를 따른다.
