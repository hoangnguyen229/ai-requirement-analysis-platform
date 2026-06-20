package com.example.excelanalyzer.parser;

import org.apache.poi.xwpf.usermodel.*;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;

@Component
public class DocxDocumentParser implements DocumentParser {

    @Override
    public Map<String, Object> getMetadata(File file) throws IOException {
        Map<String, Object> meta = new LinkedHashMap<>();
        try (XWPFDocument doc = new XWPFDocument(new FileInputStream(file))) {
            meta.put("fileName", file.getName());
            meta.put("fileType", "DOCX");
            meta.put("paragraphCount", doc.getParagraphs().size());
            meta.put("tableCount", doc.getTables().size());
            
            boolean hasHighlights = false;
            for (XWPFParagraph p : doc.getParagraphs()) {
                for (XWPFRun r : p.getRuns()) {
                    if (isHighlighted(r)) {
                        hasHighlights = true;
                        break;
                    }
                }
                if (hasHighlights) break;
            }
            
            if (!hasHighlights) {
                for (XWPFTable t : doc.getTables()) {
                    for (XWPFTableRow row : t.getRows()) {
                        for (XWPFTableCell cell : row.getTableCells()) {
                            for (XWPFParagraph cp : cell.getParagraphs()) {
                                for (XWPFRun cr : cp.getRuns()) {
                                    if (isHighlighted(cr)) {
                                        hasHighlights = true;
                                        break;
                                    }
                                }
                                if (hasHighlights) break;
                            }
                            if (hasHighlights) break;
                        }
                        if (hasHighlights) break;
                    }
                    if (hasHighlights) break;
                }
            }
            
            meta.put("hasHighlights", hasHighlights);
        }
        return meta;
    }

    @Override
    public List<Map<String, Object>> getChangedSections(File file) throws IOException {
        List<Map<String, Object>> changed = new ArrayList<>();
        try (XWPFDocument doc = new XWPFDocument(new FileInputStream(file))) {
            List<XWPFParagraph> paragraphs = doc.getParagraphs();
            for (int i = 0; i < paragraphs.size(); i++) {
                XWPFParagraph p = paragraphs.get(i);
                boolean hasHighlight = false;
                StringBuilder highlightedText = new StringBuilder();
                for (XWPFRun r : p.getRuns()) {
                    if (isHighlighted(r)) {
                        hasHighlight = true;
                        highlightedText.append(r.text());
                    }
                }
                if (hasHighlight) {
                    Map<String, Object> sec = new LinkedHashMap<>();
                    sec.put("type", "paragraph");
                    sec.put("index", i + 1);
                    sec.put("text", p.getText());
                    sec.put("highlightedText", highlightedText.toString());
                    changed.add(sec);
                }
            }

            List<XWPFTable> tables = doc.getTables();
            for (int t = 0; t < tables.size(); t++) {
                XWPFTable table = tables.get(t);
                for (int r = 0; r < table.getRows().size(); r++) {
                    XWPFTableRow row = table.getRow(r);
                    boolean rowHasHighlight = false;
                    Map<String, String> rowData = new LinkedHashMap<>();
                    List<String> highlightedCols = new ArrayList<>();

                    for (int c = 0; c < row.getTableCells().size(); c++) {
                        XWPFTableCell cell = row.getCell(c);
                        String header = "Col " + (c + 1);
                        if (r > 0 && table.getRow(0) != null) {
                            String headerText = table.getRow(0).getCell(c).getText().trim();
                            if (!headerText.isEmpty()) {
                                header = headerText;
                            }
                        }
                        rowData.put(header, cell.getText());

                        boolean cellHasHighlight = false;
                        for (XWPFParagraph cp : cell.getParagraphs()) {
                            for (XWPFRun cr : cp.getRuns()) {
                                if (isHighlighted(cr)) {
                                    cellHasHighlight = true;
                                    break;
                                }
                            }
                        }
                        if (cellHasHighlight) {
                            rowHasHighlight = true;
                            highlightedCols.add(header);
                        }
                    }

                    if (rowHasHighlight) {
                        Map<String, Object> sec = new LinkedHashMap<>();
                        sec.put("type", "table-row");
                        sec.put("tableIndex", t + 1);
                        sec.put("rowIndex", r + 1);
                        sec.put("rowData", rowData);
                        sec.put("highlightedColumns", highlightedCols);
                        changed.add(sec);
                    }
                }
            }
        }
        return changed;
    }

    @Override
    public String getFullContent(File file) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (XWPFDocument doc = new XWPFDocument(new FileInputStream(file))) {
            for (XWPFParagraph p : doc.getParagraphs()) {
                sb.append(p.getText()).append("\n");
            }
            for (XWPFTable t : doc.getTables()) {
                sb.append("[Table ").append(t.hashCode() & 0xffff).append("]\n");
                for (XWPFTableRow r : t.getRows()) {
                    List<String> rowCells = new ArrayList<>();
                    for (XWPFTableCell c : r.getTableCells()) {
                        rowCells.add(c.getText().trim());
                    }
                    sb.append(" | ").append(String.join(" | ", rowCells)).append(" |\n");
                }
                sb.append("\n");
            }
        }
        return sb.toString();
    }

    @Override
    public List<Map<String, Object>> getHighlightedContent(File file) throws IOException {
        List<Map<String, Object>> highlights = new ArrayList<>();
        try (XWPFDocument doc = new XWPFDocument(new FileInputStream(file))) {
            List<XWPFParagraph> paragraphs = doc.getParagraphs();
            for (int i = 0; i < paragraphs.size(); i++) {
                XWPFParagraph p = paragraphs.get(i);
                for (XWPFRun r : p.getRuns()) {
                    if (isHighlighted(r)) {
                        Map<String, Object> hl = new LinkedHashMap<>();
                        hl.put("type", "run");
                        hl.put("source", "paragraph");
                        hl.put("index", i + 1);
                        hl.put("text", r.text());
                        highlights.add(hl);
                    }
                }
            }
            List<XWPFTable> tables = doc.getTables();
            for (int t = 0; t < tables.size(); t++) {
                XWPFTable table = tables.get(t);
                for (int r = 0; r < table.getRows().size(); r++) {
                    XWPFTableRow row = table.getRow(r);
                    for (int c = 0; c < row.getTableCells().size(); c++) {
                        XWPFTableCell cell = row.getCell(c);
                        for (XWPFParagraph cp : cell.getParagraphs()) {
                            for (XWPFRun cr : cp.getRuns()) {
                                if (isHighlighted(cr)) {
                                    Map<String, Object> hl = new LinkedHashMap<>();
                                    hl.put("type", "run");
                                    hl.put("source", "table-cell");
                                    hl.put("tableIndex", t + 1);
                                    hl.put("rowIndex", r + 1);
                                    hl.put("cellIndex", c + 1);
                                    hl.put("text", cr.text());
                                    highlights.add(hl);
                                }
                            }
                        }
                    }
                }
            }
        }
        return highlights;
    }

    private boolean isHighlighted(XWPFRun run) {
        if (run == null || run.getCTR() == null) return false;
        String xml = run.getCTR().toString();
        return xml.contains("<w:highlight") && 
               (xml.contains("w:val=\"yellow\"") || xml.contains("w:val=\"YELLOW\"") || xml.contains("w:val=\"lightYellow\""));
    }
}
