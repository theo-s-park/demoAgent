package theo.demoagent.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "dynamic_tool")
public class DynamicTool {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String toolName;
    private int port;
    private LocalDateTime createdAt;
    private Long pid;

    protected DynamicTool() {}

    public DynamicTool(String toolName, int port) {
        this.toolName = toolName;
        this.port = port;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public String getToolName() { return toolName; }
    public int getPort() { return port; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public Long getPid() { return pid; }

    public void setPid(Long pid) { this.pid = pid; }
}
