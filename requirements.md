# 요구사항 명세

## 1. 프로젝트 개요

### 한 줄 정의
LLM이 출력한 JSON 지시문을 Agent 서버가 파싱하여 독립 Tool 서버를 HTTP로 호출하고, 결과를 다시 LLM에 전달해 최종 답변을 생성하는 데모 시스템.

### 핵심 원칙
- Agent는 어떤 도구가 있는지 코드로 알지 않는다. 시스템 프롬프트만 본다.
- LLM은 도구를 직접 호출하지 않는다. JSON 호출 지시만 반환한다.
- 각 Tool은 독립 서버다. Agent와 HTTP로만 통신한다.
- **도구 추가 = 시스템 프롬프트 수정.** 코드 변경 없이 LLM이 FastAPI 코드를 생성해 새 도구를 런타임에 추가할 수 있다.

---

## 2. MVP 목표

1. LLM은 시스템 프롬프트만 보고 어떤 도구를 어떤 URL로 호출할지 판단한다.
2. LLM은 도구를 직접 호출하지 않고 JSON 형식의 호출 지시를 반환한다.
3. Agent 서버는 JSON을 파싱해 LLM이 지정한 URL로 HTTP를 호출한다.
4. Tool 결과를 다시 LLM에 전달해 최종 답변을 생성한다.
5. 사용자는 Web UI에서 질문, 실행 과정, 최종 답변을 실시간으로 확인할 수 있다.
6. 사용자는 자연어로 새 도구를 설명하면, LLM이 코드를 생성해 런타임에 도구를 추가한다.

---

## 3. 시스템 구성

```
Browser (index.html)
  ├── POST /api/agent/run          → AgentController (SSE)
  │       └── AgentService
  │             ├── OpenAiClient   → OpenAI API (gpt-4o)
  │             └── ToolClient     → Tool Server HTTP
  │
  └── POST /api/tool-creator/create → ToolCreatorController (SSE)
          └── ToolCreatorService
                ├── OpenAiClient   → OpenAI API (gpt-4o)
                └── 파일 시스템 조작
                      ├── tool-server/{name}_app.py 생성
                      ├── .env.local 환경변수 추가
                      ├── system-prompt.txt 도구 항목 추가
                      └── uvicorn 프로세스 시작
```

**Tool Server** — 도구마다 독립 FastAPI 프로세스

| 도구 | 포트 | 파일 |
|------|------|------|
| 난수 생성 | 8081 | `random_app.py` |
| 환율 변환 | 8082 | `currency_app.py` |
| 날씨 조회 | 8083 | `weather_app.py` |
| 동적 추가 도구 | 8090+ | `{name}_app.py` |

---

## 4. 기능 요구사항

### 4.1 Web UI

| ID | 요구사항 |
|----|----------|
| UI-001 | 사용자는 질문을 입력하고 에이전트를 실행할 수 있다 |
| UI-002 | 실행 중에는 로딩 상태를 표시한다 |
| UI-003 | Agent 실행 과정을 단계별 로그로 실시간 확인할 수 있다 (SSE) |
| UI-004 | 최종 답변을 확인할 수 있다 |
| UI-005 | 오류 발생 시 오류 메시지를 표시한다 |
| UI-006 | "도구 추가" 탭에서 자연어로 새 도구 추가를 요청할 수 있다 |
| UI-007 | LLM이 API 키 등 추가 정보를 요청하면 입력 폼이 동적으로 나타난다 |
| UI-008 | 도구 추가 진행 과정을 실시간으로 확인할 수 있다 (SSE) |

---

### 4.2 Agent Server

| ID | 요구사항 |
|----|----------|
| AG-001 | `POST /api/agent/run` — SSE 스트림으로 실행 과정을 반환한다 |
| AG-002 | 요청마다 `system-prompt.txt`를 파일에서 새로 읽는다 (런타임 도구 반영) |
| AG-003 | LLM 응답 JSON을 파싱한다. 마크다운 코드블록으로 감싸인 경우도 처리한다 |
| AG-004 | `action=call` → LLM이 지정한 URL로 Tool HTTP 호출 |
| AG-005 | Tool 결과를 대화에 추가해 LLM을 재호출한다 |
| AG-006 | `action=final_answer` → `final` SSE 이벤트로 반환하고 종료 |
| AG-007 | 최대 반복 횟수는 5회로 제한한다 |
| AG-008 | JSON 파싱 실패, Tool 호출 실패, 루프 초과 시 `error` SSE 이벤트를 반환한다 |

---

