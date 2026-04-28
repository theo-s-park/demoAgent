package theo.demoagent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import theo.demoagent.client.OpenAiClient;
import theo.demoagent.domain.DynamicTool;
import theo.demoagent.domain.DynamicToolRepository;
import theo.demoagent.dto.AgentEvent;
import theo.demoagent.dto.ToolCreatorLlmResponse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.regex.Pattern;

@Service
public class ToolCreatorService {

    private static final Logger log = LoggerFactory.getLogger(ToolCreatorService.class);

    private final OpenAiClient openAiClient;
    private final DynamicToolRepository dynamicToolRepository;
    private final ToolRegistryService toolRegistryService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AtomicInteger nextPort = new AtomicInteger(8090);

    @Value("${user.dir}")
    private String baseDir;

    private static final Pattern SAFE_TOOL_NAME = Pattern.compile("^[a-zA-Z][a-zA-Z0-9_]{0,40}$");

    public ToolCreatorService(OpenAiClient openAiClient, DynamicToolRepository dynamicToolRepository,
                              ToolRegistryService toolRegistryService) {
        this.openAiClient = openAiClient;
        this.dynamicToolRepository = dynamicToolRepository;
        this.toolRegistryService = toolRegistryService;
    }

    public void create(String description, Map<String, String> answers, Consumer<AgentEvent> emit) {
        log.info("[tool-create] start desc_len={} answers_keys={}", description == null ? 0 : description.length(), answers == null ? List.of() : answers.keySet());
        String creatorPrompt = loadCreatorPrompt();

        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", creatorPrompt));
        messages.add(Map.of("role", "user", "content", buildUserMessage(description, answers)));

        emit.accept(AgentEvent.step("요청 분석 중..."));

        String content;
        try {
            log.info("[tool-create] llm request");
            content = openAiClient.chat(messages);
        } catch (Exception e) {
            log.warn("[tool-create] llm failed: {}", e.toString());
            emit.accept(AgentEvent.error("LLM 호출 실패: " + e.getMessage()));
            return;
        }

        ToolCreatorLlmResponse response;
        try {
            String json = content.trim().replaceAll("(?s)^```\\w*\\n?", "").replaceAll("```\\s*$", "").trim();
            response = objectMapper.readValue(json, ToolCreatorLlmResponse.class);
        } catch (Exception e) {
            log.warn("[tool-create] llm response parse failed: {}", e.toString());
            emit.accept(AgentEvent.error("응답 파싱 실패: " + content));
            return;
        }

        if ("need_info".equals(response.action())) {
            try {
                List<Map<String, String>> normalized = normalizeQuestions(response.questions());
                log.info("[tool-create] need_info toolName={} questions={}", response.toolName(), normalized.size());
                String questionsJson = objectMapper.writeValueAsString(
                        Map.of("tool_name", response.toolName(), "questions", normalized)
                );
                emit.accept(AgentEvent.form(questionsJson));
            } catch (Exception e) {
                emit.accept(AgentEvent.error("질문 생성 실패"));
            }
            return;
        }

        if ("update_files".equals(response.action())) {
            emit.accept(AgentEvent.step("파일 업데이트 중..."));
            try {
                int updated = updateFiles(response.files(), emit);
                emit.accept(AgentEvent.finalAnswer("파일 업데이트 완료 (" + updated + "개). 변경 사항이 반영되도록 관련 서버/프로세스를 재시작하세요."));
            } catch (Exception e) {
                emit.accept(AgentEvent.error("파일 업데이트 실패: " + e.getMessage()));
            }
            return;
        }

        if ("update_tool".equals(response.action())) {
            String toolName = response.toolName() == null ? "" : response.toolName().trim();
            if (!SAFE_TOOL_NAME.matcher(toolName).matches()) {
                emit.accept(AgentEvent.error("toolName이 안전한 형식이 아닙니다: " + toolName));
                return;
            }

            DynamicTool existing = dynamicToolRepository.findByToolName(toolName).orElse(null);
            if (existing == null) {
                emit.accept(AgentEvent.error("존재하지 않는 도구입니다: " + toolName + ". 새 도구 추가는 create_tool을 사용하세요."));
                return;
            }

            int port = existing.getPort();
            log.info("[tool-update] name={} port={} old_pid={}", toolName, port, existing.getPid());

            emit.accept(AgentEvent.step("기존 프로세스 종료 중..."));
            killProcess(existing.getPid());

            emit.accept(AgentEvent.step("코드 파일 업데이트 중..."));
            if (!writeToolFile(toolName, response.code(), emit)) return;

            emit.accept(AgentEvent.step("시스템 프롬프트 업데이트 중..."));
            replaceSystemPromptEntry(toolName, response.promptEntry(), port, emit);

            emit.accept(AgentEvent.step("도구 서버 재시작 중..."));
            Process proc = startToolProcess(toolName, port, response.envVars(), emit);
            if (proc == null) return;
            log.info("[tool-update] restarted name={} port={} new_pid={}", toolName, port, proc.pid());

            emit.accept(AgentEvent.step("헬스 체크 중..."));
            boolean healthy = waitForHealth(port, emit);
            if (!healthy) {
                emit.accept(AgentEvent.error("도구 서버 재시작 실패(health timeout): port " + port));
                return;
            }

            existing.setPid(proc.pid());
            dynamicToolRepository.save(existing);
            log.info("[tool-update] persisted name={} port={} new_pid={}", toolName, port, proc.pid());

            emit.accept(AgentEvent.finalAnswer(toolName + " 도구가 업데이트되어 재시작되었습니다 (port " + port + ")."));
            return;
        }

        if ("create_tool".equals(response.action())) {
            int port = nextPort.getAndIncrement();
            String toolName = response.toolName() == null ? "" : response.toolName().trim();
            if (!SAFE_TOOL_NAME.matcher(toolName).matches()) {
                log.warn("[tool-create] unsafe toolName={}", toolName);
                emit.accept(AgentEvent.error("toolName이 안전한 형식이 아닙니다: " + toolName));
                return;
            }

            log.info("[tool-create] create_tool name={} port={} env_keys={}", toolName, port, response.envVars() == null ? List.of() : response.envVars().keySet());

            emit.accept(AgentEvent.step("코드 파일 저장 중..."));
            if (!writeToolFile(toolName, response.code(), emit)) return;

            emit.accept(AgentEvent.step("환경 변수 저장 중..."));
            updateEnvFile(response.envVars(), emit);

            emit.accept(AgentEvent.step("시스템 프롬프트 업데이트 중..."));
            updateSystemPrompt(response.promptEntry(), port, emit);

            emit.accept(AgentEvent.step("도구 서버 시작 중..."));
            Process proc = startToolProcess(toolName, port, response.envVars(), emit);
            if (proc == null) return;
            log.info("[tool-create] spawned name={} port={} pid={}", toolName, port, proc.pid());

            emit.accept(AgentEvent.step("헬스 체크 중..."));
            boolean healthy = waitForHealth(port, emit);
            if (!healthy) {
                emit.accept(AgentEvent.error("도구 서버가 기동되지 않았습니다(health timeout): port " + port));
                return;
            }

            DynamicTool saved = dynamicToolRepository.save(new DynamicTool(toolName, port));
            try {
                saved.setPid(proc.pid());
                dynamicToolRepository.save(saved);
            } catch (Exception ignored) {}
            log.info("[tool-create] persisted name={} port={} pid={}", toolName, port, saved.getPid());

            emit.accept(AgentEvent.finalAnswer(toolName + " 도구가 활성화되었습니다 (port " + port + "). 이제 에이전트에게 질문해보세요!"));
        }
    }

