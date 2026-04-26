현재 브랜치를 push하고, 테스트를 실행한 뒤 PR을 자동으로 생성합니다.

## 절차

### 1. 사전 확인
- `git status`와 `git diff HEAD`로 커밋되지 않은 변경사항이 없는지 확인
- 커밋되지 않은 변경사항이 있으면 중단하고 사용자에게 알린다

### 2. 테스트 코드 자동 생성 및 실행
- `git diff origin/main...HEAD --name-only --diff-filter=ACM`으로 변경된 `src/main/kotlin` 하위 `.kt` 파일 목록 추출
- 변경된 파일이 없으면 테스트 단계 건너뜀
- 각 파일에 대해 `src/test/kotlin` 하위 대응 경로에 JUnit5 + MockK 기반 테스트 코드 작성
  - 파일명은 원본 파일명 + `Test.kt`
  - 패키지 경로는 원본과 동일하게 유지
- `./gradlew test` 실행
- 테스트 성공/실패 여부와 무관하게 생성한 테스트 파일 전체 삭제 (`if: always()` 와 동일한 개념)
- 테스트 실패 시 push를 중단하고 실패 내용을 사용자에게 보고

### 3. Push
- `git push -u origin {현재 브랜치명}` 실행

### 4. PR 생성
- `git log origin/main...HEAD --oneline`으로 커밋 목록 확인
- `git diff origin/main...HEAD`로 전체 변경 내역 확인
- 아래 PR 템플릿을 분석된 내용으로 채워 `gh pr create` 실행

```
## Situation

- (변경이 필요했던 배경/문제 상황)

## Task
> Slack PR 알림의「작업 요약」에 표시되는 섹터입니다.
- (무엇을 구현/수정했는지 한 줄 요약)

## Action

- (구체적으로 어떤 작업을 했는지 bullet point)

## Result

- (결과 및 기대 효과)

---
## 연관 이슈

close #
```

- PR 제목은 `{타입}: {브랜치명에서 추출한 설명}` 형식으로 작성
- 연관 이슈 번호를 알 수 없으면 `close #` 뒤를 비워두고 사용자에게 이슈 번호를 묻는다

$ARGUMENTS
