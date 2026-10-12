package com.multiagent.agentexecution.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Simple POJO representing a prompt template.
 * Not JPA-backed -- templates are loaded from config or classpath.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PromptTemplate {

    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{\\{(\\w+)}}");

    private String id;
    private int version;
    private String template;
    private List<String> requiredVariables;

    /**
     * Renders the template by substituting {{var}} placeholders with the provided values.
     *
     * @param variables map of variable name to value
     * @return the rendered template string
     * @throws IllegalArgumentException if any required {{...}} placeholders remain after substitution
     */
    public String render(Map<String, String> variables) {
        String rendered = template;

        for (Map.Entry<String, String> entry : variables.entrySet()) {
            rendered = rendered.replace("{{" + entry.getKey() + "}}", entry.getValue());
        }

        // Validate no unreplaced placeholders remain
        Matcher matcher = VARIABLE_PATTERN.matcher(rendered);
        if (matcher.find()) {
            throw new IllegalArgumentException(
                    "Unresolved template variable(s) found after rendering: " + matcher.group(0)
                            + ". Ensure all required variables are provided."
            );
        }

        return rendered;
    }
}
