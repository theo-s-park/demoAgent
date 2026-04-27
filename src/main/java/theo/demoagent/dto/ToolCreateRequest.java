package theo.demoagent.dto;

import java.util.Map;

public record ToolCreateRequest(String description, Map<String, String> answers) {}
