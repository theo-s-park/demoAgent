# demoAgent 설계 문서

## 개요

LLM이 출력한 JSON 지시문을 Agent 서버가 파싱하여 독립 Tool 서버를 HTTP로 호출하고, 결과를 다시 LLM에 전달해 최종 답변을 생성하는 데모 시스템.

**핵심 명제:** 도구 추가 = 시스템 프롬프트 수정. 코드 변경 없이 LLM이 FastAPI 코드를 생성하고, 새 도구를 런타임에 추가할 수 있다.

**현재 구현에서 추가로 강조하는 운영 명제:** “도구 추가”는 **프롬프트에 등록되는 것만으로 끝나지 않는다**. Tool Creator는 **프로세스 기동 + `/health` 확인까지 통과**해야만 성공(`final`)으로 간주한다.

---

## 아키텍처

**배치의 기준축은 Docker(Compose) 위의 런타임**이다. Agent와 각 Tool은 **컨테이너(또는 compose 서비스)로 띄워지고**, 서로 **브리지 네트워크·서비스 DNS 이름**으로 HTTP를 주고받는다. 브라우저는 호스트에 노출된 **게이트웨이 포트**(예: `:8080`)로 Spring에 접속한다. 로컬에서는 같은 코드 경로를 “프로세스 직기동”으로 재현할 수 있지만, 이 문서에서 말하는 **통합 배치 모델**은 컨테이너 간 통신을 전제로 한다.

**동적으로 움직이는 축**은 한 번 고정된 정적 배포가 아니라, 아래처럼 **생성·조회·변경·(운영 관점의) 제거**가 겹친다.

| 축 | 무엇이 움직이는가 |
| --- | --- |
| **생성** | Tool Creator가 `*_app.py`·`.env.local`·`system-prompt.txt`를 갱신하고 uvicorn 프로세스를 띄운 뒤, `/health`로 **살아 있음**을 확정할 때까지 성공으로 치지 않는다. |
| **조회** | `GET /api/tools`, `GET /api/tools/health`, `GET /api/tools/prompt`와 UI의 주기적 폴링으로 “프롬프트에 등록된 도구”와 “지금 응답하는 엔드포인트”를 반복적으로 읽는다. |
| **변경** | `PUT /api/tools/prompt`나 `update_files`로 프롬프트·허용 파일을 덮어쓰면, 다음 Agent 요청부터 LLM에 실리는 도구 정의가 바뀐다. |
| **제거** | 단일 “도구 삭제 API”에 고정되기보다, **프롬프트에서 해당 블록을 지우고** 대응 프로세스·컨테이너를 내리고 필요 시 파일을 정리하는 **운영 단계**로 맞춘다(스택 전체 리셋은 compose 재기동). |

```
Browser (index.html)
  ├── POST /api/agent/run            → AgentController (SSE)
  │       └── AgentService
  │             ├── OpenAiClient     → OpenAI API (gpt-4o)
  │             └── ToolClient       → Tool Server HTTP (URL-based)
  │
  ├── GET  /api/tools                → ToolsController (도구 목록)
  ├── GET  /api/tools/health         → ToolsController (도구 상태 점검)
  ├── GET  /api/tools/prompt         → ToolsController (system-prompt 원문)
  ├── PUT  /api/tools/prompt         → ToolsController (system-prompt 저장)
  │
  └── POST /api/tool-creator/create  → ToolCreatorController (SSE)
          └── ToolCreatorService
                ├── OpenAiClient     → OpenAI API (gpt-4o)
                └── 파일 시스템 조작
                      ├── tool-server/{name}_app.py 생성
                      ├── .env.local 환경변수 추가
                      ├── system-prompt.txt 도구 항목 삽입
                      └── python -m uvicorn ... 프로세스 시작 (포트 8090+)
                            └── GET /health 폴링(기동 확인) → 성공 시에만 final
```

