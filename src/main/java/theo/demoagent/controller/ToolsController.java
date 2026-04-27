package theo.demoagent.controller;

import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import theo.demoagent.dto.ToolHealthStatus;
import theo.demoagent.dto.ToolInfo;
import theo.demoagent.service.ToolRegistryService;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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

    @PutMapping(value = "/prompt", consumes = MediaType.TEXT_PLAIN_VALUE, produces = MediaType.TEXT_PLAIN_VALUE)
    public String updatePrompt(@RequestBody String content) throws IOException {
        return toolRegistryService.updateSystemPrompt(content);
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

