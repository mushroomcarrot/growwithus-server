# 개발 가이드

새 기능을 추가하거나 코드를 바꿀 때 따르는 규칙. 현재 구조는 [architecture.md](architecture.md)를 본다.

## 문서 갱신 규칙

기능을 추가하거나 동작을 바꾸면 **같은 작업 안에서** 문서도 갱신한다. 코드만 바꾸고 끝내지 않는다.

| 이런 변경을 했으면 | 이 문서를 갱신 |
| --- | --- |
| 엔드포인트 추가, 변경, 삭제 | `architecture.md`의 API 표, DTO 표 |
| 도메인 패키지 추가, 삭제 | `architecture.md`의 패키지 구조, 엔티티 |
| 엔티티 필드, 연관관계 변경 | `architecture.md`의 엔티티 |
| `ErrorCode` 추가 | `architecture.md`의 에러 코드 표 |
| 보안 경로, 권한 변경 | `architecture.md`의 API 표 권한 열, 권한 섹션 |
| 환경변수, 설정 추가 | `architecture.md`의 설정, 환경변수 표, 그리고 `.env.example` |
| 의존성 추가 | `architecture.md`의 한눈에 보기 |
| 알려진 이슈 해결 또는 새로 발견 | `architecture.md`의 알려진 이슈 표 |
| 코드 규칙, 작업 방식 변경 | 이 문서 또는 `AGENTS.md` |
| 기획 변경 | `planning.md` (사용자가 요청할 때만) |

- 갱신할 때 `architecture.md` 상단의 **마지막 갱신** 줄도 날짜와 한 줄 요약으로 바꾼다.
- 새 문서를 `docs/`에 추가하면 `AGENTS.md`의 문서 목록에도 추가한다.
- `CLAUDE.md`는 `@AGENTS.md`만 담고 있다. 에이전트 지시는 항상 `AGENTS.md`에 쓴다.

## 새 도메인 추가 체크리스트

