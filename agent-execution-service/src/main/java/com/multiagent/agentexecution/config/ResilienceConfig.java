package com.multiagent.agentexecution.config;

import org.springframework.context.annotation.Configuration;

/**
 * Resilience4j configuration.
 *
 * Retry and circuit breaker instances are configured via application.yml.
 * This class is a placeholder for any future custom Customizer beans
 * (e.g., custom fallback handlers, event listeners).
 *
 * TODO: Add custom Customizer<RetryConfig> or Customizer<CircuitBreakerConfig> beans
 *       if advanced behavior is needed beyond what YAML configuration provides.
 */
@Configuration
public class ResilienceConfig {

    // Retry and CircuitBreaker for "portkey-call" are fully configured in application.yml.
    // Add @Bean Customizer methods here if runtime customization is required.
}
