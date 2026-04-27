# demoAgent

LLM이 출력한 JSON 지시문을 Agent 서버가 파싱하여 외부 Tool API를 호출하고, 결과를 다시 LLM에 전달해 최종 답변을 생성하는 데모 시스템

## 문서

| 문서 | 설명 |
|------|------|
| [요구사항](./requirements.md) | MVP 기능 요구사항 |
| [설계 문서](./docs/superpowers/specs/2026-04-27-demoagent-design.md) | 아키텍처, 컴포넌트, Agent 루프, API 스펙 |

## 다이어그램

### Architecture

![Architecture Diagram](./docs/superpowers/architecture-diagram.png)

- Excalidraw 소스: `docs/superpowers/architecture-demoagent.excalidraw`
- 재생성: `python scripts/build_architecture_excalidraw.py`

### Sequence diagrams

- 로컬에서 UI로 보기/복사: `http://localhost:8080/sequence.html`

아래 mermaid는 그대로 GitHub에서 렌더링되며, Mermaid 에디터에도 복사해 사용할 수 있습니다.

<details>
<summary>① Chat — Agent 실행(도구 호출 포함)</summary>

```mermaid
sequenceDiagram
autonumber
actor User as User
participant UI as Browser(UI)
participant Agent as Spring Agent Server
participant Prompt as Runtime State(system-prompt.txt)
participant LLM as External LLM API
participant Tool as Tool Server(/execute)

User->>UI: 질문 입력
UI->>Agent: POST /api/agent/run (SSE)
Agent->>Prompt: load system-prompt (tool list)

loop up to 5 iterations
  Agent->>LLM: request(messages + prompt)
  LLM-->>Agent: response(JSON action)

  alt action=call
    Agent->>Tool: POST /execute(args)
    Tool-->>Agent: result JSON
    Agent-->>UI: SSE step (tool call/result)
  else action=final_answer
    Agent-->>UI: SSE final(answer)
  else parse/tool error
    Agent-->>UI: SSE error
  end
end

UI-->>User: 답변 표시
```

</details>

<details>
<summary>② Add Tool — 도구 추가(키 입력 → 생성 → health gate → 영속화/복원)</summary>

```mermaid
sequenceDiagram
autonumber
actor User as User
participant UI as Browser(UI)
participant Creator as Spring ToolCreator API
participant LLM as External LLM API
participant FS as Runtime Files(tool-server/*.py, .env.local, system-prompt.txt)
participant Proc as uvicorn(:8090+)
participant DB as SQLite tools.db(dynamic_tool)

User->>UI: "도구 추가해줘" (설명 입력)
UI->>Creator: POST /api/tool-creator/create (SSE)

Creator->>LLM: request(description + answers)
LLM-->>Creator: need_info OR create_tool

alt need_info
  Creator-->>UI: SSE form(questions)
  UI-->>User: 폼 렌더링(키 입력 등)
  User->>UI: answers 입력
  UI->>Creator: POST /api/tool-creator/create (SSE, answers)
  Creator->>LLM: request(answers 포함)
  LLM-->>Creator: create_tool
end

Creator->>FS: write tool-server/{name}_app.py
Creator->>FS: append .env.local(env vars)
Creator->>FS: update system-prompt.txt(tool entry)

Creator->>Proc: spawn uvicorn {name}_app:app --port 8090+
loop health gate
  Creator->>Proc: GET /health
  Proc-->>Creator: 2xx ready
end

Creator->>DB: upsert dynamic_tool(tool_name, port, pid, created_at)
Creator-->>UI: SSE final(추가 완료)
UI-->>User: 도구 활성화 + 목록 갱신
```

</details>

<details>
<summary>③ Patch Prompt — 프롬프트 변경(패치/저장)</summary>

```mermaid
sequenceDiagram
autonumber
actor User as User
participant UI as Browser(UI)
participant Tools as Spring Tools API
participant FS as Runtime File(system-prompt.txt)
participant LLM as External LLM API

User->>UI: "프롬프트 변경" (지시/편집)

alt LLM patch (instruction 기반)
  UI->>Tools: POST /api/tools/prompt/patch {instruction}
  Tools->>FS: read system-prompt.txt
  Tools->>LLM: request(current + instruction)
  LLM-->>Tools: response(patched prompt)
  Tools->>FS: write system-prompt.txt(overwrite)
  Tools-->>UI: patched prompt(text)
else direct save (전체 저장)
  UI->>Tools: PUT /api/tools/prompt (full text)
  Tools->>FS: write system-prompt.txt(overwrite)
  Tools-->>UI: ok
end

UI-->>User: tool list refresh(프롬프트 기반)
```

</details>

## 로컬 실행

### Tool servers (local)

`tool-server/`에서 기본 제공 도구 서버 3개를 한 번에 실행:

Git Bash:

```bash
./run_all.sh
```

PowerShell:

```powershell
.\run_all.ps1
```

스크립트가 자동으로 `.venv`를 만들고(가능하면 `py -3.12` 사용), 의존성을 설치한 뒤 아래 서버를 실행합니다.
- `random` → `http://127.0.0.1:8081/execute`
- `currency` → `http://127.0.0.1:8082/execute`
- `weather` → `http://127.0.0.1:8083/execute`

### Agent server (local)

repo 루트에서:

```bash
./gradlew bootRun
```

브라우저에서 `http://localhost:8080` 접속.
