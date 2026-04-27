package theo.demoagent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import theo.demoagent.client.OpenAiClient;
import theo.demoagent.client.ToolClient;
import theo.demoagent.dto.AgentEvent;
import theo.demoagent.dto.LlmResponse;

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

    public AgentService(OpenAiClient openAiClient, ToolClient toolClient) {
        this.openAiClient = openAiClient;
        this.toolClient = toolClient;
        this.objectMapper = new ObjectMapper();
    }

    public void run(String question, Consumer<AgentEvent> emit) {
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", buildSystemPrompt()));
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
                emit.accept(AgentEvent.error("JSON 파싱 실패: " + content));
                return;
            }

            if ("final_answer".equals(response.action())) {
                emit.accept(AgentEvent.finalAnswer(response.answer()));
                return;
            }

            if ("call".equals(response.action())) {
                emit.accept(AgentEvent.step("Tool 호출: " + response.tool() + " " + response.args()));

                String toolResult;
                try {
                    toolResult = toolClient.call(response.tool(), response.args());
                } catch (Exception e) {
                    emit.accept(AgentEvent.error("Tool 호출 실패 (" + response.tool() + "): " + e.getMessage()));
                    return;
                }

                emit.accept(AgentEvent.step("Tool 결과: " + toolResult));
                messages.add(Map.of("role", "user", "content", "Tool '" + response.tool() + "' result: " + toolResult));
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

    private String buildSystemPrompt() {
        return """
                You are an AI assistant that can call external tools to answer questions.

                When you need to call a tool, respond with ONLY valid JSON in this exact format (no markdown, no extra text):
                {"action":"call","tool":"<tool_name>","args":{<key>:<value>}}

                When you have enough information to answer, respond with ONLY valid JSON in this exact format:
                {"action":"final_answer","answer":"<your complete answer>"}

                Available tools:
                - "random": Returns a random integer. Args: {"min_val": <integer>, "max_val": <integer>}
                - "currency": Converts currency. Args: {"amount": <number>, "from": "<ISO_currency_code>", "to": "<ISO_currency_code>"}
                - "weather": Returns current weather. Args: {"lat": <number>, "lon": <number>}

                Never include markdown code blocks or any text outside the JSON object.
                """;
    }
}
