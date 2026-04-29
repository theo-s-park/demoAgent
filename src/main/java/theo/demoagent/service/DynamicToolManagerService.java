package theo.demoagent.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import theo.demoagent.domain.DynamicTool;
import theo.demoagent.domain.DynamicToolRepository;
import theo.demoagent.dto.AgentEvent;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class DynamicToolManagerService {

    private static final Logger log = LoggerFactory.getLogger(DynamicToolManagerService.class);

    private final DynamicToolRepository repository;
    private final ToolRegistryService toolRegistryService;

    @Value("${user.dir}")
    private String baseDir;

    public DynamicToolManagerService(DynamicToolRepository repository,
                                     ToolRegistryService toolRegistryService) {
        this.repository = repository;
        this.toolRegistryService = toolRegistryService;
    }

    /**
     * displayName: UI에서 넘어오는 한글 표시명 (예: "지하철 시간 조회")
     * 1) 시스템 프롬프트에서 해당 표시명 블록의 포트를 추출
     * 2) 포트로 DB에서 DynamicTool 조회
     * 3) 프로세스 종료 → 코드 파일 삭제 → 프롬프트 항목 삭제 → DB 삭제
     */
    public void deleteByToolName(String displayName, Consumer<AgentEvent> emit) {
        String name = displayName == null ? "" : displayName.trim();
        if (name.isBlank()) {
            emit.accept(AgentEvent.error("도구 이름이 비어있습니다."));
            return;
        }

        log.info("[tool-delete] start displayName={}", name);

        // 1. 시스템 프롬프트에서 포트 추출
        Integer port = extractPortFromPrompt(name);

        // 2. DB 항목 조회 (포트 기반)
        Optional<DynamicTool> dbTool = port != null
                ? repository.findAll().stream().filter(t -> t.getPort() == port).findFirst()
                : Optional.empty();

        if (dbTool.isPresent()) {
            DynamicTool tool = dbTool.get();
            log.info("[tool-delete] found in DB name={} port={} pid={}", tool.getToolName(), tool.getPort(), tool.getPid());

            emit.accept(AgentEvent.step("프로세스 종료 중 (port " + tool.getPort() + ")..."));
            stopProcess(tool.getPid(), emit);
            killByPort(tool.getPort(), emit);

            emit.accept(AgentEvent.step("코드 파일 삭제 중..."));
            tryRemoveToolFile(tool.getToolName());

            emit.accept(AgentEvent.step("DB에서 도구 제거 중..."));
            repository.delete(tool);
        } else {
            log.info("[tool-delete] not found in DB displayName={} port={}", name, port);
            emit.accept(AgentEvent.step("DB 항목 없음 (이미 삭제되었거나 미등록 도구)"));
        }

        // 3. 시스템 프롬프트에서 항목 제거 (DB 여부와 무관하게 항상 시도)
        emit.accept(AgentEvent.step("시스템 프롬프트에서 항목 제거 중..."));
        boolean removed = tryRemovePromptEntry(name);
        if (removed) {
            emit.accept(AgentEvent.step("시스템 프롬프트 항목 제거 완료"));
        } else {
            emit.accept(AgentEvent.step("시스템 프롬프트에서 항목을 찾지 못했습니다 (이미 제거됨)"));
        }

        log.info("[tool-delete] done displayName={}", name);
        emit.accept(AgentEvent.finalAnswer("'" + name + "' 도구가 삭제되었습니다."));
    }

    private Integer extractPortFromPrompt(String displayName) {
        try {
            String prompt = toolRegistryService.loadSystemPromptRaw();
            // 표시명 이후 가장 가까운 localhost:{port}/execute 추출
            Pattern p = Pattern.compile(
                    Pattern.quote(displayName) + "[\\s\\S]{0,300}?localhost:(\\d+)/execute",
                    Pattern.MULTILINE);
            Matcher m = p.matcher(prompt);
            if (m.find()) {
                return Integer.parseInt(m.group(1));
            }
        } catch (Exception e) {
            log.warn("[tool-delete] port extraction failed: {}", e.toString());
        }
        return null;
    }

    private void stopProcess(Long pid, Consumer<AgentEvent> emit) {
        if (pid == null || pid <= 0) {
            emit.accept(AgentEvent.step("PID 정보 없음 — 프로세스 종료 생략"));
            return;
        }
        try {
            ProcessHandle.of(pid).ifPresentOrElse(ph -> {
                boolean ok = ph.destroy();
                if (!ok) ph.destroyForcibly();
            }, () -> {});
            emit.accept(AgentEvent.step("프로세스 종료 완료 (pid " + pid + ")"));
        } catch (Exception e) {
            emit.accept(AgentEvent.step("프로세스 종료 실패 (무시 가능): " + e.getMessage()));
        }
    }

    private void killByPort(int port, Consumer<AgentEvent> emit) {
        if (port <= 0) return;
        try {
            try (ServerSocket ignored = new ServerSocket(port)) {
                return; // already free
            } catch (IOException ignored) {}

            String os = System.getProperty("os.name", "").toLowerCase();
            ProcessBuilder pb;
            if (os.contains("win")) {
                // PowerShell: find PID listening on port and kill it
                String script = String.format(
                    "$p = (Get-NetTCPConnection -LocalPort %d -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1).OwningProcess; if ($p) { Stop-Process -Id $p -Force }",
                    port);
                pb = new ProcessBuilder("powershell", "-NoProfile", "-NonInteractive", "-Command", script);
            } else {
                pb = new ProcessBuilder("sh", "-c", "lsof -ti :" + port + " | xargs -r kill -9");
            }
            pb.redirectErrorStream(true);
            pb.start().waitFor(5, TimeUnit.SECONDS);
            log.info("[kill-port] port={} killed", port);
            emit.accept(AgentEvent.step("포트 " + port + " 프로세스 강제 종료 완료"));
        } catch (Exception e) {
            log.warn("[kill-port] port={} error: {}", port, e.toString());
        }
    }

    private void tryRemoveToolFile(String toolName) {
        if (baseDir == null) return;
        try {
            Files.deleteIfExists(Path.of(baseDir, "tool-server", toolName + "_app.py"));
        } catch (Exception ignored) {}
    }

    private boolean tryRemovePromptEntry(String displayName) {
        if (baseDir == null) return false;
        Path promptPath = Path.of(baseDir, "system-prompt.txt");
        Path devPromptPath = Path.of(baseDir, "src/main/resources/system-prompt.txt");
        try {
            if (!Files.exists(promptPath)) return false;
            String current = Files.readString(promptPath);
            String updated = removeToolBlock(current, displayName);
            if (updated.equals(current)) return false;
            Files.writeString(promptPath, updated, StandardOpenOption.TRUNCATE_EXISTING);
            if (Files.exists(devPromptPath)) {
                Files.writeString(devPromptPath, updated, StandardOpenOption.TRUNCATE_EXISTING);
            }
            return true;
        } catch (IOException ignored) {
            return false;
        }
    }

    /**
     * "N. displayName" 또는 "- displayName" 블록을 프롬프트에서 제거.
     * 다음 도구 블록 시작 또는 [응답 형식] 전까지를 한 블록으로 간주.
     */
    static String removeToolBlock(String prompt, String displayName) {
        if (prompt == null || prompt.isBlank()) return prompt;

        // 도구 블록 헤더: "N. displayName" 또는 "- displayName"
        Pattern blockHeader = Pattern.compile(
                "(\\n|^)([ \\t]*)(?:\\d+\\.|-)[ \\t]+" + Pattern.quote(displayName) + "[ \\t]*(?:\\n|$)",
                Pattern.MULTILINE);
        Matcher m = blockHeader.matcher(prompt);
        if (!m.find()) return prompt;

        int blockStart = m.start(1) < 0 ? m.start() : m.start(1);
        // 실제로 줄 시작부터 포함
        if (blockStart < prompt.length() && prompt.charAt(blockStart) == '\n') blockStart++;

        // 다음 도구 블록 헤더 또는 [응답 형식] 찾기
        Pattern nextBlock = Pattern.compile(
                "(?:^|\\n)[ \\t]*(?:\\d+\\.|-) |\\[응답 형식\\]",
                Pattern.MULTILINE);
        Matcher next = nextBlock.matcher(prompt);
        next.region(m.end(), prompt.length());

        int blockEnd = next.find() ? next.start() : prompt.length();

        String updated = prompt.substring(0, blockStart) + prompt.substring(blockEnd);
        return updated.replaceAll("\\n{3,}", "\n\n");
    }
}
