# 대신텃밭 API 명세 요약

후속 추가: 주인 프로필의 familySize(선택, 1~20, 등록 생략 시 1), /users/me의 owner.familySize와 owner.sentHearts, 예약 인원 생략 시 가족 수 기본값을 지원한다. 내부 키 전용 GET/PUT /internal/crops/{cropId}/task-guidance와 POST /internal/weather/daily를 추가했다. 상세 필드와 운영 조건은 [ai-completion.md](ai-completion.md)를 따른다.

> 원본: Notion "API 명세서 (초안)" 데이터베이스 (API 54개). 쓰지 않는 푸시 토큰 등록을 제외하고 음성 재시도와 복구 조회 API를 추가했다. 작업 브랜치의 구현 범위와 추가 상태 필드는 [ai-integration.md](ai-integration.md), [voice-api.md](voice-api.md)를 함께 확인한다.
>
> 엔드포인트별 요청, 응답 필드는 [api-detail.md](api-detail.md)를 본다.

## 1. 서비스 한 줄 요약

수지, 기흥의 도시 가족이 처인구 텃밭 한 칸을 분양받으면, 평일 관리는 근처 어르신 "텃밭 관리사"가 AI가 정해준 작업으로 수행한다. 가족은 앱에서 실제 날씨대로 자라는 작물 캐릭터를 매일 키우고 주말에 직접 수확한다.

## 2. 공통 규칙

| 항목 | 내용 |
| --- | --- |
| 인증 | `Authorization: Bearer {accessToken}` (JWT, 만료 3600초) |
| 내부 배치 API | `/internal/*`, 헤더 `X-Service-Key` 필수 |
| 시연 API | `/demo/*`, ADMIN 계정 토큰과 헤더 `X-Demo-Mode: true` 모두 필수 |
| 날짜 형식 | 날짜 `YYYY-MM-DD`, 시각 ISO 8601 (`+09:00`) |
| 페이지네이션 | 커서 방식 (`cursor`, `limit`, 응답 `nextCursor`) |
| 파일 업로드 | `multipart/form-data` (사진 jpg, png 10MB 이하, 음성 webm, m4a, mp4, wav 60초 이하) |
| 숫자 정책 | 월 이용료 3만 원, 관리사 수당은 모두 **가정치** |

### 권한 (Permission)

| 값 | 의미 |
| --- | --- |
| GUEST | 비로그인 |
| USER | 로그인했지만 역할 선택 또는 프로필 등록 전 |
| OWNER | 텃밭 주인 (도시 가족) |
| CARETAKER | 텃밭 관리사 (처인구 어르신) |
| SYSTEM | 내부 배치 |
| DEMO | 발표 시연 전용 (ADMIN 계정 토큰 + `X-Demo-Mode` 헤더) |

### 주요 enum

| 이름 | 값 |
| --- | --- |
| role | OWNER, CARETAKER |
| stage | SEED, SPROUT, FLOWER, FRUIT (1~4단계) |
| weather.condition | SUNNY, CLOUDY, RAIN, HOT, COLD (시연은 SUNNY, RAIN, HOT) |
| character.mood | HAPPY, CALM, SAD |
| character.animation | sunny_happy, rain_umbrella, hot_sweat, sad_droop |
| task.type | WATER, SUPPORT, WEED, PRUNE, FERTILIZE, HARVEST_PREP, CHECK |
| stage-check observed | FLOWER, FRUIT, NO_CHANGE |
| diary item type | CARE_LOG, STAGE_CHANGE (REST_DAY는 계획 항목, 현재 미생성) |
| district | SUJI, GIHEUNG, CHEOIN |
| nextStep | ROLE_SELECT, PROFILE_OWNER, PROFILE_CARE, OWNER_HOME, CARE_TODAY |

### 예시 ID

`usr_minji`, `gdn_b14`, `farm_theme`, `crt_soonja`, `tsk_1110_b14`, `cl_1110_01`, `vm_1110_b14`, `pht_1110_b14`, `evt_fruit_b14`, `vst_1128_b14`

## 3. 화면 코드

