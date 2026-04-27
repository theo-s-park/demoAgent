package theo.demoagent.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import theo.demoagent.domain.DynamicTool;
import theo.demoagent.domain.DynamicToolRepository;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class ToolRestoreRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ToolRestoreRunner.class);

    private final DynamicToolRepository repository;

    @Value("${user.dir}")
    private String baseDir;

    public ToolRestoreRunner(DynamicToolRepository repository) {
        this.repository = repository;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<DynamicTool> tools = repository.findAll();
        if (tools.isEmpty()) return;

        Path toolDir = Path.of(baseDir, "tool-server");
        String python = resolvePython(toolDir);
        Map<String, String> mergedEnv = new HashMap<>();
        mergedEnv.putAll(readDotEnv(Path.of(baseDir, ".env")));
        mergedEnv.putAll(readDotEnv(Path.of(baseDir, ".env.local")));

        log.info("[tool-restore] start count={} toolDir={}", tools.size(), toolDir);
        for (DynamicTool tool : tools) {
            Path codeFile = toolDir.resolve(tool.getToolName() + "_app.py");
            if (!Files.exists(codeFile)) {
                log.warn("[tool-restore] missing code name={} path={}", tool.getToolName(), codeFile);
                continue;
            }
            try {
                ProcessBuilder pb = new ProcessBuilder(python, "-m", "uvicorn",
                        tool.getToolName() + "_app:app",
                        "--host", "0.0.0.0",
                        "--port", String.valueOf(tool.getPort()))
                        .directory(toolDir.toFile())
                        .redirectErrorStream(true)
                        ;
                pb.environment().putAll(mergedEnv);
                Process p = pb.start();
                try {
                    tool.setPid(p.pid());
                    repository.save(tool);
                } catch (Exception ignored) {}
                log.info("[tool-restore] restored name={} port={} pid={}", tool.getToolName(), tool.getPort(), p.pid());
            } catch (Exception e) {
                log.warn("[tool-restore] failed name={} err={}", tool.getToolName(), e.toString());
            }
        }
    }

    private String resolvePython(Path toolDir) {
        Path venv = toolDir.resolve(".venv").resolve("Scripts").resolve("python.exe");
        if (Files.exists(venv)) return venv.toString();
        Path rootVenv = Path.of(baseDir).resolve(".venv").resolve("Scripts").resolve("python.exe");
        if (Files.exists(rootVenv)) return rootVenv.toString();
        return "python";
    }

    private Map<String, String> readDotEnv(Path path) {
        Map<String, String> out = new HashMap<>();
        try {
            if (!Files.exists(path)) return out;
            for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
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
}
