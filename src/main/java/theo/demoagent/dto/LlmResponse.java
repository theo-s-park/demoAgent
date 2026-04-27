package theo.demoagent.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record LlmResponse(String action, String tool, Map<String, Object> args, String answer) {}
