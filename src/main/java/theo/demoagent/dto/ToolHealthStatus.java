package theo.demoagent.dto;

public record ToolHealthStatus(
        String id,
        String url,
        boolean ok,
        int status,
        long latencyMs,
        String error
) {}

