package com.multiagent.orchestrator;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class OrchestratorApplicationTests {

    @Test
    void contextLoads() {
        // Smoke test: verifies that the Spring application context starts up without errors
    }
}
