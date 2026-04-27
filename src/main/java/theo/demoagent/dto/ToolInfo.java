package theo.demoagent.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ToolInfo(
        String id,
        String name,
        String url,
        String args,
        String description,
        String promptBlock
) {}

