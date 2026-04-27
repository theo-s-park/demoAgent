package theo.demoagent.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import theo.demoagent.dto.AgentEvent;
import theo.demoagent.service.DynamicToolManagerService;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

@RestController
@RequestMapping("/api/dynamic-tools")
public class DynamicToolsController {

    private final DynamicToolManagerService manager;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ExecutorService executor = Executors.newCachedThreadPool();

    public DynamicToolsController(DynamicToolManagerService manager) {
        this.manager = manager;
    }

    public record DeleteRequest(String tool_name) {}

    @PostMapping(value = "/delete", produces = MediaType.TEXT_EVENT_STREAM_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
    public SseEmitter delete(@RequestBody DeleteRequest request) {
        SseEmitter emitter = new SseEmitter(120_000L);

        executor.submit(() -> {
            try {
                long t0 = System.nanoTime();
                AtomicLong last = new AtomicLong(t0);

                manager.deleteByToolName(request == null ? null : request.tool_name(), (AgentEvent event) -> {
                    try {
                        long now = System.nanoTime();
                        long dtMs = (now - last.getAndSet(now)) / 1_000_000;
                        long totalMs = (now - t0) / 1_000_000;
                        emitter.send(SseEmitter.event()
                                .name(event.type())
                                .data(objectMapper.writeValueAsString(Map.of(
                                        "message", event.message(),
                                        "dt_ms", dtMs,
                                        "total_ms", totalMs
                                ))));
                    } catch (IOException e) {
                        emitter.completeWithError(e);
                    }
                });

                emitter.complete();
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }
}

