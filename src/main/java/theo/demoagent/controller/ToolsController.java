package theo.demoagent.controller;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import theo.demoagent.dto.ToolHealthStatus;
import theo.demoagent.dto.ToolInfo;
import theo.demoagent.service.ToolRegistryService;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/tools")
public class ToolsController {

    private final ToolRegistryService toolRegistryService;

    public ToolsController(ToolRegistryService toolRegistryService) {
        this.toolRegistryService = toolRegistryService;
    }

    @GetMapping(value = "", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<ToolInfo> list() {
        return toolRegistryService.listTools();
    }

    @GetMapping(value = "/prompt", produces = MediaType.TEXT_PLAIN_VALUE)
    public String prompt() {
        return toolRegistryService.loadSystemPromptRaw();
    }

    @GetMapping(value = "/health", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<ToolHealthStatus> health() {
        List<ToolInfo> tools = toolRegistryService.listTools();
        List<ToolHealthStatus> out = new ArrayList<>();
        RestClient client = RestClient.builder().build();

        for (ToolInfo t : tools) {
            if (t.url() == null || t.url().isBlank()) {
                out.add(new ToolHealthStatus(t.id(), null, false, 0, 0, "Missing URL in prompt"));
                continue;
            }
            long start = System.currentTimeMillis();
            try {
                // Prefer GET /health if implemented; otherwise a quick POST /execute with empty JSON.
                int status;
                try {
                    status = client.get().uri(t.url().replace("/execute", "/health")).retrieve().toBodilessEntity().getStatusCode().value();
                } catch (Exception ignored) {
                    status = client.post()
                            .uri(t.url())
                            .contentType(MediaType.APPLICATION_JSON)
                            .body("{}")
                            .retrieve()
                            .toBodilessEntity()
                            .getStatusCode()
                            .value();
                }
                long latency = System.currentTimeMillis() - start;
                out.add(new ToolHealthStatus(t.id(), t.url(), status >= 200 && status < 500, status, latency, null));
            } catch (Exception e) {
                long latency = System.currentTimeMillis() - start;
                out.add(new ToolHealthStatus(t.id(), t.url(), false, 0, latency, e.getMessage()));
            }
        }
        return out;
    }
}

