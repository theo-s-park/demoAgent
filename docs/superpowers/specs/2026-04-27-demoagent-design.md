# demoAgent 설계 문서

## 개요

LLM이 출력한 JSON 지시문을 Agent 서버가 파싱하여 외부 Tool API를 호출하고, 결과를 다시 LLM에 전달해 최종 답변을 생성하는 데모 시스템.

**목적:** 프롬프트 엔지니어링과 JSON 파싱만으로 외부 도구 사용 흐름을 구현할 수 있음을 검증한다.

---

## 아키텍처

```
Browser
  ├── GET  /             → index.html (Spring Boot static)
  └── POST /api/agent/run (SSE stream)
        └── Agent Server (Spring Boot :8080)
              ├── OpenAI API (gpt-4o)
              └── Tool Server (FastAPI :8081)
                    ├── POST /tools/random      → Java Random
                    ├── POST /tools/currency    → ExchangeRate-API
                    └── POST /tools/weather     → OpenWeatherMap
```

**Docker Compose** 로 agent-server + tool-server 동시 기동.

---

## 컴포넌트

### Agent Server (Spring Boot :8080)

| 패키지 | 역할 |
|--------|------|
| `controller/` | `AgentController` — SSE 엔드포인트 `POST /api/agent/run` |
| `service/` | `AgentService` — Agent 루프 로직 |
| `client/` | `OpenAiClient`, `ToolClient` — 외부 HTTP 호출 |
| `dto/` | `LlmResponse`, `ToolRequest`, `ToolResponse`, `StepEvent` |
| `config/` | `WebConfig` — CORS 등 |

**Static UI:** `src/main/resources/static/index.html` (vanilla JS)

### Tool Server (FastAPI :8081)

```
tool-server/
├── main.py
├── requirements.txt
└── Dockerfile
```

| 엔드포인트 | 설명 | 외부 API |
|-----------|------|---------|
| `POST /tools/random` | 범위 내 난수 반환 | 없음 (Java Random) |
| `POST /tools/currency` | 통화 변환 | ExchangeRate-API |
| `POST /tools/weather` | 날씨 정보 | OpenWeatherMap |

---

## Agent 루프

최대 5회 반복:

1. OpenAI 호출 (system prompt에 Tool 목록 + JSON 응답 형식 포함)
2. 응답 JSON 파싱
   - `action=call` → Tool Server HTTP 호출 → 결과를 대화에 추가 → SSE `step` 이벤트 전송 → 반복
   - `action=final_answer` → SSE `final` 이벤트 전송 후 종료
3. JSON 파싱 실패 또는 Tool 호출 실패 → SSE `error` 이벤트 전송 후 종료
4. 5회 초과 시 → SSE `error` 이벤트 전송 후 종료

---

## LLM 응답 JSON 구조

### Tool 호출
```json
{
  "action": "call",
  "tool": "random",
  "args": { "min_val": 1, "max_val": 100 }
}
```

### 최종 답변
```json
{
  "action": "final_answer",
  "answer": "1에서 100 사이의 난수는 42입니다."
}
```

---

## SSE 이벤트 타입

| 이벤트 | 시점 | 데이터 |
|--------|------|--------|
| `step` | LLM 호출, Tool 호출 등 각 단계 | `{ "message": "..." }` |
| `final` | 최종 답변 완성 | `{ "answer": "..." }` |
| `error` | 파싱 실패, Tool 오류, 루프 초과 | `{ "message": "..." }` |

---

## 환경변수

```env
OPENAI_API_KEY=
EXCHANGERATE_API_KEY=
OPENWEATHERMAP_API_KEY=
TOOL_SERVER_URL=http://tool-server:8081
```

---

## Tool 요청/응답 스펙

### Random
- 요청: `{ "min_val": int, "max_val": int }`
- 응답: `{ "value": int }`

### Currency
- 요청: `{ "amount": float, "from": str, "to": str }`
- 응답: `{ "amount": float, "from": str, "to": str, "converted": float }`

### Weather
- 요청: `{ "lat": float, "lon": float }`
- 응답: `{ "temperature": float, "humidity": int, "condition": str }`

---

## 향후 확장

- Web UI → React + Vercel 분리 배포 (백엔드 SSE 엔드포인트 변경 없음)
- Tool 추가 시 FastAPI `main.py`에 라우터만 추가
