# 요구사항 명세

## 1. 프로젝트 개요

### 한 줄 정의
LLM이 출력한 JSON 지시문을 Agent 서버가 파싱하여 독립 Tool 서버를 HTTP로 호출하고, 결과를 다시 LLM에 전달해 최종 답변을 생성하는 데모 시스템.

### 핵심 원칙
- Agent는 어떤 도구가 있는지 코드로 알지 않는다. 시스템 프롬프트만 본다.
- LLM은 도구를 직접 호출하지 않는다. JSON 호출 지시만 반환한다.
- 각 Tool은 독립 서버다. Agent와 HTTP로만 통신한다.
- **도구 추가 = 시스템 프롬프트 수정.** 코드 변경 없이 LLM이 FastAPI 코드를 생성해 새 도구를 런타임에 추가할 수 있다.
- **도구 추가 완료 = 프로세스 기동 + `/health` 확인.** 프롬프트에 등록만 되고 살아있지 않은 도구는 성공으로 치지 않는다.

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
  ├── POST /api/agent/run              → AgentController (SSE)
  │       └── AgentService
  │             ├── OpenAiClient       → OpenAI API (gpt-4o)
  │             └── ToolClient         → Tool Server HTTP (URL-based)
  │
  ├── GET  /api/tools                  → ToolsController (도구 목록)
  ├── GET  /api/tools/health           → ToolsController (도구별 /health 상태)
  ├── GET  /api/tools/prompt           → ToolsController (system-prompt.txt 원문)
  ├── PUT  /api/tools/prompt           → ToolsController (system-prompt.txt 저장)
  │
  └── POST /api/tool-creator/create    → ToolCreatorController (SSE)
          └── ToolCreatorService
                ├── OpenAiClient       → OpenAI API (gpt-4o)
                └── 파일 시스템 조작
                      ├── tool-server/{name}_app.py 생성
                      ├── .env.local 환경변수 추가
                      ├── system-prompt.txt 도구 항목 삽입
                      └── python -m uvicorn ... 프로세스 시작 (포트 8090+)
                            └── GET /health 폴링 → 성공 시에만 final 이벤트
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
| UI-006 | 사이드바에서 등록된 도구 목록을 확인할 수 있다 (GET /api/tools) |
| UI-007 | 사이드바에서 각 도구의 헬스 상태를 새로고침할 수 있다 (GET /api/tools/health) |
| UI-008 | 사이드바에서 system-prompt.txt 원문을 확인하고 수정할 수 있다 (GET/PUT /api/tools/prompt) |
| UI-009 | "도구 추가" 탭에서 자연어로 새 도구 추가를 요청할 수 있다 |
| UI-010 | LLM이 API 키 등 추가 정보를 요청하면 입력 폼이 동적으로 나타난다 |
| UI-011 | 도구 추가 진행 과정을 실시간으로 확인할 수 있다 (SSE) |

---

### 4.2 Agent Server

| ID | 요구사항 |
|----|----------|
| AG-001 | `POST /api/agent/run` — SSE 스트림으로 실행 과정을 반환한다 |
| AG-002 | 요청마다 `system-prompt.txt`를 파일에서 새로 읽는다. 탐색 순서: `{baseDir}/system-prompt.txt` → `{baseDir}/src/main/resources/system-prompt.txt` → classpath |
| AG-003 | 현재 시각(KST, ISO-8601)을 system 메시지로 주입해 연도 생략 날짜 해석을 안정화한다 |
| AG-004 | LLM 응답 JSON을 파싱한다. 마크다운 코드블록으로 감싸인 경우도 처리한다 |
| AG-005 | `action=call` → LLM이 지정한 URL로 Tool HTTP 호출 |
| AG-006 | Tool 결과를 대화에 추가해 LLM을 재호출한다 |
| AG-007 | `action=final_answer` → `final` SSE 이벤트로 반환하고 종료 |
| AG-008 | 최대 반복 횟수는 5회로 제한한다 |
| AG-009 | JSON 파싱 실패, Tool 호출 실패, 루프 초과 시 `error` SSE 이벤트를 반환한다 |

---

### 4.3 Tool Manager API

| ID | 요구사항 |
|----|----------|
| TM-001 | `GET /api/tools` — system-prompt.txt의 `[사용 가능한 도구]` 구간을 파싱해 도구 목록(id/name/url/args/desc)을 반환한다 |
| TM-002 | `GET /api/tools/health` — 각 도구의 `/health` 엔드포인트를 호출해 상태(ok/latency/status code)를 반환한다 |
| TM-003 | `GET /api/tools/prompt` — system-prompt.txt 원문을 반환한다 |
| TM-004 | `PUT /api/tools/prompt` — system-prompt.txt를 저장한다. `{baseDir}/system-prompt.txt`와 `src/main/resources/system-prompt.txt`를 동기화한다 |