    private String buildUserMessage(String description, Map<String, String> answers) {
        StringBuilder sb = new StringBuilder();

        // Provide existing tools so the LLM can match tool_name for update_tool
        try {
            List<theo.demoagent.dto.ToolInfo> tools = toolRegistryService.listTools();
            if (!tools.isEmpty()) {
                sb.append("[현재 등록된 도구 목록]\n");
                for (var t : tools) {
                    // id is the numbered index or "dyn-N"; name is display name
                    // Try to resolve internal tool_name from DB by port
                    String internalName = resolveInternalName(t.url());
                    sb.append("- 표시명: ").append(t.name());
                    if (internalName != null) sb.append(" | tool_name: ").append(internalName);
                    if (t.url() != null) sb.append(" | URL: ").append(t.url());
                    sb.append("\n");
                }
                sb.append("\n");
            }
        } catch (Exception ignored) {}

        sb.append("기능 설명: ").append(description);
        if (!answers.isEmpty()) {
            sb.append("\n\n제공된 정보:");
            answers.forEach((k, v) -> sb.append("\n- ").append(k).append(": ").append(v));
        }
        return sb.toString();
    }

    private String resolveInternalName(String url) {
        if (url == null) return null;
        try {
            // URL format: http://localhost:{port}/execute
            String portStr = url.replaceAll(".*:(\\d+)/.*", "$1");
            int port = Integer.parseInt(portStr);
            return dynamicToolRepository.findAll().stream()
                    .filter(t -> t.getPort() == port)
                    .map(DynamicTool::getToolName)
                    .findFirst()
                    .orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    private List<Map<String, String>> normalizeQuestions(com.fasterxml.jackson.databind.JsonNode questionsNode) {
        if (questionsNode == null || questionsNode.isNull()) return List.of();

        // Backward-compat: questions can be ["문자열", ...]
        if (questionsNode.isArray() && questionsNode.size() > 0 && questionsNode.get(0).isTextual()) {
            List<Map<String, String>> out = new ArrayList<>();
            for (int i = 0; i < questionsNode.size(); i++) {
                String label = questionsNode.get(i).asText("");
                out.add(Map.of(
                        "key", "Q" + (i + 1),
                        "label", label,
                        "help", "",
                        "link", "",
                        "where_used", ""
                ));
            }
            return out;
        }

        // Preferred: questions: [{key,label,help,link,where_used}, ...]
        if (questionsNode.isArray()) {
            List<Map<String, String>> out = new ArrayList<>();
            for (int i = 0; i < questionsNode.size(); i++) {
                var q = questionsNode.get(i);
                String key = q.path("key").asText("");
                String label = q.path("label").asText("");
                String help = q.path("help").asText("");
                String link = q.path("link").asText("");
                String whereUsed = q.path("where_used").asText("");

                if (key.isBlank()) key = "Q" + (i + 1);
                if (label.isBlank()) label = "추가 정보 입력 (" + key + ")";

                out.add(Map.of(
                        "key", key,
                        "label", label,
                        "help", help,
                        "link", link,
                        "where_used", whereUsed
                ));
            }
            return out;
        }

        return List.of(Map.of(
                "key", "Q1",
                "label", "추가 정보가 필요합니다",
                "help", questionsNode.asText(""),
                "link", "",
                "where_used", ""
        ));
    }

    private static final Set<String> ALLOWED_UPDATE_PATHS = Set.of(
            "system-prompt.txt",
            "src/main/resources/system-prompt.txt"
    );

    private boolean isAllowedUpdatePath(String rel) {
        if (ALLOWED_UPDATE_PATHS.contains(rel)) return true;
        // allow any tool-server/*_app.py
        return rel != null && rel.matches("tool-server/[a-zA-Z][a-zA-Z0-9_]{0,40}_app\\.py");
    }

    private void killProcess(Long pid) {
        if (pid == null || pid <= 0) return;
        try {
            ProcessHandle.of(pid).ifPresent(ProcessHandle::destroyForcibly);
        } catch (Exception e) {
            log.warn("[tool-update] kill pid={} failed: {}", pid, e.toString());
        }
    }

    private void replaceSystemPromptEntry(String toolName, String promptEntry, int port, Consumer<AgentEvent> emit) {
        if (promptEntry == null || promptEntry.isBlank()) return;
        try {
            Path promptPath = Path.of(baseDir, "system-prompt.txt");
            if (!Files.exists(promptPath)) return;
            String current = Files.readString(promptPath);
            String newEntry = promptEntry.replace("{PORT}", String.valueOf(port));

            // Find the block that contains the tool's execute URL for this port
            // Replace from the tool name line to the next blank line or next numbered/dashed tool
            String portStr = String.valueOf(port);
            int portIdx = current.indexOf("/execute\n", current.indexOf("localhost:" + portStr));
            if (portIdx < 0) portIdx = current.indexOf("/execute", current.indexOf("localhost:" + portStr));

            if (portIdx >= 0) {
                // Walk back to find the start of this tool's block (numbered or dashed line)
                int blockStart = current.lastIndexOf("\n", portIdx - 1);
                // Find the previous tool header line
                int prevNewline = current.lastIndexOf("\n", blockStart - 1);
                while (prevNewline >= 0) {
                    String candidate = current.substring(prevNewline + 1, blockStart + 1).stripLeading();
                    if (candidate.matches("(\\d+\\.|-)\\s+.+\\n?")) {
                        blockStart = prevNewline;
                        break;
                    }
                    int next = current.lastIndexOf("\n", prevNewline - 1);
                    if (next < 0 || next == prevNewline) break;
                    blockStart = prevNewline;
                    prevNewline = next;
                }

                // Find the end of this block (next blank line or next tool header or [응답 형식])
                int blockEnd = portIdx + "/execute".length();
                // advance past rest of this block
                int nextBlank = current.indexOf("\n\n", blockEnd);
                int responseSection = current.indexOf("[응답 형식]", blockEnd);
                blockEnd = nextBlank >= 0 ? nextBlank : (responseSection >= 0 ? responseSection - 1 : current.length());

                String updated = current.substring(0, blockStart + 1) + newEntry + current.substring(blockEnd);
                Files.writeString(promptPath, updated);
                emit.accept(AgentEvent.step("시스템 프롬프트 항목 교체 완료"));
            } else {
                // Port not found — append as new entry
                updateSystemPrompt(promptEntry, port, emit);
            }

            // sync dev resource
            Path devPrompt = Path.of(baseDir, "src/main/resources/system-prompt.txt");
            if (Files.exists(devPrompt)) {
                Files.writeString(devPrompt, Files.readString(promptPath));
            }
        } catch (IOException e) {
            emit.accept(AgentEvent.step("프롬프트 항목 교체 실패 (무시 가능): " + e.getMessage()));
        }
    }

    private int updateFiles(List<theo.demoagent.dto.ToolFileUpdate> files, Consumer<AgentEvent> emit) throws IOException {
        if (files == null || files.isEmpty()) {
            throw new IllegalArgumentException("files is empty");
        }

        int updated = 0;
        Set<String> seen = new HashSet<>();
        for (var f : files) {
            if (f == null) continue;
            String rel = f.path();
            if (rel == null || rel.isBlank()) continue;
            if (!isAllowedUpdatePath(rel)) {
                throw new IllegalArgumentException("허용되지 않은 path: " + rel);
            }
            if (!seen.add(rel)) continue;

            Path target = Path.of(baseDir, rel);
            Files.createDirectories(target.getParent() != null ? target.getParent() : Path.of(baseDir));
            Files.writeString(target, f.content() == null ? "" : f.content(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            updated++;
            emit.accept(AgentEvent.step("업데이트: " + rel));
        }
        return updated;
    }

    private boolean writeToolFile(String toolName, String code, Consumer<AgentEvent> emit) {
        try {
            Path file = Path.of(baseDir, "tool-server", toolName + "_app.py");
            Files.writeString(file, code, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            return true;
        } catch (IOException e) {
            emit.accept(AgentEvent.error("파일 저장 실패: " + e.getMessage()));
            return false;
        }
    }

    private void updateEnvFile(Map<String, String> envVars, Consumer<AgentEvent> emit) {
        if (envVars == null || envVars.isEmpty()) return;
        try {
            Path env = Path.of(baseDir, ".env.local");
            StringBuilder sb = new StringBuilder();
            envVars.forEach((k, v) -> sb.append("\n").append(k).append("=").append(v));
            Files.writeString(env, sb.toString(), StandardOpenOption.APPEND, StandardOpenOption.CREATE);
        } catch (IOException e) {
            emit.accept(AgentEvent.step("환경 변수 저장 실패 (무시 가능): " + e.getMessage()));
        }
    }

    private void updateSystemPrompt(String promptEntry, int port, Consumer<AgentEvent> emit) {
        try {
            Path promptPath = Path.of(baseDir, "system-prompt.txt");
            String current;
            if (Files.exists(promptPath)) {
                current = Files.readString(promptPath);
            } else {
                Path devPrompt = Path.of(baseDir, "src/main/resources/system-prompt.txt");
                current = Files.exists(devPrompt) ? Files.readString(devPrompt) : "";
            }
            String entry = "\n" + promptEntry.replace("{PORT}", String.valueOf(port));

            int insertIdx = current.indexOf("[응답 형식]");
            String updated = insertIdx >= 0
                    ? current.substring(0, insertIdx) + entry + "\n\n" + current.substring(insertIdx)
                    : current + entry;

            Files.writeString(promptPath, updated);
        } catch (IOException e) {
            emit.accept(AgentEvent.step("프롬프트 업데이트 실패 (무시 가능): " + e.getMessage()));
        }
    }

    private Process startToolProcess(String toolName, int port, Map<String, String> envVars, Consumer<AgentEvent> emit) {
        try {
            Path toolDir = Path.of(baseDir, "tool-server");
            String python = resolvePythonPath(toolDir);

            ProcessBuilder pb = new ProcessBuilder(
                    python, "-m", "uvicorn",
                    toolName + "_app:app",
                    "--host", "0.0.0.0",
                    "--port", String.valueOf(port)
            )
                    .directory(toolDir.toFile())
                    .redirectErrorStream(true);

            // Inherit current env + load .env/.env.local + merge new env_vars
            Map<String, String> merged = new HashMap<>();
            merged.putAll(readDotEnv(Path.of(baseDir, ".env")));
            merged.putAll(readDotEnv(Path.of(baseDir, ".env.local")));
            if (envVars != null) merged.putAll(envVars);
            pb.environment().putAll(merged);

            Process p = pb.start();

            emit.accept(AgentEvent.step("프로세스 시작 요청 완료"));
            return p;
        } catch (Exception e) {
            emit.accept(AgentEvent.step(
                    "자동 시작 실패. 수동 실행: cd tool-server && python -m uvicorn " + toolName + "_app:app --port " + port
            ));
            emit.accept(AgentEvent.error("도구 서버 자동 시작 실패: " + toolName + " (port " + port + ")"));
            return null;
        }
    }

    private boolean waitForHealth(int port, Consumer<AgentEvent> emit) {
        SimpleClientHttpRequestFactory rf = new SimpleClientHttpRequestFactory();
        rf.setConnectTimeout(600);
        rf.setReadTimeout(800);
        RestClient client = RestClient.builder().requestFactory(rf).build();

        String url = "http://localhost:" + port + "/health";
        int attempts = 20;
        for (int i = 0; i < attempts; i++) {
            try {
                int status = client.get().uri(url).retrieve().toBodilessEntity().getStatusCode().value();
                if (status >= 200 && status < 300) {
                    emit.accept(AgentEvent.step("헬스 체크 성공 → " + url));
                    return true;
                }
            } catch (Exception ignored) {
                // keep retrying
            }
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    private String resolvePythonPath(Path toolDir) {
        Path toolPython = toolDir.resolve(".venv").resolve("Scripts").resolve("python.exe");
        if (Files.exists(toolPython)) return toolPython.toString();

        if (baseDir != null) {
            Path rootPython = Path.of(baseDir).resolve(".venv").resolve("Scripts").resolve("python.exe");
            if (Files.exists(rootPython)) return rootPython.toString();
        }

        return "python";
    }

    private Map<String, String> readDotEnv(Path path) {
        Map<String, String> out = new HashMap<>();
        try {
            if (!Files.exists(path)) return out;
            for (String line : Files.readAllLines(path)) {
                String s = line.trim();
                if (s.isEmpty() || s.startsWith("#")) continue;
                int idx = s.indexOf('=');
                if (idx <= 0) continue;
                String k = s.substring(0, idx).trim();
                String v = s.substring(idx + 1).trim();
                if ((v.startsWith("\"") && v.endsWith("\"")) || (v.startsWith("'") && v.endsWith("'"))) {
                    v = v.substring(1, v.length() - 1);
                }
                if (!k.isBlank()) out.put(k, v);
            }
        } catch (Exception ignored) {}
        return out;
    }

    private String loadCreatorPrompt() {
        try {
            Path path = Path.of(baseDir, "src/main/resources/system-prompt-creator.txt");
            if (Files.exists(path)) return Files.readString(path);
            var resource = new org.springframework.core.io.ClassPathResource("system-prompt-creator.txt");
            return new String(resource.getInputStream().readAllBytes());
        } catch (IOException e) {
            throw new RuntimeException("system-prompt-creator.txt를 찾을 수 없습니다.", e);
        }
    }
}
