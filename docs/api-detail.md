# 대신텃밭 API 상세 명세서

후속 API 추가와 기존 필드 확장: [ai-completion.md](ai-completion.md)에 작물 지침 조회와 교체, 출처를 포함한 일별 기온 입력, familySize와 sentHearts, 예약 기본 인원을 정의했다. 내부 API는 X-Service-Key를 요구한다. 기존 요청에서 가족 수와 예약 인원을 생략하는 동작도 유지한다.

> 엔드포인트별 요청, 응답, 에러를 정리한 문서다. 전체 목록과 공통 규칙, 핵심 로직은 [api-spec.md](api-spec.md)를 본다.
>
> 필드는 Notion "API 명세서 (초안)"과 [api-spec.md](api-spec.md)를 기준으로 작성한 초안이다. 구현하면서 바뀌면 이 문서를 같이 고친다.
>
> 마지막 갱신: 2026-11-11

## 목차

1. [공통](#1-공통)
2. [User](#2-user)
3. [Farm, Caretaker](#3-farm-caretaker)
4. [Home, Diary, Game](#4-home-diary-game)
5. [Harvest](#5-harvest)
6. [Task](#6-task)
7. [Notification](#7-notification)
8. [내부 배치](#8-내부-배치)
9. [시연](#9-시연)
10. [에러 코드](#10-에러-코드)

## 1. 공통

### 1.1 요청 헤더

| 헤더 | 대상 | 값 |
| --- | --- | --- |
| `Authorization` | 로그인 필요한 API | `Bearer {accessToken}` |
| `Content-Type` | 본문이 있는 요청 | `application/json` 또는 `multipart/form-data` |
| `X-Service-Key` | `/internal/*` | 서버 환경변수 `INTERNAL_SERVICE_KEY` 값 |
| `X-Demo-Mode` | `/demo/*` | `true` (ADMIN 계정의 `Authorization`도 함께 필요) |

### 1.2 응답 형식

모든 응답은 `GlobalResponse<T>`로 감싼다. 아래 각 API의 응답 예시는 `data` 안의 값만 적는다.

```json
// 성공
{ "data": { }, "error": { "code": null, "message": null } }

// 실패
{ "data": null, "error": { "code": "C4041", "message": "사용자를 찾을 수 없습니다." } }
```

### 1.3 페이지네이션

목록 API는 커서 방식이다.

| 쿼리 | 타입 | 기본값 | 설명 |
| --- | --- | --- | --- |
| `cursor` | String | 없음 | 이전 응답의 `nextCursor`. 첫 페이지는 생략 |
| `limit` | Integer | 20 | 1~50 |

```json
{ "items": [ ], "nextCursor": "cl_1103_01" }
```

`nextCursor`가 `null`이면 마지막 페이지다.

### 1.4 형식 규칙

1. 날짜는 `YYYY-MM-DD`, 시각은 ISO 8601 `+09:00` (예: `2026-11-10T09:30:00+09:00`)
2. ID는 접두어가 붙은 문자열이다. 이 문서의 `usr_minji`, `gdn_b14` 같은 값은 읽기 쉽게 쓴 예시이고, 실제 값은 `usr_12`, `gdn_3`처럼 접두어에 숫자가 붙는다. 클라이언트는 ID를 그대로 저장했다가 돌려주기만 한다
3. enum 값은 대문자 문자열로 주고받는다 (단, `character.animation`은 소문자)
4. 금액은 원 단위 정수다. 월 이용료와 관리사 수당은 가정치다

## 2. User

### 2.1 회원가입

`POST /auth/signup` | 권한 GUEST | 화면 DT_SIGNUP

| 필드 | 타입 | 필수 | 검증 |
| --- | --- | --- | --- |
| `name` | String | O | 1~20자 |
| `username` | String | O | 영문 소문자, 숫자 4~20자 |
| `password` | String | O | 8~64자 |

```json
// 요청
{ "name": "김민지", "username": "minji", "password": "password1234" }

// 응답
{
  "userId": "usr_minji",
  "accessToken": "eyJ...",
  "refreshToken": "eyJ...",
  "expiresIn": 3600,
  "role": "USER",
  "nextStep": "ROLE_SELECT"
}
```

에러: `C002` 검증 실패, `C4092` 아이디 중복

### 2.2 아이디 중복 확인

`GET /auth/username-check?username=minji` | 권한 GUEST | 화면 DT_SIGNUP

```json
{ "username": "minji", "available": false }
```

### 2.3 로그인

`POST /auth/login` | 권한 GUEST | 화면 DT_LOGIN

```json
// 요청
{ "username": "minji", "password": "password1234" }

// 응답
{
  "userId": "usr_minji",
  "accessToken": "eyJ...",
  "refreshToken": "eyJ...",
  "expiresIn": 3600,
  "role": "OWNER",
  "nextStep": "OWNER_HOME"
}
```

`nextStep` 결정 규칙

| 상태 | nextStep |
| --- | --- |
| 역할 미선택 (`USER`) | `ROLE_SELECT` |
| `OWNER`, 프로필 없음 | `PROFILE_OWNER` |
| `CARETAKER`, 프로필 없음 | `PROFILE_CARE` |
| `OWNER`, 프로필 있음 | `OWNER_HOME` |
| `CARETAKER`, 프로필 있음 | `CARE_TODAY` |

에러: `C4041` 아이디 없음, `C4012` 비밀번호 불일치

### 2.4 토큰 재발급

`POST /auth/refresh` | 권한 GUEST | 화면 공통

```json
// 요청
{ "refreshToken": "eyJ..." }

// 응답
{ "accessToken": "eyJ...", "refreshToken": "eyJ...", "expiresIn": 3600 }
```

Refresh Token도 새로 발급하고 이전 토큰은 무효가 된다. 에러: `C4013`

### 2.5 로그아웃

`POST /auth/logout` | 권한 USER, OWNER, CARETAKER | 화면 DT_MY

본문 없음. 응답 `data`는 `null`. Access Token과 Refresh Token을 모두 무효로 만든다.

### 2.6 내 정보

`GET /users/me` | 권한 USER, OWNER, CARETAKER | 화면 DT_LOGIN, DT_HOME, DT_CARE_TODAY, DT_MY

```json
{
  "userId": "usr_minji",
  "name": "김민지",
  "username": "minji",
  "role": "OWNER",
  "nextStep": "OWNER_HOME",
  "owner": { "nickname": "민지네", "district": "SUJI", "gardenId": "gdn_b14" },
  "caretaker": null
}
```

관리사는 `owner`가 `null`이고 `caretaker`에 `{ "caretakerId": "crt_soonja", "name": "박순자", "farmId": "farm_theme", "avatarUrl": "..." }`가 들어간다.

### 2.7 역할 선택 및 변경

`PUT /users/me/role` | 권한 USER, OWNER, CARETAKER | 화면 DT_ROLE, DT_MY

```json
// 요청
{ "role": "OWNER" }

// 응답
{ "role": "OWNER", "accessToken": "eyJ...", "refreshToken": "eyJ...", "nextStep": "PROFILE_OWNER" }
```

역할이 토큰에 들어가므로 새 토큰을 내려준다. 에러: `C002` 허용되지 않는 역할

## 3. Farm, Caretaker

### 3.1 주말농장 목록

`GET /farms?district=CHEOIN&cursor=&limit=20` | 권한 USER, OWNER, CARETAKER | 화면 DT_PROFILE_OWNER, DT_PROFILE_CARE

```json
{
  "items": [
    {
      "farmId": "farm_theme",
      "name": "처인 테마 주말농장",
      "district": "CHEOIN",
      "address": "경기도 용인시 처인구 ...",
      "latitude": 37.2342,
      "longitude": 127.2017,
      "distanceKm": 18.4
    }
  ],
  "nextCursor": null
}
```

데이터 출처: 공공데이터포털 주말농장 정보 (`/internal/farms/sync`로 동기화)

### 3.2 칸 미리보기

`GET /farms/{farmId}/plots/{plotCode}` | 권한 USER, OWNER | 화면 DT_PROFILE_OWNER

```json
{
  "farmId": "farm_theme",
  "plotCode": "B-14",
  "available": true,
  "crop": { "cropId": "crop_cherry_tomato", "name": "방울토마토", "characterName": "토순이" },
  "caretaker": { "caretakerId": "crt_soonja", "name": "박순자", "avatarUrl": "..." },
  "monthlyFee": 30000
}
```

에러: `C404` 농장 또는 칸 없음, `C4093` 이미 분양된 칸

### 3.3 작물 목록

`GET /crops` | 권한 USER, OWNER, CARETAKER | 화면 DT_PROFILE_OWNER

```json
[
  {
    "cropId": "crop_cherry_tomato",
    "name": "방울토마토",
    "characterName": "토순이",
    "baseTemp": 10.0,
    "stages": [
      { "stage": "SEED", "level": 1, "requiredGdd": 0 },
      { "stage": "SPROUT", "level": 2, "requiredGdd": 150 },
      { "stage": "FLOWER", "level": 3, "requiredGdd": 550 },
      { "stage": "FRUIT", "level": 4, "requiredGdd": 1000 }
    ]
  }
]
```

데이터 출처: 농사로 작물 생육 정보. `requiredGdd` 값은 예시다.

### 3.4 주인 프로필 등록

`POST /owners/me/profile` | 권한 USER, OWNER | 화면 DT_PROFILE_OWNER

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `nickname` | String | O | 1~20자 |
| `district` | String | O | `SUJI`, `GIHEUNG` |
| `farmId` | String | O | |
| `plotCode` | String | O | |

```json
// 요청
{ "nickname": "민지네", "district": "SUJI", "farmId": "farm_theme", "plotCode": "B-14" }

// 응답
{ "gardenId": "gdn_b14", "characterName": "토순이", "stage": "SEED", "nextStep": "OWNER_HOME" }
```

칸 분양과 텃밭(Garden) 생성을 함께 처리한다. 에러: `C4093` 이미 분양된 칸, `C4094` 프로필 이미 있음

### 3.5 주인 프로필 수정

`PATCH /owners/me/profile` | 권한 OWNER | 화면 DT_MY

```json
// 요청 (바꿀 필드만)
{ "nickname": "민지와 아이들" }

// 응답
{ "nickname": "민지와 아이들", "district": "SUJI", "gardenId": "gdn_b14" }
```

### 3.6 관리사 프로필 등록

`POST /caretakers/me/profile` | 권한 USER, CARETAKER | 화면 DT_PROFILE_CARE

```json
// 요청
{ "name": "박순자", "farmId": "farm_theme" }

// 응답
{ "caretakerId": "crt_soonja", "name": "박순자", "farmId": "farm_theme", "nextStep": "CARE_TODAY" }
```

### 3.7 AI 아바타 생성 요청

`POST /caretakers/me/avatar` | 권한 CARETAKER | 화면 DT_PROFILE_CARE, DT_MY | `multipart/form-data`

| 파트 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `photo` | File | O | jpg, png 10MB 이하 |
| `portraitConsent` | Boolean | O | 초상권 동의, `true`여야 함 |

```json
{ "avatarJobId": "avj_soonja_01", "status": "PENDING" }
```

생성은 비동기로 진행한다. 에러: `C4003` 초상권 미동의, `C4001` 파일 형식 또는 크기 초과

### 3.8 AI 아바타 결과 조회

`GET /caretakers/me/avatar` | 권한 CARETAKER | 화면 DT_PROFILE_CARE, DT_MY

```json
{ "avatarJobId": "avj_soonja_01", "status": "DONE", "avatarUrl": "https://.../crt_soonja.png" }
```

`status`: `PENDING`, `DONE`, `FAILED`. 클라이언트는 2초 간격으로 폴링한다. 실패하면 기본 아바타를 쓴다.

### 3.9 이번 달 활동 요약

`GET /caretakers/me/summary?month=2026-11` | 권한 CARETAKER | 화면 DT_CARE_DONE, DT_MY

```json
{
  "month": "2026-11",
  "completedTasks": 32,
  "gardenCount": 3,
  "workDays": 9,
  "heartsReceived": 18,
  "expectedAllowance": 240000,
  "allowanceNote": "가정치예요. 실제 수당과 다를 수 있어요."
}
```

`month`를 생략하면 이번 달이다.

### 3.10 읽어주기 음성

`POST /tts` | 권한 USER, CARETAKER | 화면 DT_PROFILE_CARE, DT_CARE_TODAY

```json
// 요청
{ "text": "오늘은 B-14 텃밭에 지지대를 세워 주세요.", "speed": 0.9 }
```

응답은 `GlobalResponse`가 아니라 `audio/mpeg` 바이너리다. `text`는 300자 이하, `speed`는 0.5~1.5 (기본 0.9). 같은 문장은 캐시한다.

## 4. Home, Diary, Game

### 4.1 홈 조회

`GET /gardens/{gardenId}/home` | 권한 OWNER | 화면 DT_HOME

```json
{
  "gardenId": "gdn_b14",
  "character": {
    "name": "토순이",
    "stage": "FLOWER",
    "level": 3,
    "mood": "HAPPY",
    "animation": "sunny_happy",
    "message": "오늘 햇살이 좋아서 기분이 좋아요"
  },
  "weather": { "condition": "SUNNY", "temperature": 14.2, "rainProbability": 10, "updatedAt": "2026-11-10T09:00:00+09:00" },
  "streak": { "days": 12, "cheeredToday": false },
  "xp": { "total": 1240, "today": 0 },
  "harvest": { "expectedDate": "2026-11-28", "dDay": 18 },
  "caretaker": { "caretakerId": "crt_soonja", "name": "박순자", "avatarUrl": "..." },
  "recentCareLog": {
    "careLogId": "cl_1110_01",
    "summary": "지지대를 세우고 곁순을 정리했어요",
    "photoUrl": "...",
    "createdAt": "2026-11-10T10:20:00+09:00",
    "hearted": false
  },
  "hasUnreadEvent": false
}
```

에러: `C404` 텃밭 없음, `C403` 내 텃밭 아님 (이 장의 텃밭 API 공통)

### 4.2 성장 시뮬레이션 상세

`GET /gardens/{gardenId}/growth` | 권한 OWNER | 화면 DT_HOME, DT_HARVEST

```json
{
  "stage": "FLOWER",
  "level": 3,
  "accumulatedGdd": 812.4,
  "nextStage": "FRUIT",
  "nextStageGdd": 1000,
  "progress": 0.75,
  "expectedHarvestDate": "2026-11-28",
  "calibratedAt": "2026-11-08T11:00:00+09:00",
  "explanation": "요즘 따뜻해서 꽃이 잘 피고 있어요. 이대로면 11월 말에 열매를 딸 수 있어요.",
  "dailyGdd": [ { "date": "2026-11-09", "avgTemp": 15.1, "gdd": 5.1 } ]
}
```

계산 방식은 [api-spec.md 5장 AI 핵심 2](api-spec.md#ai-핵심-2-성장-시뮬레이션)를 본다. `dailyGdd`는 최근 14일이다.

### 4.3 오늘의 응원

`POST /gardens/{gardenId}/cheers` | 권한 OWNER | 화면 DT_HOME

본문 없음.

```json
{ "streakDays": 13, "xpGained": 10, "totalXp": 1250, "mood": "HAPPY" }
```

하루 1회. 에러: `C4095` 오늘 이미 응원함

### 4.4 감사 하트 보내기

`POST /care-logs/{careLogId}/hearts` | 권한 OWNER | 화면 DT_HOME, DT_DIARY

본문 없음.

```json
{ "careLogId": "cl_1110_01", "hearted": true, "xpGained": 5 }
```

돌봄 기록당 1회. 관리사에게 알림을 보낸다. 에러: `C4095` 이미 보냄

### 4.5 성장 일기

`GET /gardens/{gardenId}/diary?filter=ALL&cursor=&limit=20` | 권한 OWNER | 화면 DT_DIARY

`filter`: `ALL`, `PHOTO`, `STAGE`. 본인 소유 텃밭만 조회한다. 최신 저장 순으로 반환하며 `limit`은 1~50이다. `nextCursor`는 `dry_숫자` 형식의 불투명 커서다. 날짜 대신 응답 커서를 그대로 보낸다.

```json
{
  "items": [
    {
      "diaryId": "dry_12",
      "type": "CARE_LOG",
      "date": "2026-11-10",
      "careLogId": "cl_8",
      "eventId": null,
      "taskTypes": ["CHECK"],
      "transcript": "잎과 흙을 살펴봤어요. 잎에 작은 점이 보였어요.",
      "summary": "잎과 흙을 살폈어요. 잎의 작은 점은 다시 확인할게요.",
      "photoUrl": "...",
      "caretakerName": "박순자",
      "hearted": false,
      "fromStage": null,
      "toStage": null,
      "createdAt": "2026-11-10T10:21:00+09:00"
    },
    {
      "diaryId": "dry_11",
      "type": "STAGE_CHANGE",
      "date": "2026-11-05",
      "careLogId": null,
      "eventId": "evt_3",
      "taskTypes": [],
      "transcript": null,
      "summary": "작물이 다음 단계로 자랐어요.",
      "photoUrl": null,
      "caretakerName": null,
      "hearted": false,
      "fromStage": "SPROUT",
      "toStage": "FLOWER",
      "createdAt": "2026-11-05T09:00:00+09:00"
    }
  ],
  "nextCursor": "dry_11"
}
```

돌봄 완료와 단계 상승 시 원래 저장 트랜잭션 안에서 일기 스냅샷을 추가한다. `transcript`는 확정 메모의 STT 원문이며 없으면 `null`이다. `summary`는 확정 정리문 또는 작업 완료 문구다. `PHOTO`는 사진이 있는 항목, `STAGE`는 성장 항목만 반환한다. `hearted`는 연결된 돌봄 기록의 현재 하트 수를 반영한다. 하트 전송 API, REST_DAY 생성, 과거 기록의 일괄 이관은 아직 구현하지 않았다. 새 테이블 준비는 `db/manual/diary.sql`을 확인한다.

### 4.6 미확인 진화 이벤트

`GET /gardens/{gardenId}/events` | 권한 OWNER | 화면 DT_HOME, DT_EVOLVE

```json
[
  {
    "eventId": "evt_fruit_b14",
    "fromStage": "FLOWER",
    "toStage": "FRUIT",
    "occurredAt": "2026-11-28T06:00:00+09:00",
    "reward": { "xp": 100, "badgeCode": "FIRST_FRUIT" }
  }
]
```

### 4.7 진화 이벤트 확인

`POST /events/{eventId}/ack` | 권한 OWNER | 화면 DT_EVOLVE

```json
{ "eventId": "evt_fruit_b14", "xpGained": 100, "totalXp": 1350, "badge": { "code": "FIRST_FRUIT", "name": "첫 열매" } }
```

보상은 한 번만 지급한다. 에러: `C4095` 이미 확인함

### 4.8 주간 리그

`GET /leagues/current` | 권한 OWNER | 화면 DT_LEAGUE, DT_MY

```json
{
  "leagueId": "lg_2026w46_cheoin",
  "weekStart": "2026-11-09",
  "weekEnd": "2026-11-15",
  "myRank": 3,
  "members": [
    { "rank": 1, "gardenId": "gdn_a03", "nickname": "하준이네", "weeklyXp": 320, "me": false },
    { "rank": 3, "gardenId": "gdn_b14", "nickname": "민지네", "weeklyXp": 210, "me": true }
  ]
}
```

같은 농장 텃밭끼리 한 리그다.

### 4.9 주간 퀘스트

`GET /quests/weekly` | 권한 OWNER | 화면 DT_LEAGUE, DT_MY

```json
[
  { "questId": "qst_w46_cheer5", "title": "응원 5번 하기", "progress": 3, "goal": 5, "rewardXp": 50, "status": "IN_PROGRESS" }
]
```

`status`: `IN_PROGRESS`, `COMPLETED`, `CLAIMED`

### 4.10 퀘스트 보상 받기

`POST /quests/{questId}/claim` | 권한 OWNER | 화면 DT_LEAGUE

```json
{ "questId": "qst_w46_cheer5", "xpGained": 50, "totalXp": 1300, "status": "CLAIMED" }
```

에러: `C4004` 아직 완료 안 됨, `C4095` 이미 받음

### 4.11 내 배지

`GET /users/me/badges` | 권한 OWNER | 화면 DT_MY

```json
[
  { "code": "FIRST_FRUIT", "name": "첫 열매", "description": "작물이 처음 열매를 맺었어요", "acquiredAt": "2026-11-28T06:10:00+09:00" }
]
```

### 4.12 [확장] 오늘의 텃밭 퀴즈

`GET /gardens/{gardenId}/quizzes/today` | 권한 OWNER | 화면 DT_HOME

```json
{
  "quizId": "qz_1110_b14",
  "question": "토마토 곁순은 왜 따 줄까요?",
  "choices": ["열매에 영양을 모으려고", "물을 덜 주려고", "벌레를 부르려고"],
  "answered": false
}
```

### 4.13 [확장] 퀴즈 답 제출

`POST /quizzes/{quizId}/answers` | 권한 OWNER | 화면 DT_HOME

```json
// 요청
{ "choiceIndex": 0 }

// 응답
{ "correct": true, "explanation": "곁순을 따면 열매가 더 커져요.", "xpGained": 10 }
```

## 5. Harvest

### 5.1 수확 안내

`GET /gardens/{gardenId}/harvest` | 권한 OWNER | 화면 DT_HARVEST

```json
{
  "expectedDate": "2026-11-28",
  "dDay": 18,
  "harvestable": false,
  "daysCaredTogether": 46,
  "careLogCount": 21,
  "caretakerName": "박순자",
  "message": "11월 28일쯤 토순이를 만나러 오세요"
}
```

### 5.2 방문 가능 시간

`GET /gardens/{gardenId}/visit-slots?from=2026-11-28&to=2026-11-29` | 권한 OWNER | 화면 DT_HARVEST

```json
[
  { "slotId": "slot_20261128_10", "date": "2026-11-28", "startTime": "10:00", "endTime": "11:00", "available": true },
  { "slotId": "slot_20261128_14", "date": "2026-11-28", "startTime": "14:00", "endTime": "15:00", "available": false }
]
```

1. 주말 슬롯만 내려준다. 하루에 10시, 11시, 14시, 15시에 시작하는 1시간 슬롯 네 개다.
2. `from`, `to`를 생략하면 내일부터 2주다. 한 번에 31일까지 조회할 수 있다.
3. `available`은 정원(한 농장의 한 슬롯에 2건)이 찼거나 오늘 또는 지난 날짜면 `false`다.
4. `slotId`는 `slot_{yyyyMMdd}_{HH}` 형식이다.

에러: `C002` 날짜 형식 오류, `to`가 `from`보다 앞, 31일 초과

### 5.3 방문 예약

`POST /visits` | 권한 OWNER | 화면 DT_HARVEST

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `gardenId` | String | O | |
| `slotId` | String | O | |
| `headcount` | Integer | X | 방문 인원, 1 이상. 생략하면 1명으로 저장한다 |
| `memo` | String | X | 200자 이하 |

```json
// 요청 (인원 입력 화면이 없으면 headcount 생략)
{ "gardenId": "gdn_b14", "slotId": "slot_20261128_10", "headcount": 3, "memo": "아이 둘이랑 가요" }

// 응답
{ "visitId": "vst_1128_b14", "date": "2026-11-28", "startTime": "10:00", "status": "RESERVED" }
```

같은 농장과 날짜의 예약 처리를 잠금으로 직렬화하고 현재 예약을 잠금 조회해 정원을 다시 검사한다. 관리사에게 알림을 보낸다. 내일 이후의 슬롯만 예약할 수 있다. 에러: `C4096` 이미 찬 시간, `C4095` 그 텃밭에 같은 날 예약 있음, `C002` 없는 슬롯이거나 오늘 또는 지난 날짜

### 5.4 방문 예약 취소

`DELETE /visits/{visitId}` | 권한 OWNER | 화면 DT_HARVEST

응답 `data`는 `null`. 방문 하루 전까지만 가능하다. 관리사에게 알림을 보낸다. 에러: `C4005` 취소 기한 지남 (방문 당일부터), `C403` 내 예약 아님, `C404` 예약 없음, `C4095` 이미 취소함

### 5.5 처인 나들이 추천

`GET /gardens/{gardenId}/outings` | 권한 OWNER | 화면 DT_HARVEST

```json
[
  { "type": "EXPERIENCE_FARM", "name": "OO 딸기 체험농장", "distanceKm": 3.2, "address": "...", "description": "아이와 딸기 따기 체험" },
  { "type": "RESTAURANT", "name": "OO 손두부", "distanceKm": 1.8, "address": "...", "description": "농장 근처 두부 맛집" }
]
```

`type`: `EXPERIENCE_FARM`, `RESTAURANT`. 농장에서 가까운 순으로 최대 6곳이다. `distanceKm`는 직선 거리이고 농장 위치를 모르면 `null`이다.

## 6. Task

### 6.1 오늘 할 일

`GET /caretaker/tasks/today` | 권한 CARETAKER | 화면 DT_CARE_TODAY

```json
{
  "date": "2026-11-10",
  "weatherNotice": "오후에 비가 와요. 물은 안 주셔도 돼요.",
  "skippedTypes": ["WATER"],
  "totalMinutes": 75,
  "gardens": [
    {
      "gardenId": "gdn_b14",
      "plotCode": "B-14",
      "cropName": "방울토마토",
      "ownerNickname": "민지네",
      "tasks": [
        { "taskId": "tsk_1110_b14", "type": "SUPPORT", "title": "지지대 세우기", "description": "줄기가 기울었어요. 지지대에 묶어 주세요.", "minutes": 15, "status": "TODO" }
      ],
      "stageCheck": { "required": true, "expectedStage": "FRUIT" }
    }
  ]
}
```

작업 `status`: `TODO`, `DONE`. 문구는 짧고 쉬운 말로 쓴다 (읽어주기용).

### 6.2 작업 인증 사진

`POST /caretaker/tasks/{taskId}/photos` | 권한 CARETAKER | 화면 DT_CARE_PHOTO | `multipart/form-data`

| 파트 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `photo` | File | O | jpg, png 10MB 이하 |

```json
{
  "photoId": "pht_1110_b14",
  "photoUrl": "...",
  "aiCheck": { "matched": true, "message": "지지대가 잘 보여요" }
}
```

AI 사진 확인이 실패해도 사진은 저장하고 `aiCheck`는 `null`로 준다.

### 6.3 말로 남기기

`POST /caretaker/tasks/{taskId}/voice-memos` | 권한 CARETAKER | 화면 DT_CARE_TODAY, DT_CARE_VOICE | `multipart/form-data`

| 파트 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `audio` | File | O | webm, m4a, mp4, wav 60초 이하. 브라우저 녹음(webm, 사파리 mp4)은 서버가 길이를 읽지 못할 수 있어 5MB 이하로만 확인하므로, 녹음은 프론트에서 60초에 멈춘다 |

```json
{
  "voiceMemoId": "vm_1110_b14",
  "transcript": "오늘 토마토 꽃이 많이 폈고 잎에 점이 좀 있어",
  "summary": "꽃이 많이 피었어요. 잎에 작은 점이 보여서 지켜볼게요.",
  "issues": ["LEAF_SPOT"],
  "status": "DRAFT"
}
```

STT로 받아 적고 LLM이 가족용 문장으로 정리한다. `issues`는 다음 날 작업 생성에 쓰인다.

현재 작업 조회는 구현했으며 실제 텃밭 조회 어댑터 연결이 필요하다. 코드별 한글 문구와 상태 필드는 [voice-api.md](voice-api.md)를 본다. 원문은 요약과 분리해 저장하고, 확정된 메모만 후속 작업에 사용한다. 필수 `audio` 파트 누락은 C002, 담당 작업이 아니면 C403, 완료된 작업이면 C4095다.

초안 응답에는 `processingStatus`, `retryable`, `processingError`가 추가된다. 정상 결과는 READY다. 인식 실패 시 파일을 보관하고 `STT_FAILED`, 원문과 요약 null, `retryable=true`로 반환한다. 정리만 실패하면 `LLM_FAILED`와 임시 요약을 제공한다. 이 경우 HTTP 응답은 성공이므로 화면은 처리 상태도 검사해야 한다. `POST /caretaker/voice-memos/{voiceMemoId}/retry`로 같은 초안을 재시도한다. 형식 및 길이가 잘못된 INVALID_AUDIO는 다시 녹음해야 한다.

### 6.4 음성 메모 확정

연결 중단으로 업로드 응답을 받지 못했다면 먼저 `GET /caretaker/tasks/{taskId}/voice-memos/latest`를 호출한다. 마지막 저장 메모 하나와 업로드 응답과 같은 처리 상태를 반환한다. 본인 담당 작업만 조회 가능하며 저장된 메모가 없으면 C404다. PROCESSING 상태의 처리 시작 후 유효 시간(기본 2분, 읽기 제한 90초일 때 3분 30초)이 지나면 `retryable=true`로 표시되어 같은 메모 ID로 retry할 수 있다. 조회는 새 파일이나 메모를 만들지 않는다. 복구 순서와 중복 녹음 시 주의점은 [voice-api.md](voice-api.md)를 본다.

`POST /caretaker/voice-memos/{voiceMemoId}/confirm` | 권한 CARETAKER | 화면 DT_CARE_VOICE

```json
// 요청 (고친 문장이 있을 때만 summary 포함)
{ "summary": "꽃이 많이 피었어요. 잎에 작은 점이 보여요." }

// 응답
{ "voiceMemoId": "vm_1110_b14", "status": "CONFIRMED" }
```

본문을 생략하거나 빈 객체를 보내도 원래 요약으로 확정한다. 같은 확정 요청 재시도는 허용하고, 확정한 메모를 다른 문장으로 다시 바꾸면 C4095다. 초안 요약을 실제 수정하면 원문은 유지하고 이전 자동 추출 코드는 비운다. 상세 구현 및 남은 연결은 [voice-api.md](voice-api.md)를 본다.

### 6.5 작업 완료

`POST /caretaker/tasks/{taskId}/complete` | 권한 CARETAKER | 화면 DT_CARE_TODAY, DT_CARE_PHOTO, DT_CARE_DONE

```json
// 요청 (모두 선택)
{ "photoId": "pht_1110_b14", "voiceMemoId": "vm_1110_b14" }

// 응답
{
  "taskId": "tsk_1110_b14",
  "status": "DONE",
  "careLogId": "cl_1110_01",
  "remainingTasks": 2,
  "stageCheck": { "required": true, "gardenId": "gdn_b14" }
}
```

돌봄 기록을 만들고 가족에게 알린다. 에러: `C403` 담당 작업 아님, `C4095` 이미 완료함

### 6.6 성장 단계 확인

`POST /gardens/{gardenId}/stage-checks` | 권한 CARETAKER | 화면 DT_CARE_DONE

```json
// 요청
{ "observed": "FRUIT", "photoId": "pht_1110_b14" }

// 응답
{ "stage": "FRUIT", "calibrated": true, "calibratedAt": "2026-11-10T10:30:00+09:00", "eventId": "evt_fruit_b14" }
```

`observed`: `FLOWER`, `FRUIT`, `NO_CHANGE`. 단계가 오르면 `eventId`가 생기고, 그대로면 `null`이다.

## 7. Notification

### 7.1 알림 목록

`GET /notifications?cursor=&limit=20` | 권한 OWNER, CARETAKER | 화면 DT_HOME, DT_CARE_DONE, DT_MY

```json
{
  "items": [
    {
      "notificationId": "ntf_1110_01",
      "type": "CARE_DONE",
      "title": "순자 할머니가 토순이를 돌봤어요",
      "body": "지지대를 세우고 곁순을 정리했어요",
      "target": { "screen": "DT_DIARY", "id": "cl_1110_01" },
      "read": false,
      "createdAt": "2026-11-10T10:21:00+09:00"
    }
  ],
  "unreadCount": 3,
  "nextCursor": null
}
```

`type`: `CARE_DONE`, `HEART`, `STAGE_UP`, `VISIT_RESERVED`, `VISIT_CANCELED`, `STREAK_RISK`, `QUEST_DONE`

1. 최신순이다. `unreadCount`는 페이지와 상관없이 안 읽은 알림 전체 수다.
2. `target`은 알림을 눌렀을 때 이동할 화면 코드와 그 화면에서 쓸 ID다. 이동 대상이 없는 알림은 `null`이다.
3. 기기 푸시는 없다. 새 알림과 `unreadCount`는 화면에 들어올 때와 일정 간격으로 이 API를 다시 조회해서 확인한다.

### 7.2 알림 읽음

`PATCH /notifications/{notificationId}/read` | 권한 OWNER, CARETAKER | 화면 DT_HOME, DT_CARE_DONE, DT_MY

`notificationId` 자리에 `all`을 넣으면 모두 읽음 처리한다.

```json
{ "readCount": 3, "unreadCount": 0 }
```

`readCount`는 이번 요청으로 읽음 처리된 수다. 이미 읽은 알림이면 0이고 에러는 아니다. 에러: `C404` 알림 없음 또는 내 알림 아님, `C002` ID 형식 오류

## 8. 내부 배치

모두 `X-Service-Key` 헤더가 필요하고 스케줄러가 같은 로직을 주기대로 실행한다. 수동 실행용으로 아래 요청 본문을 받는다.

| Endpoint | 주기 | 요청 (모두 선택) | 응답 |
| --- | --- | --- | --- |
| `POST /internal/weather/sync` | 1시간마다 (매시 15분) | `{ "farmIds": ["farm_theme"] }` (본문 생략 가능) | `{ "syncedFarms": 1, "failedFarms": [] }` |
| `POST /internal/ai/daily-tasks` | 매일 05:00 | `{ "date": "2026-11-10", "gardenIds": [] }` | `{ "generated": 3, "fallback": 0 }` |
| `POST /internal/ai/growth-simulation` | 매일 06:00 | `{ "date": "2026-11-10" }` | `{ "updated": 3, "stageChanged": 1 }` |
| `POST /internal/streaks/check` | 매일 00:05 | `{ "date": "2026-11-09" }` | `{ "broken": 2 }` |
| `POST /internal/leagues/settle` | 매주 월 00:10 | `{ "weekStart": "2026-11-02" }` | `{ "settledLeagues": 4, "createdQuests": 12 }` |
| `POST /internal/farms/sync` | 매주 월 03:00 | 없음 | `{ "created": 2, "updated": 10 }` |

1. `daily-tasks`의 `fallback`은 LLM 실패로 규칙 기반 작업을 쓴 텃밭 수다.
2. 대상을 비워 두면 전체를 처리한다.
3. 에러: `C4014` 서비스 키 불일치

## 9. 시연

모두 ADMIN 계정의 `Authorization` 토큰과 `X-Demo-Mode: true` 헤더가 필요하다. 토큰이 없으면 `C4011`, ADMIN이 아니면 `C403`, 헤더가 없으면 `C4032`다. 대상은 시연용 텃밭 `gdn_b14`와 관리사 `crt_soonja`다.

### 9.1 데모 상태

`GET /demo/state`

```json
{ "weather": "SUNNY", "stage": "FLOWER", "stageProgress": "3/4", "fastForwardDays": 18, "todayTaskGardens": 3 }
```

현재 구현: weather와 시연 텃밭의 stage, stageProgress(단계 순서), todayTaskGardens를 반환한다. fastForwardDays는 미구현으로 null이다. 시연 텃밭이 없으면 텃밭 상태는 null이다.

### 9.2 날씨 바꾸기

`PUT /demo/weather`

```json
// 요청
{ "condition": "RAIN" }

// 응답
{ "condition": "RAIN", "animation": "rain_umbrella", "weatherNotice": "오후에 비가 와요. 물은 안 주셔도 돼요.", "regeneratedTasks": 3 }
```

`condition`: `SUNNY`, `RAIN`, `HOT` (그 외는 `C002`). 작업을 즉시 다시 만든다.

현재 구현: 시연 농장 B-14의 minji 텃밭에 기존 작업 생성과 날씨 검증을 연결한다. 완료 기록과 첨부는 보존한다. weatherNotice는 현재 작업 안내, regeneratedTasks는 작업이 새로 추가된 텃밭 수다. 이미 충분한 작업이 있으면 0이다.

### 9.3 시간 빨리 감기

`POST /demo/fast-forward`

```json
// 요청
{ "days": 18 }

// 응답
{ "stage": "FRUIT", "eventId": "evt_fruit_b14", "expectedHarvestDate": "2026-11-28" }
```

현재 구현: 없다 (성장 시뮬레이션과 진화 이벤트가 먼저 필요하다).

### 9.4 초기화

`POST /demo/reset`

본문 없음. 맑음, 꽃 단계, 할 일 3곳 상태로 되돌린다.

```json
{ "weather": "SUNNY", "stage": "FLOWER", "todayTaskGardens": 3 }
```

현재 구현: 날씨를 SUNNY로 되돌리고 시연 텃밭 작업을 보완한다. 응답은 9.1과 같다. 단계와 완료 기록을 초기화하거나 시드를 재적재하지 않는다.

## 10. 에러 코드

기존 코드는 [architecture.md](architecture.md#에러-코드)에 있다. 아래는 이 명세에서 새로 쓰는 코드의 초안이다. 구현할 때 `ErrorCode`에 추가한다.

| code | HTTP | 메시지 | 사용처 |
| --- | --- | --- | --- |
| C4001 | 400 | 지원하지 않는 파일입니다. | 파일 형식, 크기, 길이 초과 |
| C4003 | 400 | 초상권 동의가 필요합니다. | AI 아바타 |
| C4004 | 400 | 아직 완료되지 않았습니다. | 퀘스트 보상 |
| C4005 | 400 | 취소 기한이 지났습니다. | 방문 취소 |
| C4014 | 401 | 서비스 키가 유효하지 않습니다. | 내부 배치 |
| C4032 | 403 | 시연 모드에서만 사용할 수 있습니다. | 시연 (`X-Demo-Mode` 헤더 없음 또는 서버에서 비활성화) |
| C4092 | 409 | 이미 사용 중인 아이디입니다. | 회원가입 |
| C4093 | 409 | 이미 분양된 칸입니다. | 칸 미리보기, 주인 프로필 |
| C4094 | 409 | 이미 프로필이 있습니다. | 프로필 등록 |
| C4095 | 409 | 이미 처리된 요청입니다. | 응원, 하트, 이벤트, 퀘스트, 작업 완료 |
| C4096 | 409 | 예약이 마감된 시간입니다. | 방문 예약 |
