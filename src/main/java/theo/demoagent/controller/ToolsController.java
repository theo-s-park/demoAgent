package theo.demoagent.controller;

import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import theo.demoagent.client.OpenAiClient;
import theo.demoagent.dto.ToolHealthStatus;
import theo.demoagent.dto.ToolInfo;
import theo.demoagent.service.ToolRegistryService;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/tools")
public class ToolsController {

    private final ToolRegistryService toolRegistryService;
    private final OpenAiClient openAiClient;

    public ToolsController(ToolRegistryService toolRegistryService, OpenAiClient openAiClient) {
        this.toolRegistryService = toolRegistryService;
        this.openAiClient = openAiClient;
    }

    @GetMapping(value = "", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<ToolInfo> list() {
        return toolRegistryService.listTools();
    }

    @GetMapping(value = "/prompt", produces = MediaType.TEXT_PLAIN_VALUE)
    public String prompt() {
        return toolRegistryService.loadSystemPromptRaw();
    }

    @PutMapping(value = "/prompt", consumes = MediaType.TEXT_PLAIN_VALUE, produces = MediaType.TEXT_PLAIN_VALUE)
    public String updatePrompt(@RequestBody String content) throws IOException {
        return toolRegistryService.updateSystemPrompt(content);
    }

    @PostMapping(value = "/prompt/patch", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.TEXT_PLAIN_VALUE)
    public String patchPrompt(@RequestBody Map<String, String> body) throws IOException {
        String instruction = body.getOrDefault("instruction", "").trim();
        if (instruction.isBlank()) throw new IllegalArgumentException("instruction이 비어있습니다.");

        String currentPrompt = toolRegistryService.loadSystemPromptRaw();

        String systemMsg = "당신은 시스템 프롬프트 편집 전문가입니다. " +
                "사용자의 수정 지시에 따라 현재 시스템 프롬프트를 정확히 수정한 뒤, 수정된 전체 프롬프트 텍스트만 반환하세요. " +
                "반드시 지켜야 할 규칙:\n" +
                "1. 원본의 줄바꿈, 들여쓰기, 공백을 그대로 유지할 것\n" +
                "2. 도구 블록(URL/인자/설명) 구조를 변경하지 말 것\n" +
                "3. 마크다운, 코드블록, 설명, 추가 텍스트 일절 금지\n" +
                "4. 수정된 프롬프트 원문만 출력할 것";
        String userMsg = "[현재 프롬프트]\n" + currentPrompt + "\n\n[수정 지시]\n" + instruction;

        String patched = openAiClient.chat(List.of(
                Map.of("role", "system", "content", systemMsg),
                Map.of("role", "user", "content", userMsg)
        ));

        return toolRegistryService.updateSystemPrompt(patched);
    }

    @GetMapping(value = "/health", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<ToolHealthStatus> health() {
        List<ToolInfo> tools = toolRegistryService.listTools();
        List<ToolHealthStatus> out = new ArrayList<>();
        // Use HttpURLConnection (HTTP/1.1) to avoid accidental Upgrade/WS warnings in uvicorn logs.
        SimpleClientHttpRequestFactory rf = new SimpleClientHttpRequestFactory();
        rf.setConnectTimeout(1500);
        rf.setReadTimeout(2000);
        RestClient client = RestClient.builder().requestFactory(rf).build();

        for (ToolInfo t : tools) {
            if (t.url() == null || t.url().isBlank()) {
                out.add(new ToolHealthStatus(t.id(), null, false, 0, 0, "Missing URL in prompt"));
                continue;
            }
            long start = System.currentTimeMillis();
            try {
                // Prefer GET /health if implemented.
                int status = 0;
                try {
                    status = client.get().uri(t.url().replace("/execute", "/health")).retrieve().toBodilessEntity().getStatusCode().value();
                } catch (Exception ignored) {
                    // If /health is missing, report "unknown" instead of calling /execute with invalid payload (422).
                    long latency = System.currentTimeMillis() - start;
                    out.add(new ToolHealthStatus(t.id(), t.url(), true, 404, latency, "No /health endpoint"));
                    continue;
                }
                long latency = System.currentTimeMillis() - start;
                out.add(new ToolHealthStatus(t.id(), t.url(), status >= 200 && status < 300, status, latency, null));
            } catch (Exception e) {
                long latency = System.currentTimeMillis() - start;
                out.add(new ToolHealthStatus(t.id(), t.url(), false, 0, latency, e.getMessage()));
            }
        }
        return out;
    }
}

