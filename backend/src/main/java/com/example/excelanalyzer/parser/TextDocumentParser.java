package com.example.excelanalyzer.parser;

import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;

@Component
public class TextDocumentParser implements DocumentParser {

    @Override
    public Map<String, Object> getMetadata(File file) throws IOException {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("fileName", file.getName());
        meta.put("fileType", "TXT");
        meta.put("fileSize", file.length());
        
        String content = getFullContent(file);
        meta.put("characterCount", content.length());
        meta.put("wordCount", content.split("\\s+").length);
        meta.put("hasHighlights", false);
        return meta;
    }

    @Override
    public List<Map<String, Object>> getChangedSections(File file) throws IOException {
        return Collections.emptyList();
    }

    @Override
    public String getFullContent(File file) throws IOException {
        return Files.readString(file.toPath());
    }

    @Override
    public List<Map<String, Object>> getHighlightedContent(File file) throws IOException {
        return Collections.emptyList();
    }
}
