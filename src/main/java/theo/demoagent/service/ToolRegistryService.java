package theo.demoagent.service;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import theo.demoagent.dto.ToolInfo;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ToolRegistryService {

    @org.springframework.beans.factory.annotation.Value("${user.dir}")
    private String baseDir;

    public String loadSystemPromptRaw() {
        if (baseDir != null) {
            Path rootPrompt = Path.of(baseDir, "system-prompt.txt");
            if (Files.exists(rootPrompt)) {
                try {
                    return Files.readString(rootPrompt);
                } catch (IOException ignored) {}
            }
        }
        try {
            ClassPathResource resource = new ClassPathResource("system-prompt.txt");
            return new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("system-prompt.txt를 찾을 수 없습니다.", e);
        }
    }

    public List<ToolInfo> listTools() {
        String prompt = loadSystemPromptRaw();
        return parseToolsFromPrompt(prompt);
    }

    public synchronized String updateSystemPrompt(String content) throws IOException {
        if (baseDir == null) throw new IOException("baseDir is null");
        String normalized = (content == null ? "" : content).replace("\r\n", "\n");

        Path rootPrompt = Path.of(baseDir, "system-prompt.txt");
        Files.writeString(rootPrompt, normalized, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

        // Keep dev resource in sync when running from source tree
        Path devPrompt = Path.of(baseDir, "src/main/resources/system-prompt.txt");
        if (Files.exists(devPrompt)) {
            Files.writeString(devPrompt, normalized, StandardOpenOption.TRUNCATE_EXISTING);
        }
        return normalized;
    }

    /**
     * Very lightweight parser:
     * - Finds blocks starting with "N. <name>" (built-in tools)
     * - Also supports "- <name>" (tools appended by ToolCreator prompt_entry)
     * - Captures URL/인자/설명 lines if present
     * - Keeps original block for UI preview
     */
    static List<ToolInfo> parseToolsFromPrompt(String prompt) {
        List<ToolInfo> out = new ArrayList<>();
        if (prompt == null || prompt.isBlank()) return out;

        int start = prompt.indexOf("[사용 가능한 도구]");
        if (start < 0) return out;
        int end = prompt.indexOf("[응답 형식]", start);
        String section = end >= 0 ? prompt.substring(start, end) : prompt.substring(start);

        Pattern blockStart = Pattern.compile("(?m)^(?:(\\d+)\\.\\s*(.+?)|-\\s*(.+?))\\s*$");
        Matcher m = blockStart.matcher(section);

        List<Integer> starts = new ArrayList<>();
        List<String> names = new ArrayList<>();
        List<String> ids = new ArrayList<>();
        while (m.find()) {
            starts.add(m.start());
            String num = m.group(1);
            String numberedName = m.group(2);
            String dashedName = m.group(3);

            String name = (numberedName != null ? numberedName : dashedName).trim();
            String id = (num != null && !num.isBlank()) ? num : "dyn-" + (ids.size() + 1);

            ids.add(id);
            names.add(name);
        }
        starts.add(section.length());

        Pattern urlP = Pattern.compile("(?m)^\\s*URL:\\s*(\\S+)\\s*$");
        Pattern argsP = Pattern.compile("(?m)^\\s*인자[^:]*:\\s*(.+?)\\s*$");
        Pattern descP = Pattern.compile("(?m)^\\s*설명:\\s*(.+?)\\s*$");

        for (int i = 0; i < ids.size(); i++) {
            String block = section.substring(starts.get(i), starts.get(i + 1)).trim();
            String id = ids.get(i);
            String name = names.get(i);

            String url = null;
            Matcher urlM = urlP.matcher(block);
            if (urlM.find()) url = urlM.group(1).trim();

            String args = null;
            Matcher argsM = argsP.matcher(block);
            if (argsM.find()) args = argsM.group(1).trim();

            String desc = null;
            Matcher descM = descP.matcher(block);
            if (descM.find()) desc = descM.group(1).trim();

            out.add(new ToolInfo(id, name, url, args, desc, block));
        }

        return out;
    }
}

