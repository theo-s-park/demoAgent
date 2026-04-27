package theo.demoagent.client;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Component
public class ToolClient {

    public String call(String url, Map<String, Object> args) {
        return RestClient.create()
                .post()
                .uri(url)
                .contentType(MediaType.APPLICATION_JSON)
                .body(args)
                .retrieve()
                .body(String.class);
    }
}
