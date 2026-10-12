package com.multiagent.agentexecution;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@TestConfiguration
public class TestConfig {

    @SuppressWarnings("unchecked")
    @Bean
    public RedisTemplate<String, String> redisTemplate() {
        RedisTemplate<String, String> mock = mock(RedisTemplate.class);
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        when(mock.opsForValue()).thenReturn(valueOps);
        return mock;
    }
}
