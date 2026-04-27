package theo.demoagent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import theo.demoagent.client.OpenAiClient;
import theo.demoagent.dto.AgentEvent;
import theo.demoagent.dto.ToolCreatorLlmResponse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

@Service
public class ToolCreatorService {

    private final OpenAiClient openAiClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AtomicInteger nextPort = new AtomicInteger(8090);

    @Value("${user.dir}")
    private String baseDir;

    public ToolCreatorService(OpenAiClient openAiClient) {
        this.openAiClient = openAiClient;
    }

    public void create(String description, Map<String, String> answers, Consumer<AgentEvent> emit) {
        String creatorPrompt = loadCreatorPrompt();

        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", creatorPrompt));
        messages.add(Map.of("role", "user", "content", buildUserMessage(description, answers)));

        emit.accept(AgentEvent.step("요청 분석 중..."));

        String content;
        try {
            content = openAiClient.chat(messages);
        } catch (Exception e) {
            emit.accept(AgentEvent.error("LLM 호출 실패: " + e.getMessage()));
            return;
        }

        ToolCreatorLlmResponse response;
        try {
            String json = content.trim().replaceAll("(?s)^```\\w*\\n?", "").replaceAll("```\\s*$", "").trim();
            response = objectMapper.readValue(json, ToolCreatorLlmResponse.class);
        } catch (Exception e) {
            emit.accept(AgentEvent.error("응답 파싱 실패: " + content));
            return;
        }

        if ("need_info".equals(response.action())) {
            try {
                String questionsJson = objectMapper.writeValueAsString(
                        Map.of("tool_name", response.toolName(), "questions", response.questions())
                );
                emit.accept(AgentEvent.form(questionsJson));
            } catch (Exception e) {
                emit.accept(AgentEvent.error("질문 생성 실패"));
            }
            return;
        }

        if ("create_tool".equals(response.action())) {
            int port = nextPort.getAndIncrement();

            emit.accept(AgentEvent.step("코드 파일 저장 중..."));
            if (!writeToolFile(response.toolName(), response.code(), emit)) return;

            emit.accept(AgentEvent.step("환경 변수 저장 중..."));
            updateEnvFile(response.envVars(), emit);

            emit.accept(AgentEvent.step("시스템 프롬프트 업데이트 중..."));
            updateSystemPrompt(response.promptEntry(), port, emit);

            emit.accept(AgentEvent.step("도구 서버 시작 중..."));
            startToolProcess(response.toolName(), port, emit);

            emit.accept(AgentEvent.finalAnswer(
                    response.toolName() + " 도구가 포트 " + port + "에 추가되었습니다. 이제 에이전트에게 질문해보세요!"
            ));
        }
    }

    private String buildUserMessage(String description, Map<String, String> answers) {
        StringBuilder sb = new StringBuilder("기능 설명: ").append(description);
        if (!answers.isEmpty()) {
            sb.append("\n\n제공된 정보:");
            answers.forEach((k, v) -> sb.append("\n- ").append(k).append(": ").append(v));
        }
        return sb.toString();
    }

    private boolean writeToolFile(String toolName, String code, Consumer<AgentEvent> emit) {
        try {
            Path file = Path.of(baseDir, "tool-server", toolName + "_app.py");
            Files.writeString(file, code, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            return true;
        } catch (IOException e) {
            emit.accept(AgentEvent.error("파일 저장 실패: " + e.getMessage()));
            return false;
        }
    }

    private void updateEnvFile(Map<String, String> envVars, Consumer<AgentEvent> emit) {
        if (envVars == null || envVars.isEmpty()) return;
        try {
            Path env = Path.of(baseDir, ".env.local");
            StringBuilder sb = new StringBuilder();
            envVars.forEach((k, v) -> sb.append("\n").append(k).append("=").append(v));
            Files.writeString(env, sb.toString(), StandardOpenOption.APPEND, StandardOpenOption.CREATE);
        } catch (IOException e) {
            emit.accept(AgentEvent.step("환경 변수 저장 실패 (무시 가능): " + e.getMessage()));
        }
    }

    private void updateSystemPrompt(String promptEntry, int port, Consumer<AgentEvent> emit) {
        try {
            Path promptPath = Path.of(baseDir, "src/main/resources/system-prompt.txt");
            String current = Files.readString(promptPath);
            String entry = "\n" + promptEntry.replace("{PORT}", String.valueOf(port));

            int insertIdx = current.indexOf("[응답 형식]");
            String updated = insertIdx >= 0
                    ? current.substring(0, insertIdx) + entry + "\n\n" + current.substring(insertIdx)
                    : current + entry;

            Files.writeString(promptPath, updated);
        } catch (IOException e) {
            emit.accept(AgentEvent.step("프롬프트 업데이트 실패 (무시 가능): " + e.getMessage()));
        }
    }

    private void startToolProcess(String toolName, int port, Consumer<AgentEvent> emit) {
        try {
            Path toolDir = Path.of(baseDir, "tool-server");
            String uvicorn = toolDir.resolve(".venv/Scripts/uvicorn").toString();

            new ProcessBuilder(uvicorn, toolName + "_app:app", "--host", "0.0.0.0", "--port", String.valueOf(port))
                    .directory(toolDir.toFile())
                    .redirectErrorStream(true)
                    .start();

            Thread.sleep(1000);
            emit.accept(AgentEvent.step("서버 시작 완료 → http://localhost:" + port + "/execute"));
        } catch (Exception e) {
            emit.accept(AgentEvent.step(
                    "자동 시작 실패. 수동 실행: cd tool-server && uvicorn " + toolName + "_app:app --port " + port
            ));
        }
    }

    private String loadCreatorPrompt() {
        try {
            Path path = Path.of(baseDir, "src/main/resources/system-prompt-creator.txt");
            if (Files.exists(path)) return Files.readString(path);
            var resource = new org.springframework.core.io.ClassPathResource("system-prompt-creator.txt");
            return new String(resource.getInputStream().readAllBytes());
        } catch (IOException e) {
            throw new RuntimeException("system-prompt-creator.txt를 찾을 수 없습니다.", e);
        }
    }
}
