# AI 마무리 구현과 배포 연결

PR #32 이후 후속 변경이다. 실제 서버의 배포 주소나 인증 정보를 만들었다는 의미는 아니다.

## 작물별 작업 지침

실제 텃밭의 Crop ID로 작업 지침을 읽는다. 토마토와 방울토마토에는 조건부 물주기와 지주 점검을 기본 제공한다. [용인시 농업기술센터 토마토 안내](https://www.yongin.go.kr/home/atc/cityConsum/cityConsum05/cityConsum05_02/cityConsum05_02_04.jsp)의 수분과 지주 관리 내용을 참고했다. 10분, 5분과 최소 재편성 간격 1일, 7일은 수정 가능한 프로젝트 편성 기본값이며 해당 기관이 발표한 작업 시간이나 주기가 아니다.

물주기는 기상 정보가 있고 비 예보가 없는 경우에만 후보가 된다. 실제 작업에서도 흙이 마른지 먼저 확인한다. 기상 정보가 없으면 물주기를 제외한다. 기본 지침이 없는 작물은 CHECK 대체를 유지한다. 약제나 비료의 사용량을 자동 추천하지 않는다.

| Method | 경로 | 동작 |
| --- | --- | --- |
| GET | /internal/crops/{cropId}/task-guidance | 현재 지침. 예: crop_1 |
| PUT | /internal/crops/{cropId}/task-guidance | 전체 지침 교체. 빈 배열은 기본 지침도 끔 |

기존 X-Service-Key 인증을 사용한다. 일반 사용자 토큰만으로 호출할 수 없다. 변경은 실제 작물 행을 잠근 트랜잭션에서 저장한다. 서버가 재시작해도 저장한 빈 배열이 기본값으로 되살아나지 않는다.

PUT 본문은 rules 배열이다. 각 항목은 type, stages, minimumIntervalDays, title, description, minutes, sourceId를 가진다. sourceId는 인증 정보가 없는 공개 HTTPS 출처 페이지를 넣는다. URL을 서버에서 가져오는 기능은 없다. 작업 종류는 중복될 수 없고 최대 7개, 간격은 1일부터 365일, 시간은 1분부터 120분이다. 단계, 반복 간격, 기상 조건과 관리사 하루 120분 검증은 기존 작업 생성 과정에서 다시 수행한다.

## 과거 일별 기온 입력

POST /internal/weather/daily에 X-Service-Key를 사용한다. 본문은 nx, ny, sourceUrl, days다. days 항목은 date, avgTemp, minTemp, maxTemp다. 과거의 완료된 날짜만 받으며 같은 날짜 중복, 유한하지 않은 기온, 최저/평균/최고 역전, 366개 초과를 거부한다. 출처 페이지는 공개 HTTPS 주소를 사용한다. 응답 data.importedDays는 처리한 날짜 수다.

자료는 기상청 등 실제 출처에서 확보해 농장에 맞는 격자에 입력해야 한다. 관측소 자료를 사용하는 경우 관측소와 농장 격자의 대응도 운영자가 확인한다. 이 API는 관측 자료를 자동 다운로드하거나 실제 농장에서 측정했다고 보증하는 기능이 아니다. 실제 자료나 이용 권한 없이 과거 기온을 생성하지 않는다.

같은 격자와 날짜를 다시 보내면 원자적으로 갱신한다. 출처와 observed=true를 기록하며 이후 시간별 예보 집계가 이 일자료를 덮어쓰지 않는다. 성장 계산은 저장된 일자료를 이용하고, 누락 날짜가 있으면 기존처럼 예측을 만들어내지 않는다. 자동 기상 수집은 기존 PUBLIC_DATA_SERVICE_KEY 설정과 수집 API를 그대로 사용한다.

## 가입과 프론트 요청 반영

자동 성장 계산으로 FRUIT가 예측돼도 즉시 수확 가능으로 바꾸지 않는다. 성장 계산 자료가 있는 텃밭은 관리사의 FRUIT 확인이 있어야 수확을 허용한다. 계산 자료가 없는 기존 도메인의 수확 상태는 유지한다.

주인 프로필 등록과 수정에 familySize를 추가했다. 1명부터 20명이며 등록에서 생략하면 1명이다. 수정에서 생략하면 기존 값을 유지한다. 프로필 응답과 /users/me의 owner.familySize에 반환한다.

방문 예약에서 headcount를 생략하면 저장된 가족 수를 사용한다. 명시한 인원이 있으면 그 값이 우선한다. /users/me의 owner.sentHearts는 실제 보낸 하트 수다. 관리사 completedTasks와 받은 하트는 이전 통합 구현을 유지한다. 주인의 거주 지역 제한은 기존 SUJI, GIHEUNG을 유지하며 CHEOIN 허용 여부를 임의로 바꾸지 않는다.

주인 프로필 등록이 커밋된 뒤 첫 작업을 규칙으로 초기화한다. 이 과정에서는 LLM을 호출하지 않으며 기존 소유권, 기상과 120분 검증을 재사용한다. 담당 관리사가 아직 없으면 임의로 배정하지 않는다. 초기화 실패는 가입 성공을 되돌리지 않고 오늘 할 일 조회에서 재시도한다. 첫 일기는 기존 팀 구현의 REST_DAY로 표시하며 가짜 돌봄 기록을 생성하지 않는다.

## Linux 서버의 AI 작업자 배포 설정

compose.ai-workers.json은 기존 Linux 서버에서 AI 작업자만 시작하는 설정이다. DB, Redis, 가상 사용자나 local-demo 백엔드를 포함하지 않는다. Docker host 네트워크를 사용하고 두 AI 서버는 127.0.0.1에만 바인딩한다. 외부로 AI 포트를 공개하지 않는다.

```bash
docker compose -f compose.ai-workers.json config --quiet
docker compose -f compose.ai-workers.json up -d --build
docker compose -f compose.ai-workers.json ps -a
curl http://127.0.0.1:8765/health
curl http://127.0.0.1:11434/api/tags
```

Linux의 Docker Engine을 기준으로 준비했다. 기존 Windows 로컬 시연 설정을 바꾸거나 Docker Desktop의 격리 설정을 해제할 필요는 없다. Ollama와 STT의 메모리 상한 합은 6g이며 전체 서버 최소 사양을 측정한 수치는 아니다. 첫 실행 시 이미지와 모델 다운로드가 필요하다.

Spring도 같은 호스트 프로세스이거나 같은 host 네트워크 공간에서 실행해야 현재 loopback 클라이언트를 쓸 수 있다. 별도 bridge 컨테이너라면 자동 연결되지 않는다. backend의 DB, Redis 주소와 파일 볼륨, JWT, 프론트 CORS, HTTPS 주소는 팀 배포 방식에 맞춰 .env.prod.example을 채운다. HTTPS 인증서와 실제 배포 주소는 이 설정에서 생성하지 않는다. 운영 서버에 이 명령을 실행한 결과는 아직 없다.

## DB와 검사

운영의 ddl-auto는 validate다. 기존 변경 외에 db/manual/ai_completion.sql의 새 지침 테이블과 daily_weather, owner_profile 열을 반영해야 한다. 기존 자료를 삭제하지 않는다.

추가 검사는 지침 교체와 빈 배열 보존, 비 예보 물주기 제외, 기온 입력 검증과 관측 보호, 실제 MariaDB upsert, 가족 수 저장과 예약 기본값, 초기 작업의 모델 호출 방지다. DB 통합 검사에서 사용하는 합성 자료는 테스트 트랜잭션으로 롤백한다. 새 코드에 대한 전체 Docker 검사 결과는 기존 PR #32의 234개 통과 기록과 별도로 확인한다.
