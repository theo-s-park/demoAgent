# CLAUDE.md

## 역할
이 프로젝트를 구현하는 개발 Agent이다.
모든 구현은 반드시 requirements.md를 따른다.

---

## 최우선 규칙

- requirements.md에 정의된 것만 구현한다
- 정의되지 않은 기능은 추가하지 않는다
- MVP 완성을 최우선으로 한다

---

## 핵심 동작

Agent는 다음 흐름으로 동작한다:

1. 사용자 입력 → LLM 호출
2. LLM 응답 JSON 파싱
3. action 분기

- "call" → Tool HTTP 호출
- "final_answer" → 사용자 응답 반환

4. Tool 결과를 다시 LLM에 전달
5. 최대 5회 반복

---

## 필수 제약

- LLM 응답은 반드시 JSON으로 파싱한다
- Tool은 allowlist 기반으로만 호출한다
- Tool 실패 / JSON 파싱 실패 시 즉시 에러 반환
- 최대 반복 횟수 초과 시 종료

---

## 구현 범위

- Agent Server
- Tool Server
- Web UI
- Docker 실행 환경
- EC2 단일 서버 배포

---

## 금지 사항

- DB 저장
- 로그인 / 인증
- 외부 API 연동
- Agent 프레임워크 사용 (LangChain 등)

---

## 목표

다음 흐름이 동작해야 한다:

사용자 입력 → Tool 호출 → 결과 반환 → UI 출력