package com.example.excelanalyzer.parser;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

public interface DocumentParser {
    Map<String, Object> getMetadata(File file) throws IOException;
    List<Map<String, Object>> getChangedSections(File file) throws IOException;
    String getFullContent(File file) throws IOException;
    List<Map<String, Object>> getHighlightedContent(File file) throws IOException;
}
