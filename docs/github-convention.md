# Issue / PR 컨벤션

에이전트(Claude, Codex 등)가 Issue나 PR을 작성하거나 초안을 제안할 때는 이 문서를 따른다.
실제 템플릿 파일은 `.github/`에 있고, GitHub에서 Issue나 PR 생성 시 자동으로 적용된다.

## 브랜치 전략

| 브랜치 | 역할 |
| --- | --- |
| `develop` | 기본 브랜치. 모든 작업을 이 브랜치에 계속 병합하면서 진행한다 |
| `main` | 최종 반영용. 마지막에 `develop`에서 `main`으로 올리는 PR로만 반영한다 |

- 기능 작업 PR의 base는 항상 `develop`이다. `main`을 base로 기능 PR을 올리지 않는다.
- `main`에는 직접 커밋하거나 푸시하지 않는다.
- 기본 브랜치를 `develop`으로 바꾸는 작업은 사용자가 직접 한다.

## Issue

기능 단위로 Issue를 생성한다. Issue 생성 버튼을 누르면 등록된 템플릿을 고를 수 있다.

| 템플릿 | 파일 | 제목 접두어 |
| --- | --- | --- |
| Template: Feature (기능 추가) | `.github/ISSUE_TEMPLATE/feature.md` | `[Feature] ` |
| Template: Bug (버그) | `.github/ISSUE_TEMPLATE/bug.md` | `[Bug] ` |
| Template: Refactor (리팩토링) | `.github/ISSUE_TEMPLATE/refactor.md` | `[Refactor] ` |

### Feature

```markdown
## 💡 이슈 내용
- 기능 설명

## ✅ 상세 작업 내용
- [ ] 기능 1
- [ ] 기능 2
```

### Bug

```markdown
## 🐛 버그 내용
- [ ] 문제 상황

## 서버 로그 or 스크린샷
- 서버 로그 또는 사진 첨부
```

### Refactor

```markdown
## ⚙️ 리팩토링 내용
- [ ] 내용 1
- [ ] 내용 2
```

## Pull Request

PR 작성 시 `.github/PULL_REQUEST_TEMPLATE.md` 템플릿을 활용한다.

### 제목

`[Tag] 작업 내용` 형식으로 쓴다.

> 예시: `[Feature] 사용자 회원가입 기능 구현`

| Tag | Description |
| --- | --- |
| Feature | 새 기능을 추가했을 때 |
| Bug | 버그를 수정했을 때 |
| Refactor | 리팩토링 했을 때 |

### 본문

```markdown
## 개요
- close #작업한_이슈_번호

## 작업사항
- 내용을 적어주세요.
```

- `close #번호`로 작업한 Issue를 연결해 머지 시 자동으로 닫히게 한다. 이슈 번호를 모르면 임의로 만들지 말고 사용자에게 묻는다.
- PR 본문에도 "Generated with Claude Code" 같은 AI 도구 표기를 넣지 않는다. 에이전트 기본 템플릿에 있어도 제거한다.

## 커밋, Issue, PR 연결

1. Issue: `[Feature] 회원가입` (#1)
2. 커밋: `feat: 회원가입 기능 구현 (#1)`
3. PR: `[Feature] 사용자 회원가입 기능 구현`, 본문에 `close #1`

커밋 메시지 규칙은 [commit-convention.md](commit-convention.md)를 따른다.