1. 패키지 생성: `<domain>/controller`, `service`, `data/{dto,entity,exception,repository}` (`enums`는 필요할 때만)
2. 엔티티, Repository, DTO, 예외, Service, Controller 순으로 작성
3. `SecurityConfiguration`에 경로 권한 추가 (아래 [보안 경로](#보안-경로))
4. 도메인 이름이 SQL 예약어(`order`, `user`, `group` 등)면 `@Table(name = "orders")`처럼 피한다
5. `./gradlew compileJava`로 컴파일 확인
6. 문서 갱신 ([위 표](#문서-갱신-규칙))

## 패키지 규칙

- 도메인 패키지는 `common`에 의존해도 되지만, `common`이 도메인에 의존해선 안 된다. 예외: `JwtTokenProvider`가 `UserRepository`를 참조
- DTO는 `data/dto/`에 둔다. `user` 도메인만 `request/`, `response/`로 한 겹 더 나뉜다
- 다른 도메인의 사용자 엔티티가 필요하면 `UserService.getUserEntity(username)`을 쓴다
- `nextStep`은 `UserService.resolveNextStep(user)`로만 계산한다. 다른 곳에서 같은 분기를 다시 만들지 않는다

## 외부 ID

- DB PK는 `Long`으로 두고, API로 주고받는 ID는 `IdPrefix`로 `접두어_숫자` 문자열을 만든다 (예: `IdPrefix.GARDEN.format(garden.getId())`는 `gdn_3`)
- 경로 변수나 본문으로 받은 ID는 `IdPrefix.GARDEN.parse(gardenId)`로 PK를 얻는다. 형식이 틀리면 C002가 나간다
- 새 종류의 ID가 필요하면 `IdPrefix`에 항목을 추가한다

## 응답

- 컨트롤러는 `ResponseEntity`가 아니라 `GlobalResponse<T>`를 직접 반환한다. 상태 코드는 예외 핸들러가 정한다
- 서비스가 `GlobalResponse.ok(dto)`, 반환값이 없으면 `GlobalResponse.ok()`로 감싼다

```java
public GlobalResponse<OrderResponseDto> getOrder(@RequestParam Long id) {
    return orderService.getOrder(id);
}
```

## 커서 페이지네이션

목록 API는 `CursorPageRequest`로 받고 `CursorPageResponse<T>`로 돌려준다. 다음 페이지가 있는지는 한 건 더 조회해서(limit + 1) 판단한다.

```java
// Controller
@GetMapping("/gardens/{gardenId}/diary")
public GlobalResponse<CursorPageResponse<DiaryItemDto>> getDiary(@PathVariable String gardenId,
                                                                 @Valid @ModelAttribute CursorPageRequest pageRequest) {
    return diaryService.getDiary(SecurityUtil.getUsername(), gardenId, pageRequest);
}

// Service: 최신순(id 내림차순), cursor보다 작은 id부터 limit + 1건 조회
Long cursorId = pageRequest.getCursorId(IdPrefix.CARE_LOG);   // 첫 페이지면 null
List<CareLog> fetched = careLogRepository.findPage(garden, cursorId, PageRequest.of(0, pageRequest.getFetchSize()));

CursorPageResponse<DiaryItemDto> page = CursorPageResponse
        .of(fetched, pageRequest.getLimit(), careLog -> IdPrefix.CARE_LOG.format(careLog.getId()))
        .map(DiaryItemDto::from);
return GlobalResponse.ok(page);

// Repository
@Query("select c from CareLog c where c.garden = :garden and (:cursorId is null or c.id < :cursorId) order by c.id desc")
List<CareLog> findPage(Garden garden, Long cursorId, Pageable pageable);
```

## 파일 업로드

- `multipart/form-data`로 받은 `MultipartFile`을 `FileUploadService.uploadImage(file, "care-photos")` 또는 `uploadAudio(file, "voice-memos")`에 넘기면 검증 후 저장하고 URL을 돌려준다
- 형식, 크기, 길이 검증은 `FileUploadService`가 한다. 도메인에서 다시 검사하지 않는다 (위반 시 C4001)
- `directory`는 도메인별로 고정된 이름을 쓴다. 사용자 입력을 넣지 않는다
- 저장소를 직접 다루지 않는다. `FileStorage`는 `FileUploadService`만 쓴다

```java
@PostMapping(value = "/caretaker/tasks/{taskId}/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
public GlobalResponse<TaskPhotoResponseDto> uploadPhoto(@PathVariable String taskId, @RequestPart("file") MultipartFile file) {
    return taskService.uploadPhoto(SecurityUtil.getUsername(), taskId, file);
}
```

## 날짜와 시각

- 응답 DTO의 날짜는 `LocalDate`, 시각은 `OffsetDateTime`으로 둔다. 각각 `YYYY-MM-DD`, `2026-11-10T09:30:00+09:00`으로 나간다
- 엔티티의 `LocalDateTime`은 `DateTimes.toOffset(entity.getCreatedAt())`로 바꿔 담는다. 응답 DTO에 `LocalDateTime`을 그대로 두면 오프셋이 빠진다
- `@JsonFormat`으로 필드마다 형식을 따로 정하지 않는다

## 외부 API 호출

- `RestClient externalRestClient` 빈을 주입받아 `externalRestClient.mutate().baseUrl("https://...").build()`로 파생해서 쓴다. 타임아웃이 이미 걸려 있으므로 `RestClient.create()`나 `RestTemplate`을 새로 만들지 않는다
- 외부 API 클라이언트는 그 데이터를 쓰는 도메인의 `client/` 패키지에 둔다 (예: `weather/client/KmaClient`)
- API 키는 `${ENV_VAR}`로 받고 `.env.example`에 추가한다
- 아직 만들어지지 않은 도메인이나 외부 서비스에 기대는 코드는 인터페이스와 기본 빈(`@ConditionalOnMissingBean`)으로 접점을 만든다 (`LlmClient`, `HarvestGardenReader`). 구현체를 `@Component`로 등록하면 기본 빈이 빠진다
- LLM, STT, TTS는 `LlmClient`, `SttClient`, `TtsClient` 인터페이스로만 호출한다. 실패하면 `AiClientException`이 나오므로, 규칙 기반 대체가 있는 로직(오늘의 작업 생성 등)은 이 예외를 잡아 대체한다

## 알림 발송

알림은 `NotificationService.send(...)`만 호출한다. `Notification` 엔티티나 저장소를 도메인에서 직접 쓰지 않는다. 프론트가 웹이라 기기 푸시는 없고, 저장한 알림을 화면이 `GET /notifications`로 가져간다.

```java
notificationService.send(NotificationSendDto.builder()
        .receiver(owner)                       // 수신자 User 엔티티
        .type(NotificationType.CARE_DONE)
        .title("순자 할머니가 토순이를 돌봤어요")
        .body(careLog.getSummary())
        .targetScreen("DT_DIARY")              // 눌렀을 때 이동할 화면 코드 (api-spec.md 3장). 없으면 생략
        .targetId(IdPrefix.CARE_LOG.format(careLog.getId()))
        .build());
```

- 호출한 서비스 메서드의 트랜잭션에 참여한다. 그 작업이 롤백되면 알림도 저장되지 않는다
- 새 알림 종류가 필요하면 `NotificationType`에 추가하고 `api-detail.md` 7.1과 `architecture.md`의 표를 함께 고친다
- 문구는 짧고 쉬운 말로 쓴다 (관리사 화면은 읽어주기를 쓴다)

## 날씨 조회

- 날씨가 필요하면 `WeatherService`만 쓴다. `WeatherForecastRepository`를 도메인에서 직접 보지 않는다. 그래야 시연 날씨 덮어쓰기(`PUT /demo/weather`)가 모든 화면에 똑같이 반영된다
- 현재 날씨는 `getCurrentWeather(nx, ny)`, 시간대별 예보는 `getForecasts(...)`, 적산온도용 일별 기온은 `getDailyWeather(...)`
- `getCurrentWeather`는 수집된 값이 없으면 비어 있다. 비어 있을 때의 처리를 호출한 쪽에서 정한다

## 배치 작업

배치 로직은 서비스 메서드 하나에 두고, 스케줄러와 내부 API가 같은 메서드를 호출한다. 스케줄러나 컨트롤러에 로직을 쓰지 않는다.

```java
// 로직: 서비스
@Service
public class WeatherSyncService {
    @Transactional
    public WeatherSyncResponseDto sync(List<String> farmIds) { ... }
}

// 주기 실행: <domain>/scheduler
@Component
@RequiredArgsConstructor
public class WeatherSyncScheduler {
    private final WeatherSyncService weatherSyncService;

    @Scheduled(cron = "0 0 * * * *")   // 1시간마다. 시간대는 JVM 기본(Asia/Seoul)
    public void run() {
        weatherSyncService.sync(List.of());
    }
}

// 수동 실행: /internal 컨트롤러 (X-Service-Key 검증은 필터가 한다)
@PostMapping("/internal/weather/sync")
public GlobalResponse<WeatherSyncResponseDto> sync(@RequestBody(required = false) WeatherSyncRequestDto request) {
    return GlobalResponse.ok(weatherSyncService.sync(request == null ? List.of() : request.getFarmIds()));
}
```

- `/internal/**`와 `/demo/**`의 헤더 검증과 권한은 필터와 `SecurityConfiguration`에 이미 있다. 컨트롤러에서 헤더를 다시 확인하지 않는다
- 스케줄러 메서드는 예외가 밖으로 나가면 로그만 남고 끝난다. 실패를 알아야 하면 서비스에서 로그를 남긴다

## 값을 읽고 고치는 작업 (잠금)

XP, 스트릭, 보상처럼 지금 값을 읽어 판단하고 고치는 작업은 동시에 들어온 요청이 같은 값을 보고 두 번 처리하지 않게 행을 잠근다.

1. 텃밭의 값을 고치는 서비스 메서드는 `GardenService.getOwnedGardenForUpdate(username, gardenId)`나 `findMyGardenForUpdate(username)`로 텃밭을 잠그고, 그 뒤에 상태(이미 응원했는지, 보상을 받았는지)를 읽는다. 잠그기 전에 읽은 값으로 판단하지 않는다.
2. XP는 `XpService.grant`, 배지는 `BadgeService.grant`, 작물 단계는 `EvolutionEventService.advanceStage`, 퀘스트 진행도는 `QuestService.record`로만 바꾼다. 모두 호출한 쪽의 트랜잭션에 참여한다.
3. 잠그는 쿼리(`@Lock(PESSIMISTIC_WRITE)`)에는 다른 테이블 조인이나 서브쿼리를 넣지 않는다. PK를 먼저 읽고 PK로 잠근다. 조인하면 그 테이블의 행까지 잠겨 교착이 생기고, 서브쿼리를 넣으면 MariaDB가 "Record has changed since last read"로 실패한다.
4. 격리 수준은 READ COMMITTED다 (`application.yml`). 이유는 [architecture.md](architecture.md#동시-요청과-잠금)를 본다.
5. 여러 요청이 동시에 처음 만들 수 있는 행(그 주의 리그, 퀘스트)은 유니크 제약을 두고 `REQUIRES_NEW` 트랜잭션에서 만든다 (`LeagueCreator`, `WeeklyQuestCreator`).

## 예외

1. `ErrorCode`에 `이름("코드", "한국어 메시지", HTTP상태)` 추가
2. 도메인의 `data/exception/`에 `GlobalException`을 상속한 예외 추가 (인자 없는 생성자)
3. 서비스에서 던지기만 하면 `GlobalExceptionHandler`가 처리한다

코드 체계: `C` + HTTP 상태. 같은 상태에 여러 개면 뒤에 일련번호 (`C4011`, `C4012`). 단순 조회 실패는 기존 `DATA_NOT_FOUND`를 재사용해도 된다.

```java
public class OrderNotFoundException extends GlobalException {
    public OrderNotFoundException() {
        super(ErrorCode.DATA_NOT_FOUND);
    }
}
```

## 서비스

- `@Service @RequiredArgsConstructor`, 로그가 필요하면 `@Slf4j`
- 의존성은 `private final` 필드 (Lombok 생성자 주입)
- 쓰기 메서드 `@Transactional`, 읽기 `@Transactional(readOnly = true)` (`org.springframework.transaction.annotation.Transactional`)
- 엔티티 조회는 `private <Domain> get<Domain>Entity(Long id)` 헬퍼 + `orElseThrow(XxxNotFoundException::new)`
- 엔티티는 Lombok `@Builder`로 생성
- 서비스는 `SecurityContextHolder`를 직접 보지 않는다. 사용자 아이디는 컨트롤러에서 받는다
- 생성 시각 같은 감사 필드는 `LocalDateTime.now()`로 직접 채운다 (`@EnableJpaAuditing` 없음)

## 컨트롤러

- `@RestController @RequestMapping("/<domain>") @RequiredArgsConstructor`. 경로는 `api-spec.md` 그대로 쓰고 `/api/v1` 같은 접두어를 붙이지 않는다`
- 현재 사용자가 필요하면 `SecurityUtil.getUsername()`(로그인 아이디)을 컨트롤러에서 읽어 서비스에 넘긴다
- 요청 바디에는 `@Valid @RequestBody`
- Swagger 설명은 `@Operation(summary = "...")`

## 엔티티

- `@Data @Builder @NoArgsConstructor @AllArgsConstructor @Entity @Table(name = "...")`
- PK: `@Id @GeneratedValue(strategy = GenerationType.IDENTITY) Long id`
- 기본값이 필요한 필드는 `@Builder.Default`
- 사용자 연관: `@ManyToOne @JoinColumn(name = "user_id", nullable = false) User user`
- enum 컬럼은 `@Enumerated(EnumType.STRING)`

## DTO

- `@Data @Builder @NoArgsConstructor @AllArgsConstructor`
- 요청 DTO에만 `jakarta.validation` 애너테이션(`@NotBlank`, `@NotNull`, `@Email`, `@Size`)
- 검증 실패, 읽을 수 없는 본문(잘못된 enum 값 포함), 필수 쿼리 누락은 `GlobalExceptionHandler`가 C002로 변환한다

## 보안 경로

- 공개 엔드포인트는 `SecurityConfiguration`의 `permitAll()` 목록에 추가
- `api-spec.md` 4장의 경로별 권한은 `SecurityConfiguration`에 이미 매핑되어 있다 ([architecture.md의 경로별 권한](architecture.md#경로별-권한-securityconfiguration)). 새 도메인은 명세와 다른 권한이 필요할 때만 고친다
- 권한 제한은 `.requestMatchers("/<domain>/**").hasRole("OWNER")` 또는 `.hasAnyRole("OWNER", "CARETAKER")`를 `.anyRequest().authenticated()` **앞에** 넣는다. 먼저 맞는 규칙이 적용되므로 더 구체적인 경로를 위에 둔다
- `hasRole`에는 `ROLE_` 접두사 없이 쓴다
- ADMIN은 RoleHierarchy로 USER, OWNER, CARETAKER 권한을 자동으로 갖는다. ADMIN 전용 경로만 `hasRole("ADMIN")`
- USER, OWNER, CARETAKER는 서로 포함하지 않는다. 명세의 "USER, OWNER, CARETAKER"는 `hasAnyRole("USER", "OWNER", "CARETAKER")`로 쓴다

## 로그

`[메서드명] 한국어 설명: {}` 형식으로 통일한다.

```java
log.info("[signUp] 새로운 사용자 등록: {}", user.getUsername());
log.error("[logout] 유효하지 않은 Access Token으로 로그아웃 시도");
```

토큰 값, 비밀번호, 개인정보 원문은 남기지 않는다. 식별자(로그인 아이디) 정도까지만.

## 설정

- 공통은 `application.yml`, 프로필별 값은 `application-local.yml` / `application-prod.yml`
- 모든 값은 `${ENV_VAR:기본값}` 형태로 환경변수 우선
- prod에는 기본값을 두지 않는다. 누락되면 부팅이 실패하는 편이 낫다. 예외: 환경과 무관한 튜닝 값(타임아웃, 스케줄러 사용 여부, 저장소 종류)과 아직 제공자가 정해지지 않은 AI 설정은 `application.yml`에 기본값을 둔다
- 새 환경변수를 추가하면 `.env.example`에도 추가한다. 비밀 값은 주석 처리한 줄과 생성 방법을 적는다
- 날짜와 시간은 JVM 기본 시간대(Asia/Seoul)를 따른다. 코드에서 `ZoneId`를 따로 하드코딩하지 않는다

## 주의할 점

- `./gradlew build`는 DB 없이 실패한다. `./gradlew compileJava` 또는 `./gradlew build -x test`를 쓴다
- DB가 필요 없는 단위 테스트는 `./gradlew test --tests 'community.likelion.growwithusserver.common.*'`처럼 패키지를 지정해 돌린다. 테스트 메서드 이름은 한국어로 써도 된다
- 서비스 단위 테스트는 저장소를 Mockito(`@ExtendWith(MockitoExtension.class)`)로 대체해 DB 없이 돌게 쓴다 (예: `VisitServiceTest`)
- jjwt는 0.12.x API다: `Jwts.builder().subject()`, `.signWith(key, Jwts.SIG.HS256)`, `Jwts.parser().verifyWith(key).build().parseSignedClaims()`. 구버전 `setSubject`/`parseClaimsJws`는 컴파일되지 않는다
- Spring Boot 4의 Jackson은 `tools.jackson.databind.ObjectMapper`다 (`com.fasterxml` 아님)
- prod는 `ddl-auto: validate`라 새 엔티티나 컬럼을 추가하면 prod DB 스키마도 직접 맞춰야 한다