| 코드 | 디자인 화면 | 사용자 |
| --- | --- | --- |
| DT_LOGIN | 로그인 (Main) | 공통 |
| DT_SIGNUP | 회원가입 | 공통 |
| DT_ROLE | 역할 선택 | 공통 |
| DT_PROFILE_OWNER | 주인 프로필 설정 | 주인 |
| DT_HOME | 주인 홈 (맑음, 비 오는 날) | 주인 |
| DT_DIARY | 성장 일기 | 주인 |
| DT_EVOLVE | 열매 진화 연출 | 주인 |
| DT_HARVEST | 수확과 방문 예약 | 주인 |
| DT_LEAGUE | 리그와 퀘스트 (디자인 미작성) | 주인 |
| DT_MY | 마이, 설정 (디자인 미작성) | 공통 |
| DT_PROFILE_CARE | 관리사 프로필 설정 | 관리사 |
| DT_CARE_TODAY | 관리사 오늘 할 일 | 관리사 |
| DT_CARE_PHOTO | 작업 인증 사진 | 관리사 |
| DT_CARE_VOICE | 말로 남기기 AI 정리 | 관리사 |
| DT_CARE_DONE | 작업 완료와 단계 확인 | 관리사 |
| DT_DEMO | 데모 컨트롤 패널 | 시연 |
| BATCH | 내부 배치 | 시스템 |

## 4. API 목록

### 4.1 User (8)

| Method | Endpoint | 기능 | 권한 | 화면 |
| --- | --- | --- | --- | --- |
| POST | /auth/signup | 회원가입 (이름, 아이디, 비밀번호) | GUEST | DT_SIGNUP |
| GET | /auth/username-check | 아이디 중복 확인 | GUEST | DT_SIGNUP |
| POST | /auth/login | 로그인, nextStep으로 이동 화면 안내 | GUEST | DT_LOGIN |
| POST | /auth/refresh | 액세스 토큰 재발급 | GUEST | 공통 |
| POST | /auth/logout | 로그아웃 | USER, OWNER, CARETAKER | DT_MY |
| GET | /users/me | 내 정보와 역할 (홈 분기 기준) | USER, OWNER, CARETAKER | DT_LOGIN, DT_HOME, DT_CARE_TODAY, DT_MY |
| PUT | /users/me/role | 역할 선택 및 변경 | USER, OWNER, CARETAKER | DT_ROLE, DT_MY |

### 4.2 Farm, Caretaker (10)

| Method | Endpoint | 기능 | 권한 | 화면 |
| --- | --- | --- | --- | --- |
| GET | /farms | 주말농장 목록 (공공데이터포털) | USER, OWNER, CARETAKER | DT_PROFILE_OWNER, DT_PROFILE_CARE |
| GET | /farms/{farmId}/plots/{plotCode} | 칸 번호로 작물과 담당 관리사 미리보기 | USER, OWNER | DT_PROFILE_OWNER |
| GET | /crops | 작물과 생육 기준 (농사로) | USER, OWNER, CARETAKER | DT_PROFILE_OWNER |
| POST | /owners/me/profile | 주인 프로필 등록 및 텃밭 시작 | USER, OWNER | DT_PROFILE_OWNER |
| PATCH | /owners/me/profile | 주인 프로필 수정 | OWNER | DT_MY |
| POST | /caretakers/me/profile | 관리사 프로필 등록 (성함, 농장) | USER, CARETAKER | DT_PROFILE_CARE |
| POST | /caretakers/me/avatar | AI 아바타 생성 요청 (초상권 동의 필수) | CARETAKER | DT_PROFILE_CARE, DT_MY |
| GET | /caretakers/me/avatar | AI 아바타 생성 결과 조회 (폴링) | CARETAKER | DT_PROFILE_CARE, DT_MY |
| GET | /caretakers/me/summary | 이번 달 활동 요약과 예상 수당 (가정치) | CARETAKER | DT_CARE_DONE, DT_MY |
| POST | /tts | 읽어주기 음성 (audio/mpeg) | USER, CARETAKER | DT_PROFILE_CARE, DT_CARE_TODAY |

