package theo.demoagent.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ToolCreatorLlmResponse(
        String action,
        @JsonProperty("tool_name") String toolName,
        JsonNode questions,
        String code,
        @JsonProperty("env_vars") Map<String, String> envVars,
        @JsonProperty("prompt_entry") String promptEntry,
        List<ToolFileUpdate> files
) {}

