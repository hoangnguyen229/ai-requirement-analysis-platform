package com.example.excelanalyzer.mcp;

import com.example.excelanalyzer.parser.DocumentParser;
import com.example.excelanalyzer.parser.ParserRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.util.*;

@RestController
@RequestMapping("/api/mcp")
public class DocumentMcpServer {

    private final ParserRegistry parserRegistry;
    private final ObjectMapper objectMapper;
    private volatile String activeFilePath;

    public DocumentMcpServer(ParserRegistry parserRegistry, ObjectMapper objectMapper) {
        this.parserRegistry = parserRegistry;
        this.objectMapper = objectMapper;
    }

    // JSON-RPC DTOs
    public record JsonRpcRequest(String jsonrpc, String method, Map<String, Object> params, Object id) {}
    public record JsonRpcResponse(String jsonrpc, Object result, Object error, Object id) {}
    public record McpTool(String name, String description, Map<String, Object> inputSchema) {}
    public record ToolListResult(List<McpTool> tools) {}
    public record McpContent(String type, String text) {}
    public record ToolCallResult(List<McpContent> content) {}
    public record JsonRpcError(int code, String message, Object data) {}

    @PostMapping("/rpc")
    public JsonRpcResponse handleRpc(@RequestBody JsonRpcRequest request) {
        if (request == null || !"2.0".equals(request.jsonrpc())) {
            return new JsonRpcResponse("2.0", null, new JsonRpcError(-32600, "Invalid Request", null), null);
        }

        try {
            Object result = null;
            switch (request.method()) {
                case "initialize":
                    result = handleInitialize(request.params());
                    break;
                case "tools/list":
                    result = handleToolsList();
                    break;
                case "tools/call":
                    result = handleToolsCall(request.params());
                    break;
                default:
                    return new JsonRpcResponse("2.0", null, new JsonRpcError(-32601, "Method not found: " + request.method(), null), request.id());
            }
            return new JsonRpcResponse("2.0", result, null, request.id());
        } catch (Exception e) {
            return new JsonRpcResponse("2.0", null, new JsonRpcError(-32603, "Internal error: " + e.getMessage(), null), request.id());
        }
    }

    private Map<String, Object> handleInitialize(Map<String, Object> params) {
        if (params != null && params.containsKey("filePath")) {
            this.activeFilePath = (String) params.get("filePath");
        }
        Map<String, Object> response = new HashMap<>();
        response.put("protocolVersion", "2024-11-05");
        response.put("capabilities", Collections.emptyMap());
        Map<String, Object> serverInfo = new HashMap<>();
        serverInfo.put("name", "Document MCP Server");
        serverInfo.put("version", "1.0.0");
        response.put("serverInfo", serverInfo);
        return response;
    }

    private ToolListResult handleToolsList() {
        List<McpTool> tools = new ArrayList<>();

        tools.add(new McpTool(
            "getDocumentMetadata",
            "Returns document metadata (file name, type, statistics, highlight status) for the active file.",
            createEmptySchema()
        ));

        tools.add(new McpTool(
            "getChangedSections",
            "Returns rows/paragraphs that contain yellow highlights representing modifications. Empty list for plaintext/markdown files.",
            createEmptySchema()
        ));

        tools.add(new McpTool(
            "getFullRequirementContent",
            "Returns the raw full text content of the active requirement document.",
            createEmptySchema()
        ));

        tools.add(new McpTool(
            "getHighlightedContent",
            "Returns only the highlighted text spans/runs or cells from the document.",
            createEmptySchema()
        ));

        return new ToolListResult(tools);
    }

    private ToolCallResult handleToolsCall(Map<String, Object> params) throws Exception {
        if (activeFilePath == null || activeFilePath.isEmpty()) {
            throw new IllegalStateException("No document initialized. Call 'initialize' with 'filePath' first.");
        }

        File file = new File(activeFilePath);
        if (!file.exists()) {
            throw new IllegalArgumentException("Active document does not exist at path: " + activeFilePath);
        }

        String toolName = (String) params.get("name");
        if (toolName == null) {
            throw new IllegalArgumentException("Missing 'name' in tools/call parameters");
        }

        // Get parser from registry
        DocumentParser parser = parserRegistry.getParser(file);

        String resultText;
        switch (toolName) {
            case "getDocumentMetadata":
                Map<String, Object> metadata = parser.getMetadata(file);
                resultText = objectMapper.writeValueAsString(metadata);
                break;
            case "getChangedSections":
                List<Map<String, Object>> changed = parser.getChangedSections(file);
                resultText = objectMapper.writeValueAsString(changed);
                break;
            case "getFullRequirementContent":
                String content = parser.getFullContent(file);
                resultText = content; // Return raw text directly
                break;
            case "getHighlightedContent":
                List<Map<String, Object>> highlights = parser.getHighlightedContent(file);
                resultText = objectMapper.writeValueAsString(highlights);
                break;
            default:
                throw new IllegalArgumentException("Unknown tool name: " + toolName);
        }

        List<McpContent> content = new ArrayList<>();
        content.add(new McpContent("text", resultText));
        return new ToolCallResult(content);
    }

    private Map<String, Object> createEmptySchema() {
        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");
        schema.put("properties", Collections.emptyMap());
        return schema;
    }
}
