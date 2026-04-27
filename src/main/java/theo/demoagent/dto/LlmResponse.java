package theo.demoagent.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record LlmResponse(String action, String url, Map<String, Object> args, String answer) {}
