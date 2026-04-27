package theo.demoagent.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ToolFileUpdate(String path, String content) {}

