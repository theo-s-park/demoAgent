# demoAgent 설계 문서

## 개요

LLM이 출력한 JSON 지시문을 Agent 서버가 파싱하여 독립 Tool 서버를 HTTP로 호출하고, 결과를 다시 LLM에 전달해 최종 답변을 생성하는 데모 시스템.

**핵심 명제:** 도구 추가 = 시스템 프롬프트 수정. 코드 변경 없이 LLM이 FastAPI 코드를 생성하고, 새 도구를 런타임에 추가할 수 있다.

---

## 아키텍처

```
Browser (index.html)
  ├── POST /api/agent/run            → AgentController (SSE)
  │       └── AgentService
  │             ├── OpenAiClient     → OpenAI API (gpt-4o)
  │             └── ToolClient       → Tool Server HTTP (URL-based)
  │
  └── POST /api/tool-creator/create  → ToolCreatorController (SSE)
          └── ToolCreatorService
                ├── OpenAiClient     → OpenAI API (gpt-4o)
                └── 파일 시스템 조작
                      ├── tool-server/{name}_app.py 생성
                      ├── .env.local 환경변수 추가
                      ├── system-prompt.txt 도구 항목 삽입
                      └── uvicorn 프로세스 시작 (포트 8090+)
```

**Docker Compose** 로 agent-server(:8080) + 각 tool-server 동시 기동.

---

## 컴포넌트

### Agent Server (Spring Boot :8080)

| 패키지 | 파일 | 역할 |
|--------|------|------|
| `controller/` | `AgentController` | SSE 엔드포인트 `POST /api/agent/run` |
| `controller/` | `ToolCreatorController` | SSE 엔드포인트 `POST /api/tool-creator/create` |
| `service/` | `AgentService` | Agent 루프 (최대 5회), system-prompt.txt 매 요청 재로드 |
| `service/` | `ToolCreatorService` | 메타 에이전트: 코드 생성, 파일 저장, 프로세스 시작 |
| `client/` | `OpenAiClient` | OpenAI chat completion 호출 |
| `client/` | `ToolClient` | LLM이 지정한 URL로 HTTP POST |
| `dto/` | `AgentEvent` | SSE 이벤트 (step / final / error / form) |
| `dto/` | `LlmResponse` | Agent LLM 응답 파싱 |
| `dto/` | `ToolCreatorLlmResponse` | Tool Creator LLM 응답 파싱 |
| `config/` | `WebConfig` | CORS |

**Static UI:** `src/main/resources/static/index.html`

### Tool Server (FastAPI, 독립 프로세스)

| 도구 | 포트 | 파일 | 외부 API |
|------|------|------|---------|
| 난수 생성 | 8081 | `random_app.py` | 없음 |
| 환율 변환 | 8082 | `currency_app.py` | ExchangeRate-API |
| 날씨 조회 | 8083 | `weather_app.py` | OpenWeatherMap |
| 동적 추가 | 8090+ | `{name}_app.py` | 도구에 따라 다름 |

모든 Tool은 `POST /execute` 단일 엔드포인트만 제공한다.

---

## Agent 루프

최대 5회 반복:

1. `system-prompt.txt`를 파일에서 읽어 messages 구성 (파일 없으면 classpath fallback)
2. OpenAI 호출 → JSON 파싱 (마크다운 코드블록 제거 후 파싱)
3. `action=call` → LLM이 반환한 URL로 ToolClient HTTP POST → 결과를 대화에 추가 → SSE `step` → 반복
4. `action=final_answer` → SSE `final` → 종료
5. 파싱 실패 / Tool 오류 / 5회 초과 → SSE `error` → 종료

---

## Tool Creator 흐름

1. 사용자가 "기능 설명" 입력 → `POST /api/tool-creator/create`
2. LLM이 `need_info` 반환 → `form` SSE 이벤트로 질문 목록 전송
3. 브라우저가 입력 폼 렌더링 → 사용자가 API 키 등 입력 → 동일 엔드포인트에 answers 포함 재전송
4. LLM이 `create_tool` 반환:
   - `{name}_app.py` 생성 (`tool-server/` 디렉터리)
   - `.env.local`에 환경변수 append
   - `system-prompt.txt`의 `[응답 형식]` 앞에 도구 항목 삽입
   - `uvicorn {name}_app:app --port {port}` 프로세스 시작 (포트: 8090부터 순차 할당)
5. SSE `final` 이벤트로 완료 알림

---

## LLM 응답 JSON 구조

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
  "questions": ["주식 API 키를 입력해주세요"]
}
```

### Tool Creator — 코드 생성
```json
{
  "action": "create_tool",
  "tool_name": "stock",
  "code": "# FastAPI app ...",
  "env_vars": { "STOCK_API_KEY": "placeholder" },
  "prompt_entry": "N. 주식 현재가\n   URL: http://localhost:{PORT}/execute\n   인자: {\"symbol\": \"<티커>\"}"
}
```

---

## SSE 이벤트 타입

| 이벤트 | 시점 | payload |
|--------|------|---------|
| `step` | 각 실행 단계 | `{ "message": "..." }` |
| `final` | 최종 답변 | `{ "message": "..." }` |
| `error` | 파싱 실패 / 호출 실패 / 루프 초과 | `{ "message": "..." }` |
| `form` | Tool Creator — 추가 정보 요청 | `{ "message": "{\"tool_name\":\"...\",\"questions\":[...]}" }` |

---

## 환경변수

```env
OPENAI_API_KEY=
EXCHANGERATE_API_KEY=
OPENWEATHERMAP_API_KEY=
TOOL_SERVER_URL=          # 기본값 http://localhost:8081 (Docker: http://tool-server:8081)
```

동적 도구 추가 시 해당 키는 `.env.local`에 저장.

---

## 향후 확장

- Web UI → React + Vercel 분리 배포 (SSE 엔드포인트 변경 없음)
- Tool Creator 생성 도구 → Docker Compose에 서비스로 추가
- 포트 할당 → 파일 기반 영속화 (현재 in-memory, 재시작 시 8090부터 재할당)
