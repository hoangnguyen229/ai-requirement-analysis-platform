package com.example.excelanalyzer.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.ResourceAccessException;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class GeminiService {

    private static final Logger logger = LoggerFactory.getLogger(GeminiService.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    @Value("${GEMINI_API_KEY:}")
    private String apiKey;

    @Value("${gemini.model:gemini-2.5-flash}")
    private String modelName;

    public GeminiService(RestClient.Builder restClientBuilder, ObjectMapper objectMapper) {
        this.restClient = restClientBuilder.build();
        this.objectMapper = objectMapper;
    }

    /**
     * Call the Gemini generateContent API.
     */
    public String generateContent(String systemInstruction, String prompt) {
        String activeApiKey = System.getenv("GEMINI_API_KEY");
        if (activeApiKey == null || activeApiKey.trim().isEmpty()) {
            activeApiKey = apiKey; // fallback to spring injected @Value
        }

        if (activeApiKey == null || activeApiKey.trim().isEmpty()) {
            throw new IllegalStateException("GEMINI_API_KEY is not configured. Please set the GEMINI_API_KEY environment variable.");
        }

        String url = "https://generativelanguage.googleapis.com/v1beta/models/" + modelName + ":generateContent";
        logger.info("Calling Gemini API with model: {}", modelName);

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

        int maxRetries = 5;
        long backoffMs = 2000; // Start with 2 seconds backoff
        double multiplier = 2.0;
        double jitter = 0.2; // 20% jitter
        Random random = new Random();

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
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
                boolean isTransient = false;
                String errorDetails = e.getMessage();
                long apiSuggestedDelay = -1;

                if (e instanceof RestClientResponseException restException) {
                    int statusCode = restException.getStatusCode().value();
                    String responseBody = restException.getResponseBodyAsString();
                    errorDetails = "HTTP " + statusCode + " - " + responseBody;
                    if (statusCode == 429 || statusCode == 503 || statusCode == 504 || statusCode == 500) {
                        isTransient = true;
                    }
                    // Parse retryDelay from 429 response body
                    if (statusCode == 429) {
                        apiSuggestedDelay = parseRetryDelay(responseBody);
                    }
                } else if (e instanceof ResourceAccessException || e instanceof IOException) {
                    isTransient = true;
                }

                if (isTransient && attempt < maxRetries) {
                    long currentBackoff;
                    if (apiSuggestedDelay > 0) {
                        // Use the delay suggested by the Gemini API (with small jitter)
                        currentBackoff = apiSuggestedDelay + (long) (random.nextDouble() * 2000);
                        logger.warn("Gemini API returned 429 (attempt {}/{}). Using API-suggested retry delay: {}ms. Details: {}",
                                attempt, maxRetries, currentBackoff, errorDetails);
                    } else {
                        // Fallback to exponential backoff
                        currentBackoff = (long) (backoffMs * (1 - jitter + random.nextDouble() * 2 * jitter));
                        logger.warn("Gemini API call returned transient error (attempt {}/{}). Retrying in {}ms: {}",
                                attempt, maxRetries, currentBackoff, errorDetails);
                    }
                    try {
                        Thread.sleep(currentBackoff);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Gemini API call execution interrupted during backoff retry", ie);
                    }
                    backoffMs = (long) (backoffMs * multiplier);
                } else {
                    logger.error("Gemini API call failed permanently after {} attempts: {}", attempt, errorDetails, e);
                    throw new RuntimeException("Gemini API call failed: " + errorDetails, e);
                }
            }
        }
        throw new RuntimeException("Gemini API call failed: Max retries exceeded");
    }

    /**
     * Parse the retryDelay from a Gemini 429 error response JSON.
     * Looks for patterns like "retryDelay": "17s" or "Please retry in 34.72s"
     * 
     * @return delay in milliseconds, or -1 if not parseable
     */
    private long parseRetryDelay(String responseBody) {
        try {
            // Try to parse structured retryDelay from JSON (e.g., "retryDelay": "17s")
            Map<String, Object> errorResponse = objectMapper.readValue(responseBody, Map.class);
            Map<String, Object> error = (Map<String, Object>) errorResponse.get("error");
            if (error != null) {
                List<Map<String, Object>> details = (List<Map<String, Object>>) error.get("details");
                if (details != null) {
                    for (Map<String, Object> detail : details) {
                        String type = (String) detail.get("@type");
                        if (type != null && type.contains("RetryInfo")) {
                            String retryDelay = (String) detail.get("retryDelay");
                            if (retryDelay != null) {
                                return parseDelayString(retryDelay);
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) {
            // Fall through to regex-based parsing
        }

        // Fallback: try regex on the message text (e.g., "Please retry in 34.721761709s")
        Pattern pattern = Pattern.compile("retry in ([\\d.]+)s", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(responseBody);
        if (matcher.find()) {
            try {
                double seconds = Double.parseDouble(matcher.group(1));
                return (long) (seconds * 1000);
            } catch (NumberFormatException ignored) {
            }
        }

        return -1;
    }

    /**
     * Parse a duration string like "17s", "34.72s", "1m30s" into milliseconds.
     */
    private long parseDelayString(String delay) {
        if (delay == null || delay.isEmpty()) return -1;
        
        // Handle simple seconds format: "17s", "34.72s"
        Pattern secPattern = Pattern.compile("([\\d.]+)s");
        Matcher secMatcher = secPattern.matcher(delay);
        if (secMatcher.find()) {
            try {
                double seconds = Double.parseDouble(secMatcher.group(1));
                return (long) (seconds * 1000);
            } catch (NumberFormatException ignored) {
            }
        }
        return -1;
    }

}
