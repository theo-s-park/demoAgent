package theo.demoagent.client;

import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ToolClient {

    private final Environment env;
    private final Map<String, RestClient> clientCache = new ConcurrentHashMap<>();

    public ToolClient(Environment env) {
        this.env = env;
    }

    public String call(String toolName, Map<String, Object> args) {
        RestClient client = clientCache.computeIfAbsent(toolName, name -> {
            String url = env.getProperty("tool.urls." + name);
            if (url == null) throw new IllegalArgumentException("No URL configured for tool: " + name);
            return RestClient.builder().baseUrl(url).build();
        });

        return client.post()
                .uri("/execute")
                .contentType(MediaType.APPLICATION_JSON)
                .body(args)
                .retrieve()
                .body(String.class);
    }
}
