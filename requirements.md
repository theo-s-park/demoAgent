# 1. 프로젝트 개요

## 한 줄 정의
LLM이 출력한 JSON 지시문을 Agent 서버가 파싱하여 외부 Tool API를 호출하고, 결과를 다시 LLM에 전달해 최종 답변을 생성하는 데모 시스템

## 목적
프롬프트 엔지니어링과 JSON 파싱만으로 외부 도구 사용 흐름을 구현할 수 있음을 검증한다.

---

# 2. MVP 목표

본 MVP는 다음을 증명하는 것을 목표로 한다.

1. LLM은 도구 호출 방법을 시스템 프롬프트만 보고 판단한다.
2. LLM은 실제 도구를 호출하지 않고 JSON 형식의 호출 지시만 반환한다.
3. Agent 서버는 JSON을 파싱해 독립 Tool 서버를 HTTP로 호출한다.
4. Tool 결과를 다시 LLM에 전달해 최종 답변을 생성한다.
5. 사용자는 Web UI에서 질문, 실행 과정, 최종 답변을 확인할 수 있다.

---

# 3. 시스템 구성

## Web UI
- 사용자 질문 입력
- Agent 실행 과정 표시
- 최종 답변 표시

## Agent Server
- 사용자 요청 수신
- LLM API 호출
- LLM 응답 JSON 파싱
- Tool HTTP 호출
- Tool 결과를 LLM에 재전달
- 최종 응답 반환

## Tool Server
- 독립 실행되는 HTTP API
- Agent로부터 요청을 받아 결과 반환

---

# 4. MVP 기능 요구사항

## 4.1 Web UI

| ID     | 요구사항 |
|--------|----------|
| UI-001 | 사용자는 질문을 입력할 수 있다 |
| UI-002 | 사용자는 실행 버튼을 눌러 Agent를 실행할 수 있다 |
| UI-003 | 사용자는 Agent의 실행 과정을 단계별 로그로 확인할 수 있다 |
| UI-004 | 사용자는 최종 답변을 확인할 수 있다 |
| UI-005 | 요청 처리 중에는 로딩 상태를 표시한다 |
| UI-006 | 오류 발생 시 오류 메시지를 표시한다 |

---

## 4.2 Agent Server

| ID     | 요구사항 |
|--------|----------|
| AG-001 | 사용자 질문을 받아 LLM API를 호출한다 |
| AG-002 | 시스템 프롬프트에 Tool 목록과 응답 형식을 포함한다 |
| AG-003 | LLM 응답을 JSON으로 파싱한다 |
| AG-004 | action 값이 call이면 Tool API를 호출한다 |
| AG-005 | Tool 결과를 다시 LLM에게 전달한다 |
| AG-006 | action 값이 final_answer이면 사용자에게 반환한다 |
| AG-007 | 최대 반복 횟수는 5회로 제한한다 |
| AG-008 | JSON 파싱 실패 시 에러를 반환한다 |
| AG-009 | Tool 호출 실패 시 에러를 반환한다 |
| AG-010 | 실행 로그를 Web UI에 반환한다 |

---

## 4.3 Tool Server

MVP에서는 최소 3개의 Tool을 제공한다.

| Tool | 설명 |
|------|------|
| Random | 지정 범위 내 난수 반환 |
| Currency | 통화 변환 |
| Weather | 날씨 정보 반환 |

---

## 4.4 Random Tool

| ID | 요구사항 |
|----|----------|
| TOOL-R-001 | min_val, max_val 입력 |
| TOOL-R-002 | 범위 내 정수 난수 반환 |
| TOOL-R-003 | 응답: {"value": number} |

---

## 4.5 Currency Tool

| ID | 요구사항 |
|----|----------|
| TOOL-C-001 | amount, from, to 입력 |
| TOOL-C-002 | 고정 환율 사용 가능 |
| TOOL-C-003 | 응답: {"amount", "from", "to", "converted"} |

---

## 4.6 Weather Tool

| ID | 요구사항 |
|----|----------|
| TOOL-W-001 | lat, lon 입력 |
| TOOL-W-002 | mock 데이터 가능 |
| TOOL-W-003 | 응답: {"temperature", "humidity", "condition"} |

---

# 5. LLM 응답 구조

## Tool 호출

```json
{
  "action": "call",
  "tool": "random",
  "args": {
    "min_val": 1,
    "max_val": 100
  }
}