package theo.demoagent.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import theo.demoagent.domain.DynamicTool;
import theo.demoagent.domain.DynamicToolRepository;
import theo.demoagent.dto.AgentEvent;
import theo.demoagent.service.ToolRegistryService;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/dynamic-tools")
public class DynamicToolController {

    private static final Logger log = LoggerFactory.getLogger(DynamicToolController.class);

    private final DynamicToolRepository dynamicToolRepository;
    private final ToolRegistryService toolRegistryService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ExecutorService executor = Executors.newCachedThreadPool();

    @org.springframework.beans.factory.annotation.Value("${user.dir}")
    private String baseDir;

    public DynamicToolController(DynamicToolRepository dynamicToolRepository,
                                 ToolRegistryService toolRegistryService) {
        this.dynamicToolRepository = dynamicToolRepository;
        this.toolRegistryService = toolRegistryService;
    }

    @PostMapping(value = "/delete", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter delete(@RequestBody Map<String, String> body) {
        String toolName = body.getOrDefault("tool_name", "").trim();
        SseEmitter emitter = new SseEmitter(30_000L);

        executor.submit(() -> {
            try {
                emit(emitter, AgentEvent.step("삭제 시작: " + toolName));

                // 1. DB에서 찾아서 프로세스 종료
                Optional<DynamicTool> dbTool = tryFindInDb(toolName);
                if (dbTool.isPresent()) {
                    DynamicTool t = dbTool.get();
                    killProcess(t.getPid());
                    emit(emitter, AgentEvent.step("프로세스 종료 (port " + t.getPort() + ")"));
                    dynamicToolRepository.delete(t);
                    emit(emitter, AgentEvent.step("DB 항목 삭제 완료"));
                } else {
                    emit(emitter, AgentEvent.step("DB 항목 없음 (이미 삭제되었거나 미등록 도구)"));
                }

                // 2. system-prompt.txt에서 해당 도구 블록 제거
                boolean removed = removeFromPrompt(toolName);
                if (removed) {
                    emit(emitter, AgentEvent.step("시스템 프롬프트에서 항목 제거 완료"));
                } else {
                    emit(emitter, AgentEvent.step("시스템 프롬프트에서 항목을 찾지 못했습니다"));
                }

                emit(emitter, AgentEvent.finalAnswer(toolName + " 도구가 삭제되었습니다."));
                emitter.complete();
            } catch (Exception e) {
                log.error("[tool-delete] error", e);
                try {
                    emit(emitter, AgentEvent.error("삭제 실패: " + e.getMessage()));
                    emitter.complete();
                } catch (Exception ignored) {}
            }
        });

        return emitter;
    }

    private Optional<DynamicTool> tryFindInDb(String displayName) {
        // displayName은 한글 표시명 — DB의 toolName(영문)과 다를 수 있음
        // DB 전체를 순회하면서 표시명 → toolName 매핑 시도
        return dynamicToolRepository.findAll().stream()
                .filter(t -> t.getToolName().equalsIgnoreCase(displayName)
                        || matchesDisplayName(t.getToolName(), displayName))
                .findFirst();
    }

    private boolean matchesDisplayName(String toolName, String displayName) {
        // toolName: "timezone_calculator", displayName: "시간대 및 날짜 계산기"
        // 정확히 매핑하기 어려우므로 system-prompt에서 해당 displayName의 URL 포트를 찾아 DB 포트와 비교
        try {
            String prompt = toolRegistryService.loadSystemPromptRaw();
            Pattern p = Pattern.compile(
                    Pattern.quote(displayName) + "[\\s\\S]*?localhost:(\\d+)/execute",
                    Pattern.MULTILINE);
            Matcher m = p.matcher(prompt);
            if (m.find()) {
                int port = Integer.parseInt(m.group(1));
                return dynamicToolRepository.findAll().stream()
                        .anyMatch(t -> t.getPort() == port && t.getToolName().equals(toolName));
            }
        } catch (Exception ignored) {}
        return false;
    }

    private void killProcess(Long pid) {
        if (pid == null || pid <= 0) return;
        try {
            ProcessHandle.of(pid).ifPresent(ProcessHandle::destroyForcibly);
            log.info("[tool-delete] killed pid={}", pid);
        } catch (Exception e) {
            log.warn("[tool-delete] kill failed pid={} err={}", pid, e.toString());
        }
    }

    private boolean removeFromPrompt(String displayName) throws IOException {
        Path promptPath = Path.of(baseDir, "system-prompt.txt");
        if (!Files.exists(promptPath)) return false;

        String content = Files.readString(promptPath);

        // 도구 블록: "N. 이름" 또는 "- 이름" 으로 시작해서 다음 도구 블록 또는 [응답 형식] 전까지
        Pattern blockPattern = Pattern.compile(
                "(?:(?<=\\n)|(?<=\\[사용 가능한 도구\\]\\n))" +
                "(?:\\d+\\.|-) +" + Pattern.quote(displayName) + "[\\s\\S]*?" +
                "(?=\\n(?:\\d+\\.|-) |\\n\\[응답 형식\\]|\\z)",
                Pattern.MULTILINE);

        Matcher m = blockPattern.matcher(content);
        if (!m.find()) return false;

        String updated = content.substring(0, m.start()) + content.substring(m.end());
        // 연속 빈줄 정리
        updated = updated.replaceAll("\\n{3,}", "\n\n");

        Files.writeString(promptPath, updated);

        // dev resource 동기화
        Path devPrompt = Path.of(baseDir, "src/main/resources/system-prompt.txt");
        if (Files.exists(devPrompt)) Files.writeString(devPrompt, updated);

        log.info("[tool-delete] removed '{}' from system-prompt", displayName);
        return true;
    }

    private void emit(SseEmitter emitter, AgentEvent event) throws IOException {
        emitter.send(SseEmitter.event()
                .name(event.type())
                .data(objectMapper.writeValueAsString(Map.of("message", event.message()))));
    }
}
