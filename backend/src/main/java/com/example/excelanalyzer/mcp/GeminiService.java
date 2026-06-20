package com.example.excelanalyzer.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;

@Service
public class GeminiService {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    @Value("${GEMINI_API_KEY:}")
    private String apiKey;

    public GeminiService(RestClient.Builder restClientBuilder, ObjectMapper objectMapper) {
        this.restClient = restClientBuilder.build();
        this.objectMapper = objectMapper;
    }

    /**
     * Call the Gemini generateContent API.
     */
    public String generateContent(String systemInstruction, String prompt) {
        String activeApiKey = apiKey;
        if (activeApiKey == null || activeApiKey.trim().isEmpty()) {
            activeApiKey = System.getenv("GEMINI_API_KEY");
        }
        if (activeApiKey == null || activeApiKey.trim().isEmpty()) {
            activeApiKey = loadApiKeyFromDotEnv();
        }

        if (activeApiKey == null || activeApiKey.trim().isEmpty()) {
            throw new IllegalStateException("GEMINI_API_KEY is not configured. Please set the GEMINI_API_KEY environment variable or define it in a .env file.");
        }

        String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-flash-latest:generateContent";

        // Construct request payload
        Map<String, Object> requestBody = new LinkedHashMap<>();

        if (systemInstruction != null && !systemInstruction.trim().isEmpty()) {
            Map<String, Object> sysInstr = new HashMap<>();
            sysInstr.put("parts", List.of(Map.of("text", systemInstruction)));
            requestBody.put("systemInstruction", sysInstr);
        }

        Map<String, Object> userContent = new LinkedHashMap<>();
        userContent.put("parts", List.of(Map.of("text", prompt)));
        requestBody.put("contents", List.of(userContent));

        try {
            String jsonResponse = restClient.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("x-goog-api-key", activeApiKey)
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);

            // Parse response to extract generated content
            Map<String, Object> respMap = objectMapper.readValue(jsonResponse, Map.class);
            List<Map<String, Object>> candidates = (List<Map<String, Object>>) respMap.get("candidates");
            if (candidates != null && !candidates.isEmpty()) {
                Map<String, Object> firstCandidate = candidates.getFirst();
                Map<String, Object> contentMap = (Map<String, Object>) firstCandidate.get("content");
                if (contentMap != null) {
                    List<Map<String, Object>> parts = (List<Map<String, Object>>) contentMap.get("parts");
                    if (parts != null && !parts.isEmpty()) {
                        return (String) parts.getFirst().get("text");
                    }
                }
            }
            throw new RuntimeException("Could not extract generated text from Gemini API response: " + jsonResponse);
        } catch (Exception e) {
            throw new RuntimeException("Gemini API call failed: " + e.getMessage(), e);
        }
    }

    private String loadApiKeyFromDotEnv() {
        List<String> pathsToCheck = List.of(".env", "backend/.env", "../.env");
        for (String path : pathsToCheck) {
            File envFile = new File(path);
            if (envFile.exists() && envFile.isFile()) {
                try {
                    List<String> lines = Files.readAllLines(envFile.toPath());
                    for (String line : lines) {
                        line = line.trim();
                        if (line.startsWith("GEMINI_API_KEY=")) {
                            String value = line.substring("GEMINI_API_KEY=".length()).trim();
                            if (value.startsWith("\"") && value.endsWith("\"")) {
                                value = value.substring(1, value.length() - 1);
                            } else if (value.startsWith("'") && value.endsWith("'")) {
                                value = value.substring(1, value.length() - 1);
                            }
                            return value;
                        }
                    }
                } catch (IOException e) {
                    // Ignore and try next path
                }
            }
        }
        return null;
    }
}