### 4.3 Home, Diary, Game (13)

| Method | Endpoint | 기능 | 권한 | 화면 |
| --- | --- | --- | --- | --- |
| GET | /gardens/{gardenId}/home | 홈 한 번에 조회 (캐릭터, 날씨, 스트릭, XP, D-day, 최근 돌봄) | OWNER | DT_HOME |
| GET | /gardens/{gardenId}/growth | 성장 시뮬레이션 상세 (AI 핵심 2) | OWNER | DT_HOME, DT_HARVEST |
| POST | /gardens/{gardenId}/cheers | 오늘의 응원 (스트릭, XP) | OWNER | DT_HOME |
| POST | /care-logs/{careLogId}/hearts | 감사 하트 보내기 | OWNER | DT_HOME, DT_DIARY |
| GET | /gardens/{gardenId}/diary | 성장 일기 (필터, 원문과 정리문, dry_ 커서) | OWNER | DT_DIARY |
| GET | /gardens/{gardenId}/events | 미확인 진화 이벤트 | OWNER | DT_HOME, DT_EVOLVE |
| POST | /events/{eventId}/ack | 진화 이벤트 확인과 보상 지급 | OWNER | DT_EVOLVE |
| GET | /leagues/current | 이웃 텃밭 주간 리그 | OWNER | DT_LEAGUE, DT_MY |
| GET | /quests/weekly | 주간 퀘스트 | OWNER | DT_LEAGUE, DT_MY |
| POST | /quests/{questId}/claim | 퀘스트 보상 받기 | OWNER | DT_LEAGUE |
| GET | /users/me/badges | 내 배지 목록 | OWNER | DT_MY |
| GET | /gardens/{gardenId}/quizzes/today | [확장] 오늘의 텃밭 퀴즈 | OWNER | DT_HOME |
| POST | /quizzes/{quizId}/answers | [확장] 퀴즈 답 제출 | OWNER | DT_HOME |

### 4.4 Harvest (5)

| Method | Endpoint | 기능 | 권한 | 화면 |
| --- | --- | --- | --- | --- |
| GET | /gardens/{gardenId}/harvest | 수확 안내 (AI 예측 수확일, 함께 돌본 일수) | OWNER | DT_HARVEST |
| GET | /gardens/{gardenId}/visit-slots | 방문 가능 시간 | OWNER | DT_HARVEST |
| POST | /visits | 주말 방문 예약 (관리사 알림) | OWNER | DT_HARVEST |
| DELETE | /visits/{visitId} | 방문 예약 취소 (하루 전까지) | OWNER | DT_HARVEST |
| GET | /gardens/{gardenId}/outings | 처인 나들이 추천 (체험농장, 식당) | OWNER | DT_HARVEST |

### 4.5 Task (8)

| Method | Endpoint | 기능 | 권한 | 화면 |
| --- | --- | --- | --- | --- |
| GET | /caretaker/tasks/today | 오늘 할 일 카드 (AI 생성) | CARETAKER | DT_CARE_TODAY |
| POST | /caretaker/tasks/{taskId}/photos | 작업 인증 사진 (AI 사진 확인) | CARETAKER | DT_CARE_PHOTO |
| POST | /caretaker/tasks/{taskId}/voice-memos | 말로 남기기 (STT, LLM 정리) | CARETAKER | DT_CARE_TODAY, DT_CARE_VOICE |
| GET | /caretaker/tasks/{taskId}/voice-memos/latest | 마지막 저장 음성 메모와 처리 상태 복구 조회 | CARETAKER | DT_CARE_VOICE |
| POST | /caretaker/voice-memos/{voiceMemoId}/confirm | 음성 메모 확정 (맞아요, 보낼게요) | CARETAKER | DT_CARE_VOICE |
| POST | /caretaker/voice-memos/{voiceMemoId}/retry | 실패한 음성 인식 또는 문장 정리 재시도 | CARETAKER | DT_CARE_VOICE |
| POST | /caretaker/tasks/{taskId}/complete | 작업 완료 (돌봄 기록 생성, 가족 알림) | CARETAKER | DT_CARE_TODAY, DT_CARE_PHOTO, DT_CARE_DONE |
| POST | /gardens/{gardenId}/stage-checks | 성장 단계 확인 (시뮬레이션 보정) | CARETAKER | DT_CARE_DONE |