**Docker Compose** 한 스택에서 agent-server(예: `:8080`)와 샘플 tool-server들을 동시에 올린다. 이후 Tool Creator로 붙는 **동적 Tool**은 호스트 기준 추가 포트(8090+)로 노출되거나, 운영자가 compose에 서비스를 추가해 **같은 Docker 네트워크**에 편입하는 식으로 확장한다(어느 쪽이든 LLM·`ToolClient`가 잡는 URL만 일관되면 된다).

---

## 컴포넌트

### Agent Server (Spring Boot :8080)

| 패키지        | 파일                     | 역할                                                    |
| ------------- | ------------------------ | ------------------------------------------------------- |
| `controller/` | `AgentController`        | SSE 엔드포인트 `POST /api/agent/run`                    |
| `controller/` | `ToolCreatorController`  | SSE 엔드포인트 `POST /api/tool-creator/create`          |
| `controller/` | `ToolsController`      | 도구 목록/헬스/프롬프트 조회·저장 API                    |
| `service/`    | `AgentService`           | Agent 루프 (최대 5회), system-prompt.txt 매 요청 재로드 |
| `service/`    | `ToolCreatorService`     | 메타 에이전트: 코드 생성, 파일 저장, 프로세스 시작      |
| `service/`    | `ToolRegistryService`    | `[사용 가능한 도구]` 구간에서 `N. 제목` 또는 `- 제목` 블록을 파싱해 도구 목록·미리보기 제공 |
| `client/`     | `OpenAiClient`           | OpenAI chat completion 호출                             |
| `client/`     | `ToolClient`             | LLM이 지정한 URL로 HTTP POST                            |
| `dto/`        | `AgentEvent`             | SSE 이벤트 (step / final / error / form)                |
| `dto/`        | `LlmResponse`            | Agent LLM 응답 파싱                                     |
| `dto/`        | `ToolCreatorLlmResponse` | Tool Creator LLM 응답 파싱                              |
| `config/`     | `WebConfig`              | CORS                                                    |

**Static UI:** `src/main/resources/static/index.html`

### Tool Server (FastAPI, 독립 프로세스)

| 도구      | 포트  | 파일              | 외부 API         |
| --------- | ----- | ----------------- | ---------------- |
| 난수 생성 | 8081  | `random_app.py`   | 없음             |
| 환율 변환 | 8082  | `currency_app.py` | ExchangeRate-API |
| 날씨 조회 | 8083  | `weather_app.py`  | OpenWeatherMap   |
| 동적 추가 | 8090+ | `{name}_app.py`   | 도구에 따라 다름 |

모든 Tool은 아래 엔드포인트를 제공한다.

- `POST /execute` (필수): 실제 도구 실행
- `GET /health` (필수): 기동 확인용(간단 JSON). Tool Creator는 이 엔드포인트로 **추가=활성화**를 검증한다.

---

## Agent 루프

최대 5회 반복:

1. `system-prompt.txt`를 파일에서 읽어 messages 구성 (파일 없으면 classpath fallback)
2. **현재 시각(KST) 컨텍스트**를 system 메시지로 주입(연도 생략 날짜 해석 안정화)
3. OpenAI 호출 → JSON 파싱 (마크다운 코드블록 제거 후 파싱)
4. `action=call` → LLM이 반환한 URL로 ToolClient HTTP POST → 결과를 대화에 추가 → SSE `step` → 반복
5. `action=final_answer` → SSE `final` → 종료
6. 파싱 실패 / Tool 오류 / 5회 초과 → SSE `error` → 종료

---

## Tool Creator 흐름

1. 사용자가 "기능 설명" 입력 → `POST /api/tool-creator/create`
2. LLM이 `need_info` 반환 → `form` SSE 이벤트로 질문 목록 전송
3. 브라우저가 입력 폼 렌더링 → 사용자가 API 키 등 입력 → 동일 엔드포인트에 answers 포함 재전송
4. LLM이 `create_tool` 반환:
   - `{name}_app.py` 생성 (`tool-server/` 디렉터리)
   - `.env.local`에 환경변수 append
   - `system-prompt.txt`의 `[응답 형식]` 앞에 도구 항목 삽입
   - `python -m uvicorn {name}_app:app --host 0.0.0.0 --port {port}` 프로세스 시작 (포트: 8090부터 순차 할당)
   - `GET http://localhost:{port}/health`를 짧게 폴링하여 **2xx 응답이 나올 때까지 대기**
