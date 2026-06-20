package com.example.excelanalyzer.mcp;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.*;

@Service
public class McpClientService {

    private final ObjectMapper objectMapper;
    private final RestClient restClient;
    private final ServletWebServerApplicationContext webServerAppCtx;

    @Value("${server.port:8080}")
    private int configuredPort;

    public McpClientService(ObjectMapper objectMapper, RestClient.Builder restClientBuilder, ServletWebServerApplicationContext webServerAppCtx) {
        this.objectMapper = objectMapper;
        this.restClient = restClientBuilder.build();
        this.webServerAppCtx = webServerAppCtx;
    }

    private String getServerUrl() {
        int port = webServerAppCtx != null && webServerAppCtx.getWebServer() != null 
                ? webServerAppCtx.getWebServer().getPort() 
                : configuredPort;
        return "http://localhost:" + port + "/api/mcp/rpc";
    }

    // JSON-RPC Request models matching server DTOs
    private record RpcRequest(String jsonrpc, String method, Map<String, Object> params, String id) {}
    private record RpcResponse(String jsonrpc, Map<String, Object> result, Map<String, Object> error, String id) {}

    /**
     * Initializes the MCP connection and registers the active Excel file.
     */
    public void initialize(String filePath) throws Exception {
        Map<String, Object> params = new HashMap<>();
        params.put("filePath", filePath);

        RpcRequest request = new RpcRequest("2.0", "initialize", params, UUID.randomUUID().toString());
        RpcResponse response = postRpc(request);

        if (response.error() != null) {
            throw new RuntimeException("MCP initialize failed: " + response.error().get("message"));
        }
    }

    /**
     * Invokes the getDocumentMetadata tool.
     */
    public Map<String, Object> getDocumentMetadata() throws Exception {
        String jsonText = callTool("getDocumentMetadata");
        return objectMapper.readValue(jsonText, new TypeReference<Map<String, Object>>() {});
    }

    /**
     * Invokes the getChangedSections tool.
     */
    public List<Map<String, Object>> getChangedSections() throws Exception {
        String jsonText = callTool("getChangedSections");
        return objectMapper.readValue(jsonText, new TypeReference<List<Map<String, Object>>>() {});
    }

    /**
     * Invokes the getFullRequirementContent tool.
     */
    public String getFullRequirementContent() throws Exception {
        return callTool("getFullRequirementContent");
    }

    /**
     * Invokes the getHighlightedContent tool.
     */
    public List<Map<String, Object>> getHighlightedContent() throws Exception {
        String jsonText = callTool("getHighlightedContent");
        return objectMapper.readValue(jsonText, new TypeReference<List<Map<String, Object>>>() {});
    }

    private String callTool(String toolName) throws Exception {
        Map<String, Object> params = new HashMap<>();
        params.put("name", toolName);
        params.put("arguments", Collections.emptyMap());

        RpcRequest request = new RpcRequest("2.0", "tools/call", params, UUID.randomUUID().toString());
        RpcResponse response = postRpc(request);

        if (response.error() != null) {
            throw new RuntimeException("MCP tools/call failed for " + toolName + ": " + response.error().get("message"));
        }

        Map<String, Object> result = response.result();
        if (result == null || !result.containsKey("content")) {
            throw new RuntimeException("Invalid response schema from MCP Server: missing 'result.content'");
        }

        List<Map<String, Object>> contentList = (List<Map<String, Object>>) result.get("content");
        if (contentList == null || contentList.isEmpty()) {
            return "[]";
        }

        return (String) contentList.getFirst().get("text");
    }

    private RpcResponse postRpc(RpcRequest request) {
        String url = getServerUrl();
        return restClient.post()
                .uri(url)
                .body(request)
                .retrieve()
                .body(RpcResponse.class);
    }
}
