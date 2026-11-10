# growwithus-server

용인 처인구 텃밭을 수지, 기흥 도시민에게 분양하고, 평일 관리는 처인구 어르신 "텃밭 관리사"가 AI가 정해준 작업으로 맡는 서비스의 백엔드. 도시민은 실제 날씨대로 자라는 작물 캐릭터를 듀오링고식 게임으로 키운다.

## 문서

작업 전에 관련 문서를 먼저 읽을 것.

1. [docs/architecture.md](docs/architecture.md): 현재 구조 (패키지, API, 권한, 인증 흐름, 엔티티, 에러 코드, 설정, 알려진 이슈)
2. [docs/development-guide.md](docs/development-guide.md): 개발 규칙, 새 도메인 추가 체크리스트, 문서 갱신 규칙
3. [docs/planning.md](docs/planning.md): 기획안 (문제 정의, 핵심 기능, 활용 데이터, 기술 구성, 시연 시나리오)
4. [docs/commit-convention.md](docs/commit-convention.md): 커밋 메시지 컨벤션 (태그, 형식, 작성 규칙)
5. [docs/github-convention.md](docs/github-convention.md): 브랜치 전략, Issue 및 PR 작성 가이드 (템플릿은 `.github/`)
6. [docs/api-spec.md](docs/api-spec.md): 대신텃밭 API 명세 요약 (공통 규칙, 권한, enum, 화면 코드, API 54개, 핵심 로직, 시연 시나리오)
7. [docs/api-detail.md](docs/api-detail.md): API 상세 명세서 (엔드포인트별 요청, 응답 예시, 에러 코드)

## 문서 갱신 (필수)

1. 기능을 추가하거나 동작을 바꾸면 같은 작업 안에서 `docs/architecture.md`를 갱신한다. 무엇을 어디에 고칠지는 [development-guide.md의 문서 갱신 규칙](docs/development-guide.md#문서-갱신-규칙)을 따른다.
2. 작업 방식이나 규칙이 바뀌면 이 파일(`AGENTS.md`)이나 `docs/development-guide.md`를 갱신한다.
3. 새 문서를 `docs/`에 추가하면 위 문서 목록에도 추가한다.
4. `CLAUDE.md`는 `@AGENTS.md`만 담는다. Claude와 Codex가 같은 지시를 읽도록 에이전트 지시는 항상 이 파일에 쓴다.

## 스택

Java 21, Spring Boot 4.1, Gradle, MariaDB, Redis, JWT (jjwt 0.12.x), Swagger

1. `./gradlew compileJava`: 컴파일 확인
2. `./gradlew build`는 DB 없이 실패한다 (`contextLoads`가 실제 MariaDB 연결 필요). DB 없이 빌드하려면 `./gradlew build -x test`

## 권한

`UserRole`: `ADMIN`, `OWNER`, `MANAGER`

1. `OWNER`는 텃밭 주인 (도시민), `MANAGER`는 텃밭 관리사 (어르신), `ADMIN`은 서비스 관리자
2. 회원가입 요청에서 role을 직접 고른다 (기획안 3.5)
3. `RoleHierarchy`: ADMIN은 OWNER와 MANAGER를 포함하고, OWNER와 MANAGER는 서로 포함하지 않는다

## 코드 규칙

자세한 내용은 [docs/development-guide.md](docs/development-guide.md)를 본다.

1. 응답은 `GlobalResponse<T>`로 감싼다
2. 예외는 `GlobalException`을 상속하고 `ErrorCode`에 항목을 추가한다
3. 로그는 `[메서드명] 한국어 설명: {}` 형식
4. 현재 사용자는 컨트롤러에서 `SecurityUtil.getUsername()`으로 가져와 서비스에 넘긴다

## Git

1. 기본 브랜치는 `develop`이다. 작업은 `develop`에 계속 병합하고, 마지막에 `develop`에서 `main`으로 PR을 올려 반영한다. 기능 PR의 base는 항상 `develop` (자세한 내용은 [docs/github-convention.md](docs/github-convention.md))
2. 커밋과 푸시는 사용자가 직접 한다. 에이전트는 git add, commit, push를 하지 않는다
3. 커밋 메시지를 쓰거나 제안할 때는 반드시 [docs/commit-convention.md](docs/commit-convention.md)를 따른다
4. Issue나 PR을 작성하거나 초안을 제안할 때는 [docs/github-convention.md](docs/github-convention.md)를 따른다
5. 커밋 메시지와 PR 본문에 `Co-Authored-By`나 "Generated with ..." 같은 AI 표기를 절대 넣지 않는다. 에이전트 기본 템플릿에 있어도 제거한다

## 작성 규칙 (모든 출력)

채팅 답변, 설명, 요약, 문서, 표, 제목, 목록, 주석 등 직접 작성하는 모든 텍스트에 항상 적용한다.

1. 가운데점(U+00B7, U+318D)을 쓰지 않는다. 쉼표, '및', '와', '과'로 바꾼다.
2. 긴 대시(U+2014, U+2013)를 쓰지 않는다. 마침표, 쉼표, 괄호로 바꾸거나 문장을 나눈다.
3. 화살표 기호(U+2192, U+2794) 등의 특수 문자를 쓰지 않는다.
4. 목록은 번호 목록을 우선 사용한다.
5. 문단은 내용이 바뀌는 지점에서 명확하게 나누고 문단 사이에 빈 줄을 넣는다.
6. 출력 전에 금지한 기호가 있는지 확인하고, 있으면 고친다. 이 규칙을 지켰다는 설명은 덧붙이지 않는다.
