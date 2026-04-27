package theo.demoagent;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import theo.demoagent.client.OpenAiClient;
import theo.demoagent.client.ToolClient;
import theo.demoagent.domain.DynamicToolRepository;

@SpringBootTest
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:sqlite::memory:",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
class DemoAgentApplicationTests {

    @MockitoBean OpenAiClient openAiClient;
    @MockitoBean ToolClient toolClient;
    @MockitoBean DynamicToolRepository dynamicToolRepository;

    @Test
    void contextLoads() {
    }
}
