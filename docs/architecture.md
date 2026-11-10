# 현재 구조

> 코드의 현재 상태를 그대로 기록한 문서다. 기능을 추가하거나 바꾸면 이 문서도 함께 갱신한다 ([development-guide.md](development-guide.md#문서-갱신-규칙)).
>
> 마지막 갱신: 2026-11-11, 인증 및 사용자 API를 명세에 맞춤 (아이디 로그인, 역할 선택, nextStep, 경로 접두어 제거), product 예제 도메인 삭제

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
| 시간대 | JVM 기본 `Asia/Seoul` (`GrowwithusServerApplication.main`에서 `TimeZone.setDefault`) |
| 앱 클래스 | `GrowwithusServerApplication` |

## 패키지 구조

```
community.likelion.growwithusserver/
├── GrowwithusServerApplication.java  # main()에서 JVM 기본 시간대를 Asia/Seoul로 고정
├── common/                         # 공통 모듈 (도메인에 의존하지 않음, 예외: JwtTokenProvider가 UserRepository 참조)
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
│   │   ├── JwtAuthenticationEntryPoint  # 401 응답 (C4011)
│   │   ├── JwtAccessDeniedHandler  # 403 응답 (C403, 역할 미선택 사용자는 C4031)
│   │   ├── SecurityUtil            # getUsername(): 현재 사용자 로그인 아이디
│   │   ├── AuthenticationToken     # {accessToken, refreshToken, expiresIn}
│   │   └── exception/
│   │       ├── TokenInvalidException        # C4013
│   │       └── AuthenticationInfoException  # C4011, SecurityUtil.getUsername() 실패 시
│   └── swagger/
│       └── SwaggerConfiguration    # OpenAPI 정보 + bearerAuth 스킴
└── user/                           # 회원 / 인증 도메인
    ├── controller/    AuthController (/auth), UserController (/users)
    ├── service/       AuthService (가입, 로그인, 재발급, 로그아웃), UserService (사용자 조회, nextStep, 토큰 발급, 역할, 푸시 토큰)
    └── data/
        ├── dto/request/   SignUpRequestDto, LoginRequestDto, RefreshRequestDto, RoleChangeRequestDto, PushTokenRequestDto
        ├── dto/response/  AuthResponseDto, TokenRefreshResponseDto, UsernameCheckResponseDto, UserMeResponseDto, RoleChangeResponseDto
        ├── entity/        User (implements UserDetails), PushToken
        ├── enums/         UserRole, NextStep, PushPlatform
        ├── exception/     UsernameDuplicationException, PasswordInvalidException, UserNotFoundException, RoleNotAllowedException
        └── repository/    UserRepository, PushTokenRepository
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
| POST | `/users/me/push-tokens` | USER, OWNER, CARETAKER | `PushTokenRequestDto` | `null` | 푸시 토큰 등록. 같은 토큰이면 사용자와 플랫폼만 갱신 |

### 경로별 권한 (`SecurityConfiguration`)

`api-spec.md` 4장의 권한 열을 그대로 옮겼다. 아직 컨트롤러가 없는 도메인의 경로도 미리 매핑해 두었으므로, 새 도메인은 명세와 권한이 다를 때만 이 표와 코드를 고친다. 위에서부터 먼저 맞는 규칙이 적용된다.

| 순서 | 경로 | 권한 |
| --- | --- | --- |
| 1 | `/swagger-ui/**`, `/v3/api-docs/**`, `/swagger-resources/**`, `/webjars/**` | 공개 |
| 2 | POST `/auth/signup`, `/auth/login`, `/auth/refresh`, GET `/auth/username-check` | 공개 |
| 3 | `/auth/logout` | USER, OWNER, CARETAKER |
| 4 | `/users/me/badges` | OWNER |
| 5 | `/users/me/**` | USER, OWNER, CARETAKER |
| 6 | `/farms/*/plots/**` | USER, OWNER |
| 7 | `/farms/**`, `/crops/**` | USER, OWNER, CARETAKER |
| 8 | POST `/owners/me/profile` | USER, OWNER |
| 9 | `/owners/**` | OWNER |
| 10 | POST `/caretakers/me/profile` | USER, CARETAKER |
| 11 | `/caretakers/**` | CARETAKER |
| 12 | `/tts` | USER, CARETAKER |
| 13 | `/caretaker/**`, `/gardens/*/stage-checks` | CARETAKER |
| 14 | `/gardens/**`, `/care-logs/**`, `/events/**`, `/leagues/**`, `/quests/**`, `/quizzes/**`, `/visits/**` | OWNER |
| 15 | `/notifications/**` | OWNER, CARETAKER |
| 16 | 그 외 모든 경로 | 인증만 필요 |

ADMIN은 RoleHierarchy로 위 모든 권한을 갖는다. `/internal/**`, `/demo/**`는 아직 매핑하지 않았다 (공통 모듈 작업에서 추가).

### DTO

| DTO | 필드 |
| --- | --- |
| `SignUpRequestDto` | `name` (`@NotBlank @Size(max=20)`), `username` (`@NotBlank`, 영문 소문자와 숫자 4~20자), `password` (`@NotBlank @Size(8~64)`) |
| `LoginRequestDto` | `username`, `password` (`@NotBlank`) |
| `RefreshRequestDto` | `refreshToken` (`@NotBlank`) |
| `RoleChangeRequestDto` | `role` (`@NotNull UserRole`, `OWNER`와 `CARETAKER`만 허용) |
| `PushTokenRequestDto` | `token` (`@NotBlank @Size(max=512)`), `platform` (`@NotNull PushPlatform`: `ANDROID`, `IOS`) |
| `AuthResponseDto` | `userId`, `accessToken`, `refreshToken`, `expiresIn` (초), `role`, `nextStep` (조립은 `AuthService.createAuthResponse` 한 곳에서만) |
| `TokenRefreshResponseDto` | `accessToken`, `refreshToken`, `expiresIn` (초) |
| `UsernameCheckResponseDto` | `username`, `available` |
| `UserMeResponseDto` | `userId`, `name`, `username`, `role`, `nextStep`, `owner` (`nickname`, `district`, `gardenId`), `caretaker` (`caretakerId`, `name`, `farmId`, `avatarUrl`) |
| `RoleChangeResponseDto` | `role`, `accessToken`, `refreshToken`, `nextStep` |

`userId`는 `usr_` 접두어에 PK를 붙인 문자열이다 (예: `usr_12`, `User.getPublicId()`).

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

### `PushToken` (`push_token` 테이블)

| 필드 | 타입 | 제약 |
| --- | --- | --- |
| `id` | Long | PK, IDENTITY |
| `token` | String | not null, unique, 512자 |
| `platform` | `PushPlatform` | not null, `EnumType.STRING` |
| `user` | `User` | `@ManyToOne(LAZY)`, `user_id` not null |
| `createdAt` | LocalDateTime | updatable=false |
| `updatedAt` | LocalDateTime | 등록, 갱신 시 직접 채움 |

## 에러 코드

| ErrorCode | code | HTTP | 메시지 | 사용처 |
| --- | --- | --- | --- | --- |
| `INVALID_REQUEST` | C001 | 400 | 잘못된 요청입니다. | 미사용 |
| `INVALID_PARAMETER` | C002 | 400 | 유효하지 않은 파라미터입니다. | `@Valid` 검증 실패, 읽을 수 없는 본문 (잘못된 enum 값 포함), 필수 쿼리 누락, `RoleNotAllowedException` |
| `VERIFICATION_INVALID` | C4011 | 401 | 인증 정보가 유효하지 않습니다. | EntryPoint (미인증 접근), `AuthenticationInfoException` |
| `PASSWORD_INVALID` | C4012 | 401 | 비밀번호가 일치하지 않습니다. | `PasswordInvalidException` |
| `TOKEN_INVALID` | C4013 | 401 | 유효하지 않은 토큰입니다. | `TokenInvalidException` |
| `ACCESS_DENIED` | C403 | 403 | 승인되지 않은 사용자입니다. | AccessDeniedHandler (권한 부족) |
| `ROLE_NOT_SELECTED` | C4031 | 403 | 역할을 먼저 선택해야 합니다. | AccessDeniedHandler (역할 선택 전 `USER`의 접근) |
| `USER_DATA_NOT_FOUND` | C4041 | 404 | 사용자를 찾을 수 없습니다. | `UserNotFoundException` |
| `DATA_NOT_FOUND` | C404 | 404 | 정보를 불러올 수 없습니다. | 매핑되지 않은 경로 (`NoResourceFoundException`), 단순 조회 실패 |
| `USERNAME_DUPLICATION` | C4092 | 409 | 이미 사용 중인 아이디입니다. | `UsernameDuplicationException` |
| `UNKNOWN_ERROR` | C500 | 500 | 오류가 발생하였습니다. | 처리되지 않은 모든 예외 |

## 설정

| 파일 | 내용 |
| --- | --- |
| `application.yml` | 공통: `.env` import(`optional:file:.env[.properties]`), 활성 프로필(`SPRING_PROFILES_ACTIVE`, 기본 `local`), JPA open-in-view off, JWT 만료 시간, Swagger 경로 |
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

## 로컬 실행

```bash
cp .env.example .env   # 필요하면 값 수정
docker run -d -p 3306:3306 -e MARIADB_ROOT_PASSWORD=root -e MARIADB_DATABASE=growwithus-server mariadb:11
docker run -d -p 6379:6379 redis:7
./gradlew bootRun
```

- 컴파일만: `./gradlew compileJava`
- DB 없이 빌드: `./gradlew build -x test` (`contextLoads` 테스트가 실제 MariaDB를 요구)
- Swagger: http://localhost:8080/swagger-ui.html

## 알려진 이슈 / 결정 대기

| 항목 | 현재 상태 | 영향 |
| --- | --- | --- |
| `profileRegistered` 갱신 | 주인, 관리사 프로필 도메인이 아직 없어 항상 false | `nextStep`이 `PROFILE_OWNER`, `PROFILE_CARE`에서 멈춘다. 프로필 등록 API를 만들 때 true로 바꾸고, 역할을 되돌렸을 때 기존 프로필이 있으면 true로 복원하도록 맞춘다 |
| `/users/me`의 `owner`, `caretaker` | 항상 `null` | 프로필, 텃밭 도메인을 만들 때 `UserService.getMe`에서 채운다 |
| 기존 로컬 DB의 `user` 테이블 | `ddl-auto: update`는 예전 `email` 컬럼(not null)을 지우지 않는다 | 가입 시 insert가 실패한다. 로컬 DB의 `user`, `product` 테이블을 직접 drop하고 다시 띄운다 |
| prod `ddl-auto: validate` | 스키마 자동 생성 안 함 | prod DB에 테이블을 미리 만들어야 부팅됨 (마이그레이션 도구 없음) |
| `@CreatedDate` 미동작 | `@EnableJpaAuditing` 없음 | 감사 필드는 서비스에서 `LocalDateTime.now()`로 직접 채운다 |
| 검증 실패 메시지 | 클라이언트에는 C002 고정 메시지만 반환 | 어떤 필드가 틀렸는지는 서버 로그에만 남음 |
| 테스트 | `contextLoads` 하나뿐 | |
