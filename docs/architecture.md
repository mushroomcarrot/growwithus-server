# 현재 구조

> 코드의 현재 상태를 그대로 기록한 문서다. 기능을 추가하거나 바꾸면 이 문서도 함께 갱신한다 ([development-guide.md](development-guide.md#문서-갱신-규칙)).
>
> 마지막 갱신: 2026-11-09, JVM 기본 시간대 Asia/Seoul 고정, .env.example 추가 및 .env 로딩

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
│   │   ├── JwtAccessDeniedHandler  # 403 응답 (C403)
│   │   ├── SecurityUtil            # getUsername(): 현재 사용자 이메일
│   │   ├── AuthenticationToken     # {accessToken, refreshToken}
│   │   └── exception/
│   │       ├── TokenInvalidException        # C4013
│   │       └── AuthenticationInfoException  # C4011, SecurityUtil.getUsername() 실패 시
│   └── swagger/
│       └── SwaggerConfiguration    # OpenAPI 정보 + bearerAuth 스킴
├── user/                           # 회원 / 인증 도메인
│   ├── controller/AuthController
│   ├── service/AuthService
│   └── data/
│       ├── dto/request/   SignUpRequestDto, SignInRequestDto, ReissueRequestDto
│       ├── dto/response/  TokenResponseDto
│       ├── entity/        User (implements UserDetails)
│       ├── enums/         UserRole
│       ├── exception/     EmailDuplicationException, PasswordInvalidException, UserNotFoundException
│       └── repository/    UserRepository
└── product/                        # 예제 도메인 (구조 참고용, 필드는 자리표시자)
    ├── controller/ProductController
    ├── service/ProductService
    └── data/
        ├── dto/           ProductRequestDto, ProductResponseDto
        ├── entity/        Product
        ├── exception/     ProductNotFoundException
        └── repository/    ProductRepository
```

## API

모든 응답은 `GlobalResponse<T>`로 감싸진다.

| Method | Path | 권한 | 요청 | 응답 `data` | 설명 |
| --- | --- | --- | --- | --- | --- |
| POST | `/api/v1/auth/signup` | 공개 | `SignUpRequestDto` | `TokenResponseDto` | 회원가입 후 바로 토큰 발급 |
| POST | `/api/v1/auth/signin` | 공개 | `SignInRequestDto` | `TokenResponseDto` | 로그인 |
| POST | `/api/v1/auth/reissue` | 공개 | `ReissueRequestDto` | `TokenResponseDto` | RT로 AT, RT 재발급 (RT 로테이션) |
| POST | `/api/v1/auth/logout` | 인증 | 헤더 `Authorization: Bearer <AT>` | `null` | AT, RT 블랙리스트 등록 및 RT 삭제 |
| POST | `/api/v1/product` | OWNER, MANAGER (+ADMIN) | `ProductRequestDto` | `null` | 예제: 현재 사용자 소유로 생성 |
| GET | `/api/v1/product?id={id}` | OWNER, MANAGER (+ADMIN) | 쿼리 `id` | `ProductResponseDto` | 예제: 단건 조회 |

공개 경로 (`permitAll`): `/api/v1/auth/signin`, `/api/v1/auth/signup`, `/api/v1/auth/reissue`, `/swagger-ui/**`, `/v3/api-docs/**`, `/swagger-resources/**`, `/webjars/**`.
그 외 모든 경로는 `.anyRequest().authenticated()`이다. 로그인만 되어 있으면 권한 무관하게 접근 가능.

### DTO

| DTO | 필드 |
| --- | --- |
| `SignUpRequestDto` | `email` (`@NotBlank @Email`), `name` (`@NotBlank`), `password` (`@NotBlank @Size(8~64)`), `role` (`@NotNull UserRole`) |
| `SignInRequestDto` | `email` (`@NotBlank @Email`), `password` (`@NotBlank`) |
| `ReissueRequestDto` | `refreshToken` |
| `TokenResponseDto` | `accessToken`, `refreshToken`, `email`, `name` (조립은 `AuthService.createTokenResponse` 한 곳에서만) |
| `ProductRequestDto` | `name`, `description` (`@NotBlank`), 자리표시자 |
| `ProductResponseDto` | `id`, `name`, `description`, `createdAt`, 자리표시자 |

### 응답 형식

```json
// 성공
{ "data": { ... }, "error": { "code": null, "message": null } }

// 실패
{ "data": null, "error": { "code": "C4041", "message": "사용자를 찾을 수 없습니다." } }
```

## 권한

| `UserRole` | authority | 의미 (기획안 3.5) |
| --- | --- | --- |
| `ADMIN` | `ROLE_ADMIN` | 서비스 관리자 |
| `OWNER` | `ROLE_OWNER` | 텃밭 주인 (도시민) |
| `MANAGER` | `ROLE_MANAGER` | 텃밭 관리사 (어르신) |

- **RoleHierarchy** (`SecurityConfiguration.roleHierarchy()`): `ROLE_ADMIN > ROLE_OWNER`, `ROLE_ADMIN > ROLE_MANAGER`. OWNER와 MANAGER는 서로 포함하지 않는다.
- **역할 결정:** 회원가입 요청의 `role` 값을 그대로 저장한다. 기본값 없음.
- `hasRole("OWNER")`처럼 `ROLE_` 접두사 없이 쓴다.

## 인증 흐름

```
[signup / signin]
  1. AuthService.createAuthentication(user)
  2. JwtTokenProvider.generateToken()
    ├─ AT: subject=email, claim "authorities"="ROLE_XXX", 만료 1시간
    ├─ RT: subject=email, claim 없음, 만료 7일
    └─ Redis SET refresh:{email} = RT (TTL = RT 만료)

[인증된 요청]
  JwtAuthenticationFilter
    1. Authorization: Bearer <AT> 헤더에서 AT 추출
    2. validateToken (서명, 만료, 블랙리스트 검사)
    3. getAuthentication: claim "authorities" 사용, 없으면 DB에서 role 조회, 사용자도 없으면 권한 없음
    4. SecurityContext 등록 (principal username = email)

[reissue]
  1. RT 검증
  2. Redis의 refresh:{email}과 일치 확인
  3. AT, RT 새로 발급 (이전 RT는 덮어써져 즉시 무효)

[logout]
  1. AT 검증
  2. Redis refresh:{email}의 RT를 블랙리스트 등록 후 삭제
  3. AT 블랙리스트 등록
```

### Redis 키

| 키 | 값 | TTL |
| --- | --- | --- |
| `refresh:{email}` | Refresh Token | RT 만료 시간 |
| `blacklist:{token}` | `"logout"` | 해당 토큰의 남은 만료 시간 |

## 엔티티

### `User` (`user` 테이블)

| 필드 | 타입 | 제약 |
| --- | --- | --- |
| `id` | Long | PK, IDENTITY |
| `email` | String | not null, unique |
| `name` | String | not null |
| `password` | String | not null, BCrypt |
| `role` | `UserRole` | not null, `EnumType.STRING` |
| `registeredAt` | LocalDateTime | updatable=false, `AuthService.signUp`에서 직접 채움 |
| `modifiedAt` | LocalDateTime | `@UpdateTimestamp` |

`UserDetails` 구현, `getUsername()`은 email 반환.

### `Product` (`product` 테이블), 예제

`id`, `name`, `description`, `user` (`@ManyToOne`, `user_id` not null), `createdAt`

## 에러 코드

| ErrorCode | code | HTTP | 메시지 | 사용처 |
| --- | --- | --- | --- | --- |
| `INVALID_REQUEST` | C001 | 400 | 잘못된 요청입니다. | 미사용 |
| `INVALID_PARAMETER` | C002 | 400 | 유효하지 않은 파라미터입니다. | `@Valid` 검증 실패 |
| `VERIFICATION_INVALID` | C4011 | 401 | 인증 정보가 유효하지 않습니다. | EntryPoint (미인증 접근), `AuthenticationInfoException` |
| `PASSWORD_INVALID` | C4012 | 401 | 비밀번호가 일치하지 않습니다. | `PasswordInvalidException` |
| `TOKEN_INVALID` | C4013 | 401 | 유효하지 않은 토큰입니다. | `TokenInvalidException` |
| `ACCESS_DENIED` | C403 | 403 | 승인되지 않은 사용자입니다. | AccessDeniedHandler (권한 부족) |
| `USER_DATA_NOT_FOUND` | C4041 | 404 | 사용자를 찾을 수 없습니다. | `UserNotFoundException` |
| `DATA_NOT_FOUND` | C404 | 404 | 정보를 불러올 수 없습니다. | `ProductNotFoundException` |
| `EMAIL_DUPLICATION` | C4091 | 409 | 이미 존재하는 이메일입니다. | `EmailDuplicationException` |
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
| 회원가입 시 `ADMIN` 선택 가능 | `role`을 요청 그대로 저장 (의도된 결정, 2026-11-09) | 누구나 ADMIN으로 가입 가능. 바꾸려면 `AuthService.signUp`에서 거른다 |
| prod `ddl-auto: validate` | 스키마 자동 생성 안 함 | prod DB에 테이블을 미리 만들어야 부팅됨 (마이그레이션 도구 없음) |
| `@CreatedDate` 미동작 | `@EnableJpaAuditing` 없음 | 감사 필드는 서비스에서 `LocalDateTime.now()`로 직접 채운다 |
| 검증 실패 메시지 | 클라이언트에는 C002 고정 메시지만 반환 | 어떤 필드가 틀렸는지는 서버 로그에만 남음 |
| `product` 도메인 | 자리표시자 필드 | 실제 도메인으로 교체하거나 삭제 |
| 테스트 | `contextLoads` 하나뿐 | |
