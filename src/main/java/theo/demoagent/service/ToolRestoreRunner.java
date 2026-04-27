package theo.demoagent.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
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

        for (DynamicTool tool : tools) {
            Path codeFile = toolDir.resolve(tool.getToolName() + "_app.py");
            if (!Files.exists(codeFile)) {
                System.out.printf("[ToolRestore] 코드 파일 없음, 건너뜀: %s%n", codeFile);
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
                System.out.printf("[ToolRestore] 복원 완료: %s (port %d, pid %d)%n", tool.getToolName(), tool.getPort(), p.pid());
            } catch (Exception e) {
                System.out.printf("[ToolRestore] 복원 실패: %s — %s%n", tool.getToolName(), e.getMessage());
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
