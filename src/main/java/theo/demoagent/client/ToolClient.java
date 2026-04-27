package theo.demoagent.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Component
public class ToolClient {

    private final RestClient restClient;

    public ToolClient(@Value("${tool.server.url}") String toolServerUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(toolServerUrl)
                .build();
    }

    public String call(String toolName, Map<String, Object> args) {
        return restClient.post()
                .uri("/tools/" + toolName)
                .contentType(MediaType.APPLICATION_JSON)
                .body(args)
                .retrieve()
                .body(String.class);
    }
}
