package com.example.excelanalyzer.agent;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@Service
public class PromptLoader {

    private static final Logger logger = LoggerFactory.getLogger(PromptLoader.class);
    private static final String PROMPTS_DIR = "prompts/";

    private final Map<String, String> promptCache = new HashMap<>();

    @PostConstruct
    public void init() {
        loadPrompt("requirement_agent");
        loadPrompt("task_agent");
        logger.info("Loaded {} prompt templates from classpath: {}", promptCache.size(), promptCache.keySet());
    }

    private void loadPrompt(String name) {
        String path = PROMPTS_DIR + name + ".md";
        try {
            ClassPathResource resource = new ClassPathResource(path);
            String content = resource.getContentAsString(StandardCharsets.UTF_8);
            promptCache.put(name, content);
            logger.info("Loaded prompt template: {} ({} chars)", name, content.length());
        } catch (IOException e) {
            logger.error("Failed to load prompt template: {}", path, e);
            throw new RuntimeException("Could not load prompt template: " + path, e);
        }
    }

    /**
     * Get a prompt template by name with placeholder replacement.
     *
     * @param name         the prompt name (e.g., "requirement_agent")
     * @param placeholders key-value pairs for replacement (e.g., "requirementType" -> "NEW")
     * @return the prompt with placeholders replaced
     */
    public String getPrompt(String name, Map<String, String> placeholders) {
        String template = promptCache.get(name);
        if (template == null) {
            throw new IllegalArgumentException("Unknown prompt template: " + name);
        }
        String result = template;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            result = result.replace("{{" + entry.getKey() + "}}", entry.getValue());
        }
        return result;
    }

    /**
     * Get a prompt template by name without placeholder replacement.
     */
    public String getPrompt(String name) {
        return getPrompt(name, Map.of());
    }
}