---

### 4.4 Tool Creator (메타 에이전트)

| ID | 요구사항 |
|----|----------|
| TC-001 | `POST /api/tool-creator/create` — SSE 스트림으로 진행 과정을 반환한다 |
| TC-002 | LLM이 필요한 정보(API 키 등)가 있으면 `form` SSE 이벤트로 질문 목록을 반환한다. 질문은 `{key, label, help, link, where_used}` 구조 객체 또는 단순 문자열 배열을 모두 처리한다 |
| TC-003 | 사용자가 답변을 제출하면 동일 요청을 answers와 함께 재전송한다 |
| TC-004 | `create_tool` 응답: LLM이 생성한 FastAPI 코드를 `tool-server/{name}_app.py`에 저장한다 |
| TC-005 | `create_tool` 응답: 환경변수를 `.env.local`에 추가한다 |
| TC-006 | `create_tool` 응답: `system-prompt.txt`의 `[응답 형식]` 앞에 도구 항목(`prompt_entry`)을 삽입한다 |
| TC-007 | `create_tool` 응답: `python -m uvicorn {name}_app:app --host 0.0.0.0 --port {port}`로 프로세스를 시작한다. python 탐색 순서: `tool-server/.venv/Scripts/python.exe` → `{baseDir}/.venv/Scripts/python.exe` → `python` |
| TC-008 | 프로세스 시작 후 `GET /health`를 최대 20회(500ms 간격) 폴링하여 2xx 응답이 확인될 때만 `final` 이벤트를 전송한다. 타임아웃 시 `error`로 종료한다 |
| TC-009 | 포트는 8090부터 in-memory로 순차 할당한다 (재시작 시 8090부터 재시작) |
| TC-010 | `update_files` 응답: 허용된 경로(`system-prompt.txt`, `src/main/resources/system-prompt.txt`, `tool-server/weather_app.py`)의 파일을 덮어쓴다. 그 외 경로는 거부한다 |

---

### 4.5 Tool Server 공통

| ID | 요구사항 |
|----|----------|
| TS-001 | 각 Tool은 독립 FastAPI 프로세스로 실행된다 |
| TS-002 | `POST /execute` — 실제 도구 실행 엔드포인트 |
| TS-003 | `GET /health` — `{"ok": true}` 반환. Tool Creator의 기동 확인에 사용된다 |
| TS-004 | Tool은 Agent가 어떤 도구인지 알지 못한다. 요청을 받아 결과만 반환한다 |

---

### 4.6 기본 제공 Tool 스펙

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
  "questions": [
    {
      "key": "PROVIDER",
      "label": "어떤 데이터 제공사를 사용할까요?",
      "help": "원하는 제공사 이름을 입력하세요.",
      "link": "",
      "where_used": "외부 API Base URL/엔드포인트 선택에 사용"
    },
    {
      "key": "ALPHAVANTAGE_API_KEY",
      "label": "Alpha Vantage API 키를 입력해주세요",
      "help": "Alpha Vantage 사이트에서 API Key 발급",
      "link": "https://www.alphavantage.co/support/#api-key",
      "where_used": "쿼리스트링 apikey=... 로 사용"
    }
  ]
}
```

### Tool Creator — 코드 생성
```json
{
  "action": "create_tool",
  "tool_name": "stock",
  "code": "# FastAPI app code ...",
  "env_vars": { "STOCK_API_KEY": "placeholder" },
  "prompt_entry": "- 주식 현재가\n   URL: http://localhost:{PORT}/execute\n   인자: {\"symbol\": \"<티커>\"}\n   설명: 주식 현재가 반환"
}
```

### Tool Creator — 기존 파일 수정
```json
{
  "action": "update_files",
  "files": [
    { "path": "system-prompt.txt", "content": "..." }
  ]
}
```

---

## 6. SSE 이벤트 타입

| 이벤트 | 시점 | 데이터 |
|--------|------|--------|
| `step` | 각 실행 단계 | `{ "message": "..." }` |
| `final` | 최종 답변 완성 | `{ "message": "..." }` |
| `error` | 파싱 실패, 호출 실패, 루프 초과, health timeout | `{ "message": "..." }` |
| `form` | Tool Creator가 추가 정보 요청 | `{ "message": "{\"tool_name\":\"...\",\"questions\":[{\"key\":...,\"label\":...}]}" }` |

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
| JSON | Jackson (ObjectMapper를 클래스 필드로 직접 생성 — webmvc는 bean 미등록) |
| SSE | Spring SseEmitter |
| Tool Server | Python 3.12, FastAPI, uvicorn, httpx |
| 빌드/실행 | Docker Compose |
| JDK 경로 | `gradle.properties`의 `org.gradle.java.home` — BellSoft JDK 25 (시스템 JAVA_HOME은 JDK 8로 고정, 건드리지 않음) |
