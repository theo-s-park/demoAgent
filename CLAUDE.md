# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

```bash
# Build (skip tests)
./gradlew build -x test

# Run application
./gradlew bootRun

# Run all tests
./gradlew test

# Run a single test class
./gradlew test --tests "theo.demoagent.service.AgentServiceTest"

# Run a single test method
./gradlew test --tests "theo.demoagent.service.AgentServiceTest.finalAnswerOnFirstCall"

# Run everything (Docker Compose)
cp .env.example .env   # fill in your API keys
docker-compose up --build
```

On Windows use `gradlew.bat` instead of `./gradlew`.

## Tech Stack

- **Java 25**, **Spring Boot 4.0.6**, Gradle 9
- **Spring Web MVC** (`spring-boot-starter-webmvc`) — REST + SSE
- **Jackson** (`jackson-databind`) — JSON parsing
- **RestClient** (Spring 6.1+) — HTTP calls to OpenAI and Tool Server
- **Lombok** — boilerplate reduction
- **FastAPI** (Python 3.11) — standalone Tool Server
- **Docker Compose** — runs Agent Server (:8080) + Tool Server (:8081) together

## Architecture

```
Browser (index.html)
  └── POST /api/agent/run  →  AgentController (SSE stream)
        └── AgentService (agent loop, max 5 iterations)
              ├── OpenAiClient  →  OpenAI API (gpt-4o)
              └── ToolClient   →  Tool Server (FastAPI :8081)
                                    ├── POST /tools/random
                                    ├── POST /tools/currency  →  ExchangeRate-API
                                    └── POST /tools/weather   →  OpenWeatherMap
```

**Agent loop:** LLM returns JSON with `action: call` (dispatch tool) or `action: final_answer` (stream result). Each step is pushed to the browser via SSE (`event: step | final | error`). The browser uses `fetch()` + `ReadableStream` to consume SSE from a POST response (not `EventSource`, which only supports GET).

## Package Structure

```
theo.demoagent
├── controller/   AgentController — SSE endpoint
├── service/      AgentService   — agent loop logic
├── client/       OpenAiClient, ToolClient — HTTP clients
├── dto/          AgentRequest, AgentEvent, LlmResponse
└── config/       WebConfig — CORS
```

```
tool-server/
├── main.py        FastAPI app with 3 tool endpoints
└── requirements.txt
```

Static Web UI: `src/main/resources/static/index.html`

## Environment Variables

Copy `.env.example` → `.env` and fill in:

```
OPENAI_API_KEY=
EXCHANGERATE_API_KEY=        # from exchangerate-api.com
OPENWEATHERMAP_API_KEY=      # from openweathermap.org
```

`TOOL_SERVER_URL` defaults to `http://localhost:8081` (local) or `http://tool-server:8081` (Docker Compose, injected automatically).

## Key Constraints

- `gradle.properties` sets `org.gradle.java.home` to BellSoft JDK 25 — don't change this, the system `JAVA_HOME` points to JDK 8.
- `AgentService` creates its own `ObjectMapper` — `spring-boot-starter-webmvc` in Spring Boot 4.0 does not auto-configure `ObjectMapper` as a bean.
- `DemoAgentApplicationTests` uses `@MockitoBean` on `OpenAiClient` and `ToolClient` to prevent context loading from attempting real HTTP connections.
- The LLM response may wrap JSON in markdown code blocks (` ```json ... ``` `); `AgentService.parseLlmResponse()` strips these before parsing.
