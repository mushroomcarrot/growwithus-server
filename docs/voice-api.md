# 음성 메모 API

2026-11-12 작업 브랜치 기준. 응답은 `GlobalResponse`이며 경로 접두어는 없다.

## 요청과 응답

| Method | 경로 | 요청 | 응답 data |
| --- | --- | --- | --- |
| POST | `/caretaker/tasks/{taskId}/voice-memos` | multipart `audio` | 아래 초안 응답 |
| POST | `/caretaker/voice-memos/{voiceMemoId}/retry` | 본문 없음 | 아래 초안 응답 |
| POST | `/caretaker/voice-memos/{voiceMemoId}/confirm` | 선택 JSON `summary`, 본문 생략 가능 | `voiceMemoId`, `status=CONFIRMED` |

초안 응답 필드는 `voiceMemoId`, `transcript`, `summary`, `issues`, `status`, `processingStatus`, `retryable`, `processingError`다. `issues`는 코드 배열이다. STT 실패 시 원문과 요약은 null이며 성공으로 꾸민 문장을 넣지 않는다.

| processingStatus | 의미 | retryable |
| --- | --- | --- |
| PROCESSING | 분석 진행 중 | false, 2분이 지나면 재시도 가능 |
| READY | STT와 문장 정리 완료 | false |
| STT_FAILED | 인식 서버 실패 또는 읽기 실패 | true |
| LLM_FAILED | 원문은 있고 정리에 실패 | true |
| INVALID_AUDIO | 디코딩 결과 형식 또는 길이 오류 | false |

1. 업로드 전 파일 검사에서 거절하면 기존처럼 C4001이다. WAV, M4A, MP4, WEBM을 지원하며 길이는 60초 이하다. 공통 모듈이 길이를 읽지 못하는 형식은 5MB, 인식 서버는 전체 20MB 제한과 디코딩 후 60초 제한을 다시 적용한다.
2. 파일과 PROCESSING 초안을 먼저 저장한 뒤 트랜잭션 밖에서 인식한다. 저장 후 인식에 실패하면 HTTP 성공 응답 안에 실패 상태와 재시도 가능 여부를 반환한다. 프론트는 `error.code` 외에 `processingStatus`도 확인해야 한다.
3. `STT_FAILED`는 저장한 파일로 재시도한다. `LLM_FAILED`는 원문을 재사용해 정리만 다시 한다. 외부 URL을 내려받지 않으며 현재 로컬 파일 저장소의 음성 파일만 읽는다.
4. 처리 토큰을 비교하여 이전 요청의 늦은 결과가 재시도 결과를 덮어쓰지 못하게 한다. 2분 동안 중복 처리를 막고, 서버 중단 후에는 같은 초안을 다시 처리할 수 있다. 서버가 응답 전에 중단되어 클라이언트가 초안 ID를 받지 못한 경우 자동 복구 목록 API는 아직 없다.
5. LLM 실패 시 원문 앞 1000자를 임시 요약으로 제공한다. 원문이 있으면 사용자가 확인하고 확정할 수 있다. STT 실패로 원문이 없는 메모는 확정할 수 없다.
6. 담당자와 미완료 작업 여부를 매번 검증한다. 다른 담당자는 C403, 없는 데이터는 C404, 완료된 작업이나 중복 처리 중 요청은 C4095다.

분석 중 다른 요청에서 작업을 완료한 경우에도 파일과 늦게 도착한 원문은 보관한다. 완료된 작업은 다시 처리할 수 없으므로 실패 응답의 `retryable`은 false다. 해당 메모를 뒤늦게 확정하거나 돌봄 기록에 자동 추가하지 않는다. 위 표의 재시도 가능 여부는 미완료 작업 기준이다.

## 확정과 돌봄 기록

1. `TaskVoiceReader`가 실제 `care_task`와 현재 텃밭 담당자를 조회한다. `CareGardenReader`의 팀 도메인 구현이 있어야 사용할 수 있다.
2. 잠금 순서는 작업, 텃밭, 음성 메모다. 작업 완료와 메모 확정이 동일한 작업 행을 잠근다. 분석 HTTP 요청 중에는 DB 잠금을 유지하지 않는다.
3. 같은 확정 요청은 멱등 처리한다. 확정된 문장을 다른 내용으로 변경하면 C4095다. 확정할 때 문장을 수정하면 기존 이상 징후 코드는 비운다.
4. 작업 완료에서 `getConfirmedForTask`로 해당 작업의 확정된 메모만 읽어 `CareLog.transcript`, `summary`, `voiceMemoId`에 저장한다. 메모 확정 자체는 작업을 완료하지 않는다.
5. 확정된 최근 이상 징후는 다음 작업 생성 입력에 전달한다. 가족용 성장 일기 API와의 연결은 팀 일기 도메인에 별도로 구현해야 한다.

| code | 화면 문구 |
| --- | --- |
| LEAF_SPOT | 잎에 점이 있어요 |
| YELLOW_LEAF | 잎이 노랗게 보여요 |
| WILTING | 작물이 시들어 보여요 |
| PEST_SUSPECTED | 벌레가 보인다는 말씀이 있어요 |
| STEM_DAMAGE | 줄기가 손상된 것 같아요 |

병명을 확진하는 기능이 아니다. LLM에는 원문 안의 근거를 요구하며 관리사 확인을 거친다.

## DB와 검사

1. 새 DB는 `src/main/resources/db/manual/voice_memo.sql`을 검토 후 적용한다. prod는 `ddl-auto=validate`이며 자동 적용하지 않는다. 이전 초안 테이블이 이미 있다면 nullable 원문과 요약, 처리 상태와 토큰, 시작 시각과 오류 필드를 별도 마이그레이션해야 한다.
2. 저장은 성공했지만 DB 트랜잭션이 실패한 파일의 보상 삭제는 남아 있다. 파일을 무조건 삭제하면 재시도용 파일까지 지울 수 있어 자동 삭제를 추가하지 않았다.
3. 저장소 대역 검사에서 권한, 중복 확정, STT 실패 보존, 만료된 처리 재시도, 늦은 결과 거절, LLM만 재시도하는 경로를 확인했다. MariaDB 행 잠금과 실제 JWT 요청 검사는 별도로 필요하다.