### 4.3 Tool Creator (메타 에이전트)

| ID | 요구사항 |
|----|----------|
| TC-001 | `POST /api/tool-creator/create` — SSE 스트림으로 진행 과정을 반환한다 |
| TC-002 | LLM이 필요한 정보(API 키 등)가 있으면 `form` SSE 이벤트로 질문 목록을 반환한다 |
| TC-003 | 사용자가 답변을 제출하면 동일 요청을 answers와 함께 재전송한다 |
| TC-004 | LLM이 생성한 FastAPI 코드를 `tool-server/{name}_app.py`에 저장한다 |
| TC-005 | 환경변수를 `.env.local`에 추가한다 |
| TC-006 | 시스템 프롬프트(`system-prompt.txt`)의 `[응답 형식]` 앞에 도구 항목을 삽입한다 |
| TC-007 | `tool-server/.venv/Scripts/uvicorn`으로 새 도구 프로세스를 시작한다 |
| TC-008 | 포트는 8090부터 순차 할당한다 (in-memory, 재시작 시 초기화) |

---

### 4.4 Tool Server 공통

| ID | 요구사항 |
|----|----------|
| TS-001 | 각 Tool은 독립 FastAPI 프로세스로 실행된다 |
| TS-002 | 단일 엔드포인트 `POST /execute`만 제공한다 |
| TS-003 | Tool은 Agent가 어떤 도구인지 알지 못한다. 요청을 받아 결과만 반환한다 |

---

### 4.5 기본 제공 Tool 스펙

#### Random
- 요청: `{ "min_val": int, "max_val": int }`
- 응답: `{ "value": int }`

#### Currency
- 요청: `{ "amount": float, "from": str, "to": str }`
- 응답: `{ "amount": float, "from": str, "to": str, "converted": float }`
- 외부 API: ExchangeRate-API

#### Weather
- 요청: `{ "lat": float, "lon": float }`
- 응답: `{ "temperature": float, "humidity": int, "condition": str }`
- 외부 API: OpenWeatherMap

---

## 5. LLM 응답 구조

### Agent — Tool 호출
```json
{
  "action": "call",
  "url": "http://localhost:8081/execute",
  "args": { "min_val": 1, "max_val": 100 }
}
```

### Agent — 최종 답변
```json
{
  "action": "final_answer",
  "answer": "1에서 100 사이의 난수는 42입니다."
}
```

### Tool Creator — 추가 정보 필요
```json
{
  "action": "need_info",
  "tool_name": "stock",
  "questions": ["주식 API 키를 입력해주세요", "사용할 주식 데이터 제공사는?"]
}
```

### Tool Creator — 코드 생성
```json
{
  "action": "create_tool",
  "tool_name": "stock",
  "code": "# FastAPI app code ...",
  "env_vars": { "STOCK_API_KEY": "placeholder" },
  "prompt_entry": "4. 주식 현재가\n   URL: http://localhost:{PORT}/execute\n   인자: {\"symbol\": \"<티커>\"}\n   설명: 주식 현재가 반환"
}
```

---

## 6. SSE 이벤트 타입

| 이벤트 | 시점 | 데이터 |
|--------|------|--------|
| `step` | 각 실행 단계 | `{ "message": "..." }` |
| `final` | 최종 답변 완성 | `{ "message": "..." }` |
| `error` | 파싱 실패, 호출 실패, 루프 초과 | `{ "message": "..." }` |
| `form` | Tool Creator가 추가 정보 요청 | `{ "message": "{\"tool_name\":\"...\",\"questions\":[...]}" }` |

---

## 7. 환경변수

```env
OPENAI_API_KEY=           # OpenAI API 키
EXCHANGERATE_API_KEY=     # exchangerate-api.com
OPENWEATHERMAP_API_KEY=   # openweathermap.org
TOOL_SERVER_URL=          # 기본값: http://localhost:8081 (Docker: http://tool-server:8081)
```

동적으로 추가된 도구의 키는 `.env.local`에 저장된다.

---

## 8. 기술 스택

| 구분 | 기술 |
|------|------|
| Agent Server | Java 25, Spring Boot 4.0.6, Gradle 9 |
| HTTP 클라이언트 | Spring RestClient |
| JSON | Jackson (ObjectMapper를 클래스 필드로 직접 생성) |
| SSE | Spring SseEmitter |
| Tool Server | Python 3.12, FastAPI, uvicorn, httpx |
| 빌드/실행 | Docker Compose |
| JDK 경로 | `gradle.properties`의 `org.gradle.java.home` — BellSoft JDK 25 |
