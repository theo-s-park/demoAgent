package theo.demoagent.dto;

public record AgentEvent(String type, String message) {

    public static AgentEvent step(String message) {
        return new AgentEvent("step", message);
    }

    public static AgentEvent finalAnswer(String answer) {
        return new AgentEvent("final", answer);
    }

    public static AgentEvent error(String message) {
        return new AgentEvent("error", message);
    }

    public static AgentEvent form(String questionsJson) {
        return new AgentEvent("form", questionsJson);
    }
}
