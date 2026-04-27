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

            LlmResponse response = parseLlmResponse(content);

            if (response.thought() != null && !response.thought().isBlank()) {
                emit.accept(AgentEvent.thought(response.thought()));
            }

            if ("final_answer".equals(response.action())) {
                emit.accept(AgentEvent.finalAnswer(response.answer()));
                return;
            }

            if ("call".equals(response.action())) {
                String url = response.url();
                if (url == null || url.isBlank()) {
                    messages.add(Map.of("role", "user", "content", "Tool error: url이 없습니다. 올바른 도구 URL을 지정해주세요."));
                    continue;
                }

                String prettyArgs;
                try {
                    prettyArgs = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(response.args());
                } catch (Exception e) {
                    prettyArgs = String.valueOf(response.args());
                }
                emit.accept(AgentEvent.step("Tool 호출: " + url + "\n" + prettyArgs));

                String toolResult;
                try {
                    toolResult = toolClient.call(url, response.args());
                } catch (Exception e) {
                    emit.accept(AgentEvent.step("Tool 호출 실패 (" + url + "): " + e.getMessage()));
                    messages.add(Map.of("role", "user", "content",
                            "Tool error: " + e.getMessage() + ". 도구 없이 답변하거나 다른 방법을 시도해주세요."));
                    continue;
                }

                emit.accept(AgentEvent.step("Tool 결과: " + toolResult));
                messages.add(Map.of("role", "user", "content", "Tool result: " + toolResult));
            } else {
                // 알 수 없는 action은 텍스트 응답으로 간주해 최종 답변 처리
                String answer = response.answer() != null ? response.answer() : content.trim();
                emit.accept(AgentEvent.finalAnswer(answer));
                return;
            }
        }

        emit.accept(AgentEvent.error("최대 반복 횟수(" + MAX_ITERATIONS + "회)를 초과했습니다."));
    }

    private LlmResponse parseLlmResponse(String content) {
        String text = content.trim();

        // 1. 마크다운 코드블록 제거
        if (text.startsWith("```")) {
            text = text.replaceAll("(?s)^```\\w*\\n?", "").replaceAll("```\\s*$", "").trim();
        }

        // 2. 직접 파싱 시도
        try {
            return objectMapper.readValue(text, LlmResponse.class);
        } catch (Exception ignored) {}

        // 3. 텍스트 안에 섞인 JSON 추출 시도
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start != -1 && end > start) {
            try {
                return objectMapper.readValue(text.substring(start, end + 1), LlmResponse.class);
            } catch (Exception ignored) {}
        }

        // 4. 파싱 불가 → 전체 텍스트를 최종 답변으로 처리
        return new LlmResponse("final_answer", null, null, null, content.trim());
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
