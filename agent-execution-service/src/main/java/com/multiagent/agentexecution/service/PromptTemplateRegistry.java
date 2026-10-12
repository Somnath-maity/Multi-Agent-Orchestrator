package com.multiagent.agentexecution.service;

import com.multiagent.agentexecution.model.PromptTemplate;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class PromptTemplateRegistry {

    private final Map<String, PromptTemplate> templates = new ConcurrentHashMap<>();

    @PostConstruct
    void loadTemplates() throws IOException {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] resources = resolver.getResources("classpath:prompts/*.txt");

        for (Resource resource : resources) {
            String filename = resource.getFilename();
            if (filename == null) continue;

            String id = filename.replace(".txt", "");
            String content = resource.getContentAsString(StandardCharsets.UTF_8);

            PromptTemplate template = PromptTemplate.builder()
                    .id(id)
                    .version(1)
                    .template(content)
                    .build();

            templates.put(id, template);
            log.info("Loaded prompt template: {}", id);
        }

        log.info("Loaded {} prompt template(s)", templates.size());
    }

    public Optional<PromptTemplate> get(String id) {
        return Optional.ofNullable(templates.get(id));
    }
}
