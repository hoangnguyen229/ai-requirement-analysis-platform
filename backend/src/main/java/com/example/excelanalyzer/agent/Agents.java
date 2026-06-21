package com.example.excelanalyzer.agent;

import com.example.excelanalyzer.mcp.GeminiService;
import com.example.excelanalyzer.mcp.McpClientService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class Agents {

    private static final Logger logger = LoggerFactory.getLogger(Agents.class);

    private final McpClientService mcpClient;
    private final GeminiService geminiService;

    public Agents(McpClientService mcpClient, GeminiService geminiService) {
        this.mcpClient = mcpClient;
        this.geminiService = geminiService;
    }

    public record AnalysisResult(String documentType, boolean highlightsDetected, String report, List<String> trace) {}

    /**
     * Requirement Agent: Analyzes raw data and extracts summaries & business rules.
     */
    @Service
    public static class RequirementAgent {
        private final GeminiService geminiService;

        public RequirementAgent(GeminiService geminiService) {
            this.geminiService = geminiService;
        }

        public String analyze(String rawDocumentData, String requirementType, String docType) {
            String systemInstruction = """
                You are a senior Requirements Analyst Agent. Your task is to analyze requirement document contents.
                The requirementType is: """ + requirementType + """
                The document type is: """ + docType + """

                Rules:
                - If requirementType is 'NEW', you must analyze the entire document text as a completely new feature or system implementation.
                - If requirementType is 'UPDATE', you must focus strictly on the yellow-highlighted modifications or changed sections.
                  - Note: For plain text (.txt) and Markdown (.md) documents, yellow highlight detection is NOT available.
                  - If highlight detection is not available and requirementType is 'UPDATE', you must analyze the entire document and clearly list that "Highlight detection is unavailable for plain text/markdown files" under the "Clarifications Needed" section.
                - Do not assume implementation technologies.
                - Do not invent missing business rules.
                - If information is unclear or missing, list it under "Clarifications Needed".

                Your goal is to:
                1. Identify what requirements changed or are new.
                2. Write a clear "Requirement Summary" in markdown.
                3. Extract specific "Business Rules" in markdown.

                Format your output clearly in markdown under exactly these sections:

                ## Requirement Summary

                ## Business Rules

                ## Clarifications Needed
                """;
            return geminiService.generateContent(systemInstruction, rawDocumentData);
        }
    }

    /**
     * Task Agent: Generates tasks, impact, and test cases.
     */
    @Service
    public static class TaskAgent {
        private final GeminiService geminiService;

        public TaskAgent(GeminiService geminiService) {
            this.geminiService = geminiService;
        }

        public String analyze(String requirementAnalysis) {
            String systemInstruction = """
                You are a senior Software Engineer and QA Architect Agent.

                You must generate implementation analysis based ONLY on the following technology stack:

                Frontend:
                - Angular

                Backend:
                - Java 21
                - Spring Boot 3

                Restrictions:
                - Do NOT mention React, Vue, Node.js, Express, Axios, Redis, or database unless the requirement explicitly says so.
                - Do NOT invent new infrastructure.
                - Do NOT assume authentication, database, cache, or external services unless they are clearly required by the requirement analysis.
                - If required technical details are missing, write them under "Clarifications Needed".

                Based on the provided Requirement Analysis, perform a technical breakdown:

                1. Impact Analysis
                - Frontend impact using Angular
                - Backend impact using Java 21 and Spring Boot 3
                - API impact, if any

                2. Development Tasks
                - Frontend tasks
                - Backend tasks
                - Integration tasks

                3. Test Cases
                - Positive cases
                - Negative cases
                - Boundary cases

                Format your output clearly in markdown under exactly these sections:

                ## Impact Analysis

                ## Development Tasks

                ## Test Cases

                ## Clarifications Needed
                """;

            return geminiService.generateContent(systemInstruction, requirementAnalysis);
        }
    }

    /**
     * Coordinator Agent: Orchestrates the pipeline from MCP data to final report.
     */
    @Service
    public static class CoordinatorAgent {
        private final McpClientService mcpClient;
        private final RequirementAgent requirementAgent;
        private final TaskAgent taskAgent;
        private final long interAgentDelayMs;

        public CoordinatorAgent(McpClientService mcpClient, RequirementAgent requirementAgent, TaskAgent taskAgent,
                                @Value("${gemini.inter-agent-delay-ms:2000}") long interAgentDelayMs) {
            this.mcpClient = mcpClient;
            this.requirementAgent = requirementAgent;
            this.taskAgent = taskAgent;
            this.interAgentDelayMs = interAgentDelayMs;
        }

        public AnalysisResult orchestrate(String filePath, String requirementType) throws Exception {
            List<String> trace = new ArrayList<>();
            String startTime = java.time.LocalTime.now().toString().substring(0, 8);
            trace.add(String.format("%s [Coordinator] Initiating document analysis session for path: %s (Type: %s)", 
                    startTime, filePath, requirementType));

            // 1. Initialize MCP Session
            trace.add(String.format("%s [McpClient] Initializing Document MCP Session...", 
                    java.time.LocalTime.now().toString().substring(0, 8)));
            mcpClient.initialize(filePath);

            // 2. Fetch Document Metadata
            trace.add(String.format("%s [McpClient] Fetching document metadata...", 
                    java.time.LocalTime.now().toString().substring(0, 8)));
            Map<String, Object> metadata = mcpClient.getDocumentMetadata();
            String docType = (String) metadata.getOrDefault("fileType", "UNKNOWN");
            boolean hasHighlights = (Boolean) metadata.getOrDefault("hasHighlights", false);
            
            trace.add(String.format("%s [DocumentMcpServer] Metadata retrieved: [Type: %s, Highlights Detected: %s]", 
                    java.time.LocalTime.now().toString().substring(0, 8), docType, hasHighlights));

            String rawPayload = "";

            // 3. Conditional Data Retrieval based on requirementType & file type
            if ("UPDATE".equalsIgnoreCase(requirementType)) {
                if ("EXCEL".equalsIgnoreCase(docType) || "DOCX".equalsIgnoreCase(docType)) {
                    trace.add(String.format("%s [McpClient] Querying changed sections and highlights from MCP Server...", 
                            java.time.LocalTime.now().toString().substring(0, 8)));

                    List<Map<String, Object>> changedSections = mcpClient.getChangedSections();
                    List<Map<String, Object>> highlightedContent = mcpClient.getHighlightedContent();

                    trace.add(String.format("%s [DocumentMcpServer] Retrieved %d changed segments and %d highlighted items.", 
                            java.time.LocalTime.now().toString().substring(0, 8), changedSections.size(), highlightedContent.size()));

                    rawPayload = formatChangedPayload(metadata, changedSections, highlightedContent);
                } else {
                    // Plain text / Markdown
                    trace.add(String.format("%s [McpClient] Highlight detection is unavailable for plain text/markdown files. Fetching full content...", 
                            java.time.LocalTime.now().toString().substring(0, 8)));

                    String fullContent = mcpClient.getFullRequirementContent();
                    rawPayload = "Note: Highlight detection is unavailable for " + docType + " document formats. Analyzing full document context for updates.\n\n" + fullContent;
                }
            } else {
                // NEW
                trace.add(String.format("%s [McpClient] requirementType is 'NEW'. Fetching full requirement content...", 
                        java.time.LocalTime.now().toString().substring(0, 8)));

                String fullContent = mcpClient.getFullRequirementContent();
                rawPayload = fullContent;
            }

            // 4. Invoke Requirement Agent
            trace.add(String.format("%s [RequirementAgent] Analyzing document content and extracting business rules...", 
                    java.time.LocalTime.now().toString().substring(0, 8)));
            String requirementReport = requirementAgent.analyze(rawPayload, requirementType, docType);
            trace.add(String.format("%s [RequirementAgent] Summarization and business rule extraction completed.", 
                    java.time.LocalTime.now().toString().substring(0, 8)));

            // 4.5. Inter-agent delay to avoid Gemini API rate limits (burst prevention)
            if (interAgentDelayMs > 0) {
                trace.add(String.format("%s [Coordinator] Applying inter-agent rate limit delay (%dms) before TaskAgent...",
                        java.time.LocalTime.now().toString().substring(0, 8), interAgentDelayMs));
                logger.info("Inter-agent delay: waiting {}ms before TaskAgent call to avoid rate limits", interAgentDelayMs);
                Thread.sleep(interAgentDelayMs);
            }

            // 5. Invoke Task Agent
            trace.add(String.format("%s [TaskAgent] Generating technical impact analysis, developer tasks, and test cases...", 
                    java.time.LocalTime.now().toString().substring(0, 8)));
            String taskReport = taskAgent.analyze(requirementReport);
            trace.add(String.format("%s [TaskAgent] Development tasks and QA test cases compiled.", 
                    java.time.LocalTime.now().toString().substring(0, 8)));

            // 6. Assemble Report
            trace.add(String.format("%s [Coordinator] Compiling final implementation markdown report...", 
                    java.time.LocalTime.now().toString().substring(0, 8)));
            String finalReport = "# Requirement Analysis & Implementation Report\n\n" +
                    "**Document File:** " + metadata.getOrDefault("fileName", "Unknown") + " (" + docType + ")\n" +
                    "**Requirement Type:** " + requirementType + "\n\n" +
                    requirementReport + "\n\n" + taskReport;

            trace.add(String.format("%s [Coordinator] Session complete.", 
                    java.time.LocalTime.now().toString().substring(0, 8)));

            return new AnalysisResult(docType, hasHighlights, finalReport, trace);
        }

        private String formatChangedPayload(Map<String, Object> metadata, List<Map<String, Object>> changedSections, List<Map<String, Object>> highlightedContent) {
            StringBuilder sb = new StringBuilder();
            sb.append("Document Metadata:\n");
            for (Map.Entry<String, Object> entry : metadata.entrySet()) {
                sb.append(" - ").append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
            }
            sb.append("\n");

            sb.append("Yellow Highlighted Content (Changed Spans/Runs):\n");
            if (highlightedContent.isEmpty()) {
                sb.append(" [No individual highlights detected]\n");
            } else {
                for (Map<String, Object> hl : highlightedContent) {
                    sb.append(" - ").append(hl.toString()).append("\n");
                }
            }
            sb.append("\n");

            sb.append("Changed Document Sections (Rows/Paragraphs containing highlights):\n");
            if (changedSections.isEmpty()) {
                sb.append(" [No modified sections detected]\n");
            } else {
                for (Map<String, Object> section : changedSections) {
                    sb.append(" - ").append(section.toString()).append("\n");
                }
            }
            return sb.toString();
        }
    }
}
