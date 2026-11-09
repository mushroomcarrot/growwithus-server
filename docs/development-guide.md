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
- 다른 도메인의 사용자 엔티티가 필요하면 `AuthService.getUserEntity(email)`을 쓴다

## 응답

- 컨트롤러는 `ResponseEntity`가 아니라 `GlobalResponse<T>`를 직접 반환한다. 상태 코드는 예외 핸들러가 정한다
- 서비스가 `GlobalResponse.ok(dto)`, 반환값이 없으면 `GlobalResponse.ok()`로 감싼다

```java
public GlobalResponse<OrderResponseDto> getOrder(@RequestParam Long id) {
    return orderService.getOrder(id);
}
```

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
- 서비스는 `SecurityContextHolder`를 직접 보지 않는다. 사용자 이메일은 컨트롤러에서 받는다
- 생성 시각 같은 감사 필드는 `LocalDateTime.now()`로 직접 채운다 (`@EnableJpaAuditing` 없음)

## 컨트롤러

- `@RestController @RequestMapping("/api/v1/<domain>") @RequiredArgsConstructor`
- 현재 사용자가 필요하면 `SecurityUtil.getUsername()`(이메일)을 컨트롤러에서 읽어 서비스에 넘긴다
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
- 검증 실패는 `GlobalExceptionHandler`가 C002로 변환한다

## 보안 경로

- 공개 엔드포인트는 `SecurityConfiguration`의 `permitAll()` 목록에 추가
- 권한 제한은 `.requestMatchers("/api/v1/<domain>/**").hasRole("OWNER")` 또는 `.hasAnyRole("OWNER", "MANAGER")`를 `.anyRequest().authenticated()` **앞에** 넣는다. 순서가 뒤바뀌면 매처가 무시된다
- `hasRole`에는 `ROLE_` 접두사 없이 쓴다
- ADMIN은 RoleHierarchy로 OWNER와 MANAGER 권한을 자동으로 갖는다. ADMIN 전용 경로만 `hasRole("ADMIN")`

## 로그

`[메서드명] 한국어 설명: {}` 형식으로 통일한다.

```java
log.info("[signUp] 새로운 사용자 등록: {}", user.getEmail());
log.error("[logout] 유효하지 않은 Access Token으로 로그아웃 시도");
```

토큰 값, 비밀번호, 개인정보 원문은 남기지 않는다. 식별자(이메일) 정도까지만.

## 설정

- 공통은 `application.yml`, 프로필별 값은 `application-local.yml` / `application-prod.yml`
- 모든 값은 `${ENV_VAR:기본값}` 형태로 환경변수 우선
- prod에는 기본값을 두지 않는다. 누락되면 부팅이 실패하는 편이 낫다
- 새 환경변수를 추가하면 `.env.example`에도 추가한다. 비밀 값은 주석 처리한 줄과 생성 방법을 적는다
- 날짜와 시간은 JVM 기본 시간대(Asia/Seoul)를 따른다. 코드에서 `ZoneId`를 따로 하드코딩하지 않는다

## 주의할 점

- `./gradlew build`는 DB 없이 실패한다. `./gradlew compileJava` 또는 `./gradlew build -x test`를 쓴다
- jjwt는 0.12.x API다: `Jwts.builder().subject()`, `.signWith(key, Jwts.SIG.HS256)`, `Jwts.parser().verifyWith(key).build().parseSignedClaims()`. 구버전 `setSubject`/`parseClaimsJws`는 컴파일되지 않는다
- Spring Boot 4의 Jackson은 `tools.jackson.databind.ObjectMapper`다 (`com.fasterxml` 아님)
- prod는 `ddl-auto: validate`라 새 엔티티나 컬럼을 추가하면 prod DB 스키마도 직접 맞춰야 한다
