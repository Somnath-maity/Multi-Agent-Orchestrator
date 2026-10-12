package com.multiagent.agentexecution.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Service for executing tools by name. Maintains a registry of tool name to handler function.
 * Each tool validates its input arguments against a schema before execution.
 */
@Slf4j
@Service
public class ToolService {

    private final Map<String, Function<String, String>> toolRegistry = new HashMap<>();
    private final Map<String, String> toolSchemas = new HashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostConstruct
    void initTools() {
        // Register stub tools
        registerTool("web_search", """
                {
                  "type": "object",
                  "properties": {
                    "query": { "type": "string" }
                  },
                  "required": ["query"]
                }
                """, args -> {
            // TODO: Implement real web search integration
            log.info("web_search called with args: {}", args);
            return "{\"results\": [{\"title\": \"Stub result\", \"snippet\": \"This is a stub web search result.\"}]}";
        });

        registerTool("summarize", """
                {
                  "type": "object",
                  "properties": {
                    "text": { "type": "string" },
                    "max_length": { "type": "integer" }
                  },
                  "required": ["text"]
                }
                """, args -> {
            // TODO: Implement real summarization (likely via LLM call)
            log.info("summarize called with args: {}", args);
            return "{\"summary\": \"This is a stub summary of the provided text.\"}";
        });
    }

    /**
     * Registers a tool with its input schema and handler function.
     *
     * @param name    the tool name
     * @param schema  JSON schema string for input validation
     * @param handler function that takes args JSON string and returns result JSON string
     */
    public void registerTool(String name, String schema, Function<String, String> handler) {
        toolRegistry.put(name, handler);
        toolSchemas.put(name, schema);
        log.info("Registered tool: {}", name);
    }

    /**
     * Executes a tool by name after validating its input arguments.
     *
     * @param toolName the name of the tool to execute
     * @param argsJson JSON string of the tool arguments
     * @return the tool execution result as a JSON string
     * @throws IllegalArgumentException if the tool is not found or input validation fails
     */
    public String executeTool(String toolName, String argsJson) {
        Function<String, String> handler = toolRegistry.get(toolName);
        if (handler == null) {
            throw new IllegalArgumentException("Unknown tool: " + toolName);
        }

        // Validate input args against the tool's schema
        String schema = toolSchemas.get(toolName);
        if (schema != null) {
            validateArgs(toolName, argsJson, schema);
        }

        log.debug("Executing tool '{}' with args: {}", toolName, argsJson);
        return handler.apply(argsJson);
    }

    /**
     * Returns a list of all registered tool names.
     */
    public Set<String> listTools() {
        return toolRegistry.keySet();
    }

    private void validateArgs(String toolName, String argsJson, String schemaStr) {
        try {
            JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);
            JsonSchema schema = factory.getSchema(schemaStr);
            JsonNode argsNode = objectMapper.readTree(argsJson);
            Set<ValidationMessage> errors = schema.validate(argsNode);
            if (!errors.isEmpty()) {
                throw new IllegalArgumentException(
                        "Input validation failed for tool '%s': %s".formatted(toolName, errors)
                );
            }
        } catch (IllegalArgumentException e) {
            throw e; // re-throw validation errors
        } catch (Exception e) {
            log.error("Failed to validate args for tool '{}': {}", toolName, e.getMessage());
            throw new IllegalArgumentException("Invalid JSON args for tool '%s'".formatted(toolName), e);
        }
    }
}
