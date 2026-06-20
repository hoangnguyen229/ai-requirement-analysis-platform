package com.example.excelanalyzer.controller;

import com.example.excelanalyzer.agent.Agents;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class DocumentController {

    private final Agents.CoordinatorAgent coordinatorAgent;

    public DocumentController(Agents.CoordinatorAgent coordinatorAgent) {
        this.coordinatorAgent = coordinatorAgent;
    }

    @PostMapping("/analyze")
    public ResponseEntity<Map<String, Object>> analyzeDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam("requirementType") String requirementType) {
        
        Map<String, Object> response = new HashMap<>();

        if (file.isEmpty()) {
            response.put("status", "error");
            response.put("message", "Uploaded file is empty");
            return ResponseEntity.badRequest().body(response);
        }

        if (!"NEW".equalsIgnoreCase(requirementType) && !"UPDATE".equalsIgnoreCase(requirementType)) {
            response.put("status", "error");
            response.put("message", "Invalid requirementType. Supported types: NEW, UPDATE");
            return ResponseEntity.badRequest().body(response);
        }

        File tempFile = null;
        try {
            String originalFilename = file.getOriginalFilename();
            String extension = "";
            if (originalFilename != null && originalFilename.contains(".")) {
                extension = originalFilename.substring(originalFilename.lastIndexOf(".")).toLowerCase();
            }

            // Verify supported file formats in controller level
            if (!".xlsx".equals(extension) && !".docx".equals(extension) && !".txt".equals(extension) && !".md".equals(extension)) {
                response.put("status", "error");
                
                if (".xls".equals(extension) || ".doc".equals(extension) || ".pdf".equals(extension)) {
                    response.put("message", extension.toUpperCase() + " files are not supported in this MVP. Supported formats are: .xlsx, .docx, .txt, .md. (" + extension.toUpperCase() + " support is planned for future improvements).");
                } else {
                    response.put("message", "Unsupported file type. Supported formats are: .xlsx, .docx, .txt, .md.");
                }
                return ResponseEntity.badRequest().body(response);
            }

            tempFile = File.createTempFile("doc_upload_", extension);
            Files.copy(file.getInputStream(), tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

            // Execute Multi-Agent Pipeline
            Agents.AnalysisResult result = coordinatorAgent.orchestrate(tempFile.getAbsolutePath(), requirementType.toUpperCase());

            response.put("status", "success");
            response.put("fileName", originalFilename);
            response.put("documentType", result.documentType());
            response.put("highlightsDetected", result.highlightsDetected());
            response.put("report", result.report());
            response.put("trace", result.trace());
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", "Document analysis failed: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        } finally {
            // Clean up temporary file
            if (tempFile != null && tempFile.exists()) {
                try {
                    Files.delete(tempFile.toPath());
                } catch (IOException e) {
                    System.err.println("Failed to delete temp file: " + tempFile.getAbsolutePath() + " - " + e.getMessage());
                }
            }
        }
    }
}