### 4.6 Notification (2)

| Method | Endpoint | 기능 | 권한 | 화면 |
| --- | --- | --- | --- | --- |
| GET | /notifications | 알림 목록 | OWNER, CARETAKER | DT_HOME, DT_CARE_DONE, DT_MY |
| PATCH | /notifications/{notificationId}/read | 알림 읽음 (all 가능) | OWNER, CARETAKER | DT_HOME, DT_CARE_DONE, DT_MY |

### 4.7 내부 배치 (6, SYSTEM)

| Method | Endpoint | 주기 | 기능 |
| --- | --- | --- | --- |
| POST | /internal/weather/sync | 1시간마다 | 기상청 단기예보, 실황 수집 |
| POST | /internal/ai/daily-tasks | 매일 05:00 | AI 오늘의 작업 생성과 규칙 검증 (AI 핵심 1) |
| POST | /internal/ai/growth-simulation | 매일 06:00 | 적산온도 누적, 단계와 수확일 갱신 (AI 핵심 2) |
| POST | /internal/streaks/check | 매일 00:05 | 스트릭 끊김 처리, 캐릭터 SAD |
| POST | /internal/leagues/settle | 매주 월 00:10 | 리그 정산, 새 리그와 퀘스트 생성 |
| POST | /internal/farms/sync | 매주 월 03:00 | 공공데이터포털 주말농장 동기화 |

### 4.8 시연 (4, DEMO)

| Method | Endpoint | 기능 | 화면 |
| --- | --- | --- | --- |
| GET | /demo/state | 데모 상태 (날씨, 꽃 3/4, 빨리 감기 18일) | DT_DEMO |
| PUT | /demo/weather | 날씨 바꾸기, 작업 즉시 재생성 | DT_DEMO, DT_HOME, DT_CARE_TODAY |
| POST | /demo/fast-forward | 시간 빨리 감기, 열매 진화 이벤트 발생 | DT_DEMO, DT_EVOLVE, DT_HARVEST |
| POST | /demo/reset | 맑음, 꽃 단계, 할 일 3곳으로 초기화 | DT_DEMO |

## 5. 핵심 로직

### AI 핵심 1. 오늘의 작업 생성

1. 입력: 기상청 예보, 농사로 재배 일정, 지난 작업 기록, 음성 메모 이상 징후
2. LLM이 텃밭별로 오늘 필요한 작업만 고른다.
3. 규칙 검증으로 엉뚱한 지시를 막는다 (비 예보 시 물 주기 제거, 하루 120분 상한 등).
4. LLM이 실패하면 규칙 기반 기본 작업으로 대체한다.
5. 결과: 관리사 카드의 `weatherNotice` ("오후에 비가 와요. 물은 안 주셔도 돼요."), `skippedTypes`

### AI 핵심 2. 성장 시뮬레이션

1. 일별 유효적산온도 = max(0, 평균기온 - 기준온도), 이 값을 누적한다.
2. 농사로 단계별 `requiredGdd`와 비교해 단계와 수확 예정일을 정한다 (방울토마토 기준온도 10℃).
3. 관리사 단계 확인(`stage-checks`)이 들어오면 실제 상태로 보정한다 (`calibratedAt`).
4. 단계가 오르면 진화 이벤트, 일기 카드, 알림이 생긴다.
5. LLM이 결과를 쉬운 말로 설명한다 (`explanation`).

### 관리사 작업 흐름

1. `GET /caretaker/tasks/today`로 카드를 받는다.
2. (선택) `POST .../photos`로 인증 사진을 올린다.
3. (선택) `POST .../voice-memos` 후 `POST .../confirm`으로 음성 메모를 확정한다.
4. `POST .../complete`로 돌봄 기록을 만들고 가족에게 알린다.
5. `stageCheck.required`가 true면 `POST /gardens/{gardenId}/stage-checks`를 호출한다.

