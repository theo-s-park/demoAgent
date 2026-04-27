package theo.demoagent.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import theo.demoagent.domain.DynamicTool;
import theo.demoagent.domain.DynamicToolRepository;
import theo.demoagent.dto.AgentEvent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.regex.Pattern;

@Service
public class DynamicToolManagerService {

    private static final Logger log = LoggerFactory.getLogger(DynamicToolManagerService.class);

    private final DynamicToolRepository repository;

    @Value("${user.dir}")
    private String baseDir;

    private static final Pattern SAFE_TOOL_NAME = Pattern.compile("^[a-zA-Z][a-zA-Z0-9_]{0,40}$");

    public DynamicToolManagerService(DynamicToolRepository repository) {
        this.repository = repository;
    }

    public void deleteByToolName(String toolName, Consumer<AgentEvent> emit) {
        String name = toolName == null ? "" : toolName.trim();
        if (!SAFE_TOOL_NAME.matcher(name).matches()) {
            log.warn("[tool-delete] unsafe toolName={}", name);
            emit.accept(AgentEvent.error("toolName이 안전한 형식이 아닙니다: " + name));
            return;
        }

        log.info("[tool-delete] start name={}", name);
        Optional<DynamicTool> opt = repository.findAll().stream()
                .filter(t -> name.equals(t.getToolName()))
                .findFirst();
        if (opt.isEmpty()) {
            log.info("[tool-delete] not found name={}", name);
            emit.accept(AgentEvent.error("동적 도구를 찾을 수 없습니다: " + name));
            return;
        }

        DynamicTool tool = opt.get();
        log.info("[tool-delete] found name={} port={} pid={}", tool.getToolName(), tool.getPort(), tool.getPid());
        emit.accept(AgentEvent.step("도구 프로세스 종료 중..."));
        stopProcess(tool.getPid(), emit);

        emit.accept(AgentEvent.step("system-prompt에서 도구 제거 중..."));
        tryRemovePromptEntry(name);

        emit.accept(AgentEvent.step("도구 코드 파일 제거 중..."));
        tryRemoveToolFile(name);

        emit.accept(AgentEvent.step("DB에서 도구 제거 중..."));
        repository.delete(tool);
        log.info("[tool-delete] deleted name={}", name);

        emit.accept(AgentEvent.finalAnswer("도구 삭제 완료: " + name));
    }

    private void stopProcess(Long pid, Consumer<AgentEvent> emit) {
        if (pid == null || pid <= 0) {
            emit.accept(AgentEvent.step("PID 정보가 없어 프로세스 종료를 건너뜁니다."));
            return;
        }
        try {
            ProcessHandle.of(pid).ifPresentOrElse(ph -> {
                boolean ok = ph.destroy();
                if (!ok) ph.destroyForcibly();
            }, () -> {});
            emit.accept(AgentEvent.step("프로세스 종료 요청 완료 (pid " + pid + ")"));
        } catch (Exception e) {
            emit.accept(AgentEvent.step("프로세스 종료 실패(무시 가능): " + e.getMessage()));
        }
    }

    private void tryRemoveToolFile(String toolName) {
        if (baseDir == null) return;
        try {
            Path file = Path.of(baseDir, "tool-server", toolName + "_app.py");
            Files.deleteIfExists(file);
        } catch (Exception ignored) {}
    }

    private void tryRemovePromptEntry(String toolName) {
        if (baseDir == null) return;
        Path promptPath = Path.of(baseDir, "system-prompt.txt");
        Path devPromptPath = Path.of(baseDir, "src/main/resources/system-prompt.txt");
        try {
            if (!Files.exists(promptPath)) return;
            String current = Files.readString(promptPath);
            String updated = removeDashedToolBlock(current, toolName);
            if (!updated.equals(current)) {
                Files.writeString(promptPath, updated, StandardOpenOption.TRUNCATE_EXISTING);
                if (Files.exists(devPromptPath)) {
                    Files.writeString(devPromptPath, updated, StandardOpenOption.TRUNCATE_EXISTING);
                }
            }
        } catch (IOException ignored) {}
    }

    static String removeDashedToolBlock(String prompt, String toolName) {
        if (prompt == null || prompt.isBlank()) return prompt;
        int start = prompt.indexOf("[사용 가능한 도구]");
        if (start < 0) return prompt;
        int end = prompt.indexOf("[응답 형식]", start);
        if (end < 0) return prompt;

        String before = prompt.substring(0, start);
        String section = prompt.substring(start, end);
        String after = prompt.substring(end);

        // Remove a block that starts with "- <toolName>" line.
        String[] lines = section.split("\n", -1);
        StringBuilder out = new StringBuilder();
        boolean skipping = false;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String trimmed = line.trim();

            if (!skipping && trimmed.equals("- " + toolName)) {
                skipping = true;
                continue;
            }

            if (skipping) {
                // Next block start: "N. xxx" or "- yyy"
                if (trimmed.matches("^\\d+\\.\\s+.+$") || trimmed.startsWith("- ")) {
                    skipping = false;
                    out.append(line).append("\n");
                }
                // else keep skipping lines inside the removed block
            } else {
                out.append(line).append("\n");
            }
        }

        return before + out.toString().replaceAll("\\n{3,}", "\n\n") + after;
    }
}

