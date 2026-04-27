package theo.demoagent;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import theo.demoagent.client.OpenAiClient;
import theo.demoagent.client.ToolClient;

@SpringBootTest
class DemoAgentApplicationTests {

    @MockitoBean
    OpenAiClient openAiClient;

    @MockitoBean
    ToolClient toolClient;

    @Test
    void contextLoads() {
    }
}
