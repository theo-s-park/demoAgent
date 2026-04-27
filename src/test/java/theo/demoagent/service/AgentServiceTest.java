package theo.demoagent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import theo.demoagent.client.OpenAiClient;
import theo.demoagent.client.ToolClient;
import theo.demoagent.dto.AgentEvent;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentServiceTest {

    @Mock
    OpenAiClient openAiClient;

    @Mock
    ToolClient toolClient;

    @Test
    void finalAnswerOnFirstCall() {
        when(openAiClient.chat(any())).thenReturn("{\"action\":\"final_answer\",\"answer\":\"42\"}");

        List<AgentEvent> events = collect("6 x 7?");

        assertThat(last(events).type()).isEqualTo("final");
        assertThat(last(events).message()).isEqualTo("42");
    }

    @Test
    void toolCallThenFinalAnswer() {
        when(openAiClient.chat(any()))
                .thenReturn("{\"action\":\"call\",\"tool\":\"random\",\"args\":{\"min_val\":1,\"max_val\":10}}")
                .thenReturn("{\"action\":\"final_answer\",\"answer\":\"The number is 7\"}");
        when(toolClient.call(eq("random"), any())).thenReturn("{\"value\":7}");

        List<AgentEvent> events = collect("random number 1 to 10");

        assertThat(last(events).type()).isEqualTo("final");
        assertThat(last(events).message()).isEqualTo("The number is 7");
    }

    @Test
    void maxIterationsExceeded() {
        when(openAiClient.chat(any()))
                .thenReturn("{\"action\":\"call\",\"tool\":\"random\",\"args\":{\"min_val\":1,\"max_val\":10}}");
        when(toolClient.call(any(), any())).thenReturn("{\"value\":5}");

        List<AgentEvent> events = collect("question");

        assertThat(last(events).type()).isEqualTo("error");
        assertThat(last(events).message()).contains("최대 반복");
    }

    @Test
    void jsonParseFailure() {
        when(openAiClient.chat(any())).thenReturn("this is not json");

        List<AgentEvent> events = collect("question");

        assertThat(last(events).type()).isEqualTo("error");
        assertThat(last(events).message()).contains("JSON 파싱 실패");
    }

    @Test
    void toolCallFailure() {
        when(openAiClient.chat(any()))
                .thenReturn("{\"action\":\"call\",\"tool\":\"weather\",\"args\":{\"lat\":37.5,\"lon\":127.0}}");
        when(toolClient.call(any(), any())).thenThrow(new RuntimeException("Connection refused"));

        List<AgentEvent> events = collect("weather?");

        assertThat(last(events).type()).isEqualTo("error");
        assertThat(last(events).message()).contains("Tool 호출 실패");
    }

    @Test
    void markdownWrappedJsonIsParsed() {
        when(openAiClient.chat(any())).thenReturn(
                "```json\n{\"action\":\"final_answer\",\"answer\":\"parsed\"}\n```"
        );

        List<AgentEvent> events = collect("question");

        assertThat(last(events).type()).isEqualTo("final");
        assertThat(last(events).message()).isEqualTo("parsed");
    }

    private List<AgentEvent> collect(String question) {
        AgentService service = new AgentService(openAiClient, toolClient);
        List<AgentEvent> events = new ArrayList<>();
        service.run(question, events::add);
        return events;
    }

    private AgentEvent last(List<AgentEvent> events) {
        return events.get(events.size() - 1);
    }
}
