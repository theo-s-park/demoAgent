package theo.demoagent.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DynamicToolRepository extends JpaRepository<DynamicTool, Long> {
    Optional<DynamicTool> findByToolName(String toolName);
}