5. **health 확인 성공 시에만** SSE `final` 이벤트로 완료 알림
6. health 확인 실패/프로세스 시작 실패 시 `error`로 종료 (프롬프트에 등록만 되고 “살아있지 않은 도구”가 생기지 않도록)

### Tool Creator — 기존 파일 수정(`update_files`)

LLM이 아래 형태로 응답하면, 허용된 경로의 파일을 덮어쓴다.

```json
{
  "action": "update_files",
  "files": [
    { "path": "system-prompt.txt", "content": "..." }
  ]
}
```

허용 경로는 서버에서 화이트리스트로 제한한다(보안).

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
  "questions": [
    {
      "key": "PROVIDER",
      "label": "어떤 데이터 제공사를 사용할까요?",
      "help": "제공사 이름을 입력하세요.",
      "link": "",
      "where_used": "외부 API Base URL/엔드포인트 선택에 사용"
    },
    {
      "key": "ALPHAVANTAGE_API_KEY",
      "label": "Alpha Vantage API 키를 입력해주세요",
      "help": "Alpha Vantage 사이트에서 API Key 발급",
      "link": "https://www.alphavantage.co/support/#api-key",
      "where_used": "외부 API 호출 시 쿼리스트링 apikey=... 로 사용"
    }
  ]
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

| 이벤트  | 시점                              | payload                                                        |
| ------- | --------------------------------- | -------------------------------------------------------------- |
| `step`  | 각 실행 단계                      | `{ "message": "...", "dt_ms": 123, "total_ms": 456 }`          |
| `final` | 최종 답변                         | `{ "message": "...", "dt_ms": 123, "total_ms": 456 }`          |
| `error` | 파싱 실패 / 호출 실패 / 루프 초과 | `{ "message": "...", "dt_ms": 123, "total_ms": 456 }`          |
| `form`  | Tool Creator — 추가 정보 요청     | `{ "message": "{\"tool_name\":\"...\",\"questions\":[...]}" }` |

---

## 환경변수

```env.local
OPENAI_API_KEY=
EXCHANGERATE_API_KEY=
OPENWEATHERMAP_API_KEY=
TOOL_SERVER_URL=          # 기본값 http://localhost:8081 (Docker: http://tool-server:8081)
```

동적 도구 추가 시 해당 키는 `.env.local`에 저장.

---

## Web UI (현재 구현)

- **도구 목록/상태**: `GET /api/tools`, `GET /api/tools/health`를 사용해 좌측 패널에 표시(주기적 polling)
- **프롬프트 편집**: `GET/PUT /api/tools/prompt`로 `system-prompt.txt`를 직접 수정/저장 가능(“프롬프트만 바꿔 동작이 달라지는 것” 체감)
- **도구 추가 완료 후 목록 갱신**: Tool Creator `final` 이후 자동 refresh

---

## 배포·환경 참고

- **외부 API TLS**: 일부 네트워크(기업 프록시, Windows schannel 정책 등)에서는 OpenWeather 등 HTTPS 호출이 로컬에서만 실패할 수 있다. 이 경우 앱 버그가 아니라 **실행 환경의 TLS/프록시** 이슈로 먼저 구분한다.
- **EC2·Docker**: 동일 코드가 공인망/리눅스 컨테이너에서 정상 동작하는지로 교차 확인하는 것이 빠르다.

---

## 향후 확장

- Web UI → React + Vercel 분리 배포 (SSE 엔드포인트 변경 없음)
- Tool Creator 생성 도구 → Docker Compose에 서비스로 추가
- 포트 할당 → 파일 기반 영속화 (현재 in-memory, 재시작 시 8090부터 재할당)
