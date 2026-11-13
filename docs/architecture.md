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
| GET | `/gardens/{gardenId}/harvest` | OWNER | 없음 | `HarvestResponseDto` | 수확 안내 (예측 수확일, D-day, 함께 돌본 일수) |
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
| `GardenHomeResponseDto` | `gardenId`, `character` (`name`, `stage`, `level`, `mood`, `animation`, `message`), `weather` (`CurrentWeatherDto`, 수집된 날씨가 없으면 `null`), `streak` (`days`, `cheeredToday`), `xp` (`total`, `today`), `harvest` (`expectedDate`, `dDay`. 계산 전이면 둘 다 `null`), `caretaker` (배정 전이면 `null`), `recentCareLog` (`careLogId`, `summary`, `photoUrl`, `createdAt`, `hearted`. 없으면 `null`), `hasUnreadEvent` |
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
| POST | `/demo/reset` | ADMIN + `X-Demo-Mode` | 없음 | `DemoStateResponseDto` | 시연 날씨를 SUNNY로 되돌림 |
| POST | `/internal/weather/sync` | SYSTEM (`X-Service-Key`) | `WeatherSyncRequestDto` (본문 생략 가능) | `WeatherSyncResponseDto` | 기상청 단기예보와 초단기실황 수집. 스케줄러가 매시 15분에 같은 로직을 돌린다 |
| POST | `/internal/farms/sync` | SYSTEM (`X-Service-Key`) | 없음 | `FarmSyncResponseDto` | 공공데이터 주말농장 파일 동기화. 스케줄러는 매주 월 03:00 |
| POST | `/internal/streaks/check` | SYSTEM (`X-Service-Key`) | `StreakCheckRequestDto` (본문 생략 가능) | `StreakCheckResponseDto` | 전날 응원이 없는 텃밭의 스트릭 끊김 처리. 스케줄러는 매일 00:05 |
| POST | `/internal/leagues/settle` | SYSTEM (`X-Service-Key`) | `LeagueSettleRequestDto` (본문 생략 가능) | `LeagueSettleResponseDto` | 지난주 리그 정산, 새 주의 리그와 퀘스트 생성. 스케줄러는 매주 월 00:10 |