### 온보딩 흐름

1. `signup` 또는 `login` 후 `nextStep`을 본다.
2. `PUT /users/me/role`로 역할을 고른다.
3. 주인은 `GET /farms`, `GET /farms/{farmId}/plots/{plotCode}`, `POST /owners/me/profile` 순서로 진행한다.
4. 관리사는 `GET /farms`, `POST /caretakers/me/profile`, (선택) `POST /caretakers/me/avatar` 순서로 진행한다.

## 6. 시연 시나리오 (2분)와 API

| 순서 | 장면 | 호출 API |
| --- | --- | --- |
| 1 | 주인 홈, 토순이와 순자 할머니 소개 | GET /gardens/gdn_b14/home |
| 2 | 날씨를 비로 바꾸면 우산 캐릭터, 물 주기 생략 작업 | PUT /demo/weather (RAIN) |
| 3 | 관리사 앱: 큰 카드, 읽어주기, 완료 | GET /caretaker/tasks/today, POST .../complete |
| 4 | 단계 확인 후 주인 화면 하트, 스트릭 | POST /gardens/gdn_b14/stage-checks, POST /care-logs/cl_1110_01/hearts |
| 5 | 18일 빨리 감기, 열매 진화와 수확 예약 | POST /demo/fast-forward, GET .../events, POST /visits |
| 6 | 리허설 반복 | POST /demo/reset |

## 7. 현재 구현과 차이 (2026-11-12 기준)

명세를 기준으로 맞춰 나간다. 현재 코드 상태는 [architecture.md](architecture.md)를 본다.

4.1 User의 8개, 4.4 Harvest의 5개, 4.6 Notification의 2개 API와 4.8 시연의 3개(state, weather, reset)를 구현했다. 경로 접두어는 명세를 따라 `/api/v1` 없이 쓰기로 정했다.

음성 메모 업로드와 확정 경로 2개 및 저장 로직을 추가했다. 실제 작업 접점 구현이 없어 업로드는 아직 C404이며, 실제 모델과 DB 및 프론트 통합 검증은 남아 있다.

| 항목 | 명세 | 현재 구현 |
| --- | --- | --- |
| ID 형식 | `usr_minji`처럼 읽기 쉬운 예시 ID | `usr_12`처럼 접두어에 숫자 PK를 붙인 문자열. 클라이언트는 ID를 불투명한 문자열로 다룬다 |
| `/users/me`의 `owner`, `caretaker` | 프로필 요약 | 프로필 도메인 구현 전까지 `null` |
| `nextStep`의 `OWNER_HOME`, `CARE_TODAY` | 프로필 등록 후 반환 | 프로필 도메인 구현 전까지 `PROFILE_OWNER`, `PROFILE_CARE`까지만 반환 |
| 4.4 Harvest | 텃밭 기준으로 동작 | 텃밭 도메인 구현 전까지 `/gardens/{gardenId}/harvest`, `visit-slots`, `outings`, `POST /visits`는 `C404` |
| 방문 슬롯 ID | `slot_1128_10` | `slot_20261128_10` (연도 포함) |
| 나들이 추천 | 체험농장, 식당 | 공공데이터에서 고른 처인구의 실제 장소 7곳을 시드로 넣는다 ([public-data.md](public-data.md)) |
| 푸시 | 알림과 함께 기기 푸시 발송 | 프론트가 웹이라 기기 푸시는 없다. 알림은 저장만 하고 화면이 `GET /notifications`를 다시 조회해 확인한다. `POST /users/me/push-tokens`는 삭제했다 |
| 4.8 시연 | 날씨, 작업 재생성, 빨리 감기, 초기화 | 날씨 덮어쓰기와 초기화(날씨만) 동작. `POST /demo/fast-forward` 없음 |
| 그 외 API | 4.2, 4.3, 음성 메모 2개를 제외한 4.5, `/internal/weather/sync`를 뺀 4.7, `/demo/fast-forward` | 미구현 (경로별 권한만 매핑됨) |
