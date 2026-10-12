package com.multiagent.agentexecution;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestConfig.class)
class AgentExecutionApplicationTests {

    @Test
    void contextLoads() {
        // Verifies that the Spring application context starts up successfully
    }
}