아직 없는 API: 관리사 프로필과 작업 (`/caretakers/**`, `/caretaker/**`, `/tts`, `/gardens/*/stage-checks`), `POST /internal/ai/daily-tasks`, `POST /internal/ai/growth-simulation`, `POST /demo/fast-forward`. 돌봄 기록 도메인이 없어서 `POST /care-logs/{careLogId}/hearts`는 항상 `C404`다 ([알려진 이슈](#알려진-이슈--결정-대기)).

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

**텃밭 접점 (`HarvestGardenReader`).** 수확과 방문이 필요로 하는 값만 `HarvestGarden`으로 정의하고 `HarvestGardenReader.findGarden(gardenId)`로 받는다. 구현체는 텃밭 도메인의 `GardenHarvestReader`다. `HarvestGarden`의 필드는 `gardenId`, `ownerUsername`, `ownerNickname`, `characterName`, `farmId`, `farmLatitude`, `farmLongitude`, `caretakerUsername`, `caretakerName`, `startedOn`, `expectedHarvestDate`, `harvestable`, `careLogCount`다. `careLogCount`는 돌봄 기록 도메인이 생기기 전까지 0이다.

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
| 돌봄 기록, 관리사 프로필 접점 | `GardenCareLogReader`, `FarmCaretakerReader`의 구현체가 없음 (관리사 프로필 #5, 작업과 돌봄 기록 #7 미구현) | 홈의 `recentCareLog`는 `null`, 일기에는 단계 변화와 쉬는 날만 나오고, `POST /care-logs/{careLogId}/hearts`는 항상 `C404`, 수확의 `careLogCount`는 0이다. 관리사는 이름만 나가고 `caretakerId`, `avatarUrl`은 `null`이다. 해당 도메인에서 구현체를 등록한다 |
| 단계를 올리는 곳 | `EvolutionEventService.advanceStage`를 부르는 곳이 없음 (성장 시뮬레이션 #8, 단계 확인 미구현) | 진화 이벤트가 생기지 않는다. 시연 텃밭에는 이미 확인한 이벤트 두 건만 시드로 들어 있다. `EVENT` 퀘스트도 이벤트가 생겨야 달성할 수 있다 |
| 실제 농장의 칸 | 공공데이터에 칸 정보가 없어 시연 농장에만 칸이 있음 | 실제 농장 8곳은 목록에는 나오지만 분양받을 수 없다 (`C404`). 칸을 넣는 관리 기능이나 시드가 필요하다 |
| 농장 좌표 | 원본에 좌표가 없어 읍면동 대표 좌표를 쓴다 | 같은 읍면동의 농장은 좌표와 거리가 같게 나온다 |
| 방문 슬롯 정원의 동시성 | 정원은 조회 후 저장으로 확인 | 같은 슬롯에 동시에 예약이 들어오면 정원을 1건 넘길 수 있다. 같은 텃밭의 같은 날 중복은 유니크 제약으로 막힌다 |
| 관리사별 방문 가능 시간 | 데이터가 없어 주말 고정 시간(10, 11, 14, 15시)을 쓴다 | 관리사가 시간을 고르게 하려면 프로필에 가능 시간을 추가하고 `VisitSlotPolicy`를 고친다 |
| 나들이 추천 데이터 | 실제 장소 7곳을 시드로 넣는다. 식당은 원본에 좌표가 없어 좌표를 확인한 4곳만 넣었고, 그중 2곳(국수59, 백암제일식당)은 가게가 있는 길의 좌표다 | 거리가 100m쯤 어긋날 수 있다. 예시 6곳이 이미 들어간 DB에는 시드가 다시 들어가지 않으므로 `outing` 테이블을 비우고 다시 띄운다. 식당을 더 넣으려면 주소를 좌표로 바꾸는 방법이 필요하다 |
| 날씨 수집 격자 | 등록된 농장의 격자를 수집한다 (`FarmWeatherFarmReader`) | 농장이 하나도 없으면 수집할 격자가 없다. `WEATHER_DEFAULT_GRIDS`는 구현체가 없을 때의 기본 빈만 쓰므로 지금은 쓰이지 않는다 |
| 날씨 수집 인증키 | `PUBLIC_DATA_SERVICE_KEY`가 비어 있으면 수집을 건너뜀 | `weather_forecast`가 비어 `getCurrentWeather`는 시연 날씨가 있을 때만 값을 준다. 로컬 `.env`와 배포 환경에 키를 넣어야 한다 |
| 시연 API | 날씨 덮어쓰기만 동작 | `PUT /demo/weather`의 `weatherNotice`, `regeneratedTasks`와 `GET /demo/state`의 단계, 할 일 수는 비어 있다. `POST /demo/fast-forward`와 시드 재적재는 없다. 작업 생성, 성장 시뮬레이션, 진화 이벤트, 텃밭 도메인이 생기면 `DemoService`에 붙인다 |
| 파일 저장소 | 로컬 디스크만 구현 | 서버를 여러 대 띄우거나 재배포로 디스크가 초기화되면 파일이 사라진다. 필요하면 S3 구현체를 추가한다 |

### 이슈별 남은 작업

다른 도메인이 생겨야 마저 할 수 있는 항목이다. 해당 도메인을 만들 때 함께 붙인다.

| 이슈 | 남은 항목 | 필요한 것 |
| --- | --- | --- |
| #14 | 시연용 방문 시드 | 수확, 방문 API는 텃밭 도메인이 붙어 동작한다. 방문 시드는 시연 시나리오가 정해지면 `GardenDemoSeeder`에 더한다 |
| #16 | `POST /demo/fast-forward`, 날씨 변경 시 작업 재생성, 단계 진행 표시, 시드 재적재, 시연 리허설 | 작업 생성 (#7), 성장 시뮬레이션 (#8), 진화 이벤트 (#10). `DemoService`에 붙인다 |
| #11 | 리그, 마이 화면 디자인 반영 | 디자인이 아직 없어 응답 구조를 명세 기준으로 만들었다. 디자인이 나오면 필드를 맞춘다 |
