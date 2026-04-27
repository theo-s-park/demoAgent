package theo.demoagent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import theo.demoagent.client.OpenAiClient;
import theo.demoagent.client.ToolClient;
import theo.demoagent.dto.AgentEvent;
import theo.demoagent.dto.LlmResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@Service
public class AgentService {

    private static final int MAX_ITERATIONS = 5;

    private final OpenAiClient openAiClient;
    private final ToolClient toolClient;
    private final ObjectMapper objectMapper;

    @org.springframework.beans.factory.annotation.Value("${user.dir}")
    private String baseDir;

    public AgentService(OpenAiClient openAiClient, ToolClient toolClient) {
        this.openAiClient = openAiClient;
        this.toolClient = toolClient;
        this.objectMapper = new ObjectMapper();
    }

    public void run(String question, Consumer<AgentEvent> emit) {
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", loadSystemPrompt()));
        messages.add(Map.of(
                "role", "system",
                "content", "현재 날짜/시간(KST, ISO-8601): " + OffsetDateTime.now(java.time.ZoneOffset.ofHours(9)) +
                        "\n사용자가 연도를 생략한 날짜를 말하면, 올해(현재 연도)를 기준으로 해석하세요."
        ));
        messages.add(Map.of("role", "user", "content", question));

        for (int i = 0; i < MAX_ITERATIONS; i++) {
            emit.accept(AgentEvent.step("LLM 호출 중... (" + (i + 1) + "/" + MAX_ITERATIONS + ")"));

            String content;
            try {
                content = openAiClient.chat(messages);
            } catch (Exception e) {
                emit.accept(AgentEvent.error("LLM 호출 실패: " + e.getMessage()));
                return;
            }

            messages.add(Map.of("role", "assistant", "content", content));

            LlmResponse response;
            try {
                response = parseLlmResponse(content);
            } catch (Exception e) {
                emit.accept(AgentEvent.error("JSON 파싱 실패: " + e.getMessage() + " | raw=" + content));
                return;
            }

            if (response.thought() != null && !response.thought().isBlank()) {
                emit.accept(AgentEvent.thought(response.thought()));
            }

            if ("final_answer".equals(response.action())) {
                emit.accept(AgentEvent.finalAnswer(response.answer()));
                return;
            }

            if ("call".equals(response.action())) {
                emit.accept(AgentEvent.step("Tool 호출: " + response.url() + " " + response.args()));

                String toolResult;
                try {
                    toolResult = toolClient.call(response.url(), response.args());
                } catch (Exception e) {
                    emit.accept(AgentEvent.error("Tool 호출 실패 (" + response.url() + "): " + e.getMessage()));
                    return;
                }

                emit.accept(AgentEvent.step("Tool 결과: " + toolResult));
                messages.add(Map.of("role", "user", "content", "Tool result: " + toolResult));
            } else {
                emit.accept(AgentEvent.error("알 수 없는 action: " + response.action()));
                return;
            }
        }

        emit.accept(AgentEvent.error("최대 반복 횟수(" + MAX_ITERATIONS + "회)를 초과했습니다."));
    }

    private LlmResponse parseLlmResponse(String content) throws Exception {
        String json = content.trim();
        if (json.startsWith("```")) {
            json = json.replaceAll("(?s)^```\\w*\\n?", "").replaceAll("```\\s*$", "").trim();
        }
        return objectMapper.readValue(json, LlmResponse.class);
    }

    private String loadSystemPrompt() {
        if (baseDir != null) {
            Path rootPrompt = Path.of(baseDir, "system-prompt.txt");
            if (Files.exists(rootPrompt)) {
                try {
                    return Files.readString(rootPrompt);
                } catch (IOException ignored) {}
            }

            Path devPrompt = Path.of(baseDir, "src/main/resources/system-prompt.txt");
            if (Files.exists(devPrompt)) {
                try {
                    return Files.readString(devPrompt);
                } catch (IOException ignored) {}
            }
        }
        try {
            ClassPathResource resource = new ClassPathResource("system-prompt.txt");
            return new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("system-prompt.txt를 찾을 수 없습니다.", e);
        }
    }
}
