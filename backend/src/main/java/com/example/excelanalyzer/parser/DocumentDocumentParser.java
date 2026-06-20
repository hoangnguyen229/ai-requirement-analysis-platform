package com.example.excelanalyzer.parser;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;

@Component
public class DocumentDocumentParser implements DocumentParser {

    @Override
    public Map<String, Object> getMetadata(File file) throws IOException {
        validateXlsx(file);
        Map<String, Object> meta = new LinkedHashMap<>();
        List<String> sheets = getSheetNames(file);
        
        meta.put("fileName", file.getName());
        meta.put("fileType", "EXCEL");
        meta.put("sheetCount", sheets.size());
        meta.put("sheetNames", sheets);
        
        boolean hasHighlights = !getHighlightedContent(file).isEmpty();
        meta.put("hasHighlights", hasHighlights);
        return meta;
    }

    @Override
    public List<Map<String, Object>> getChangedSections(File file) throws IOException {
        validateXlsx(file);
        List<Map<String, Object>> changed = new ArrayList<>();
        try (Workbook workbook = WorkbookFactory.create(new FileInputStream(file))) {
            for (int s = 0; s < workbook.getNumberOfSheets(); s++) {
                Sheet sheet = workbook.getSheetAt(s);
                String sheetName = sheet.getSheetName();
                if (sheet.getPhysicalNumberOfRows() == 0) continue;

                List<String> headers = getHeaders(sheet);

                for (Row row : sheet) {
                    if (row.getRowNum() == 0) continue; // Skip header row

                    boolean hasYellow = false;
                    for (Cell cell : row) {
                        if (isCellYellow(cell)) {
                            hasYellow = true;
                            break;
                        }
                    }

                    if (hasYellow) {
                        Map<String, Object> sec = new LinkedHashMap<>();
                        sec.put("sheetName", sheetName);
                        sec.put("rowNum", row.getRowNum() + 1);
                        sec.put("headers", headers);
                        
                        Map<String, String> rowData = new LinkedHashMap<>();
                        List<String> yellowCols = new ArrayList<>();
                        for (int c = 0; c < headers.size(); c++) {
                            String header = headers.get(c);
                            Cell cell = row.getCell(c);
                            String val = getCellValueAsString(cell);
                            rowData.put(header, val);

                            if (cell != null && isCellYellow(cell)) {
                                yellowCols.add(header);
                            }
                        }
                        sec.put("rowData", rowData);
                        sec.put("highlightedColumns", yellowCols);
                        changed.add(sec);
                    }
                }
            }
        }
        return changed;
    }

    @Override
    public String getFullContent(File file) throws IOException {
        validateXlsx(file);
        StringBuilder sb = new StringBuilder();
        try (Workbook workbook = WorkbookFactory.create(new FileInputStream(file))) {
            for (int s = 0; s < workbook.getNumberOfSheets(); s++) {
                Sheet sheet = workbook.getSheetAt(s);
                sb.append("Sheet: ").append(sheet.getSheetName()).append("\n");
                
                List<String> headers = getHeaders(sheet);
                if (!headers.isEmpty()) {
                    sb.append("Headers: ").append(String.join(" | ", headers)).append("\n");
                }
                
                for (Row row : sheet) {
                    if (row.getRowNum() == 0 && !headers.isEmpty()) continue; // Skip header row if printed
                    List<String> vals = new ArrayList<>();
                    for (int c = 0; c < row.getLastCellNum(); c++) {
                        vals.add(getCellValueAsString(row.getCell(c)));
                    }
                    sb.append(String.join(" | ", vals)).append("\n");
                }
                sb.append("\n");
            }
        }
        return sb.toString();
    }

    @Override
    public List<Map<String, Object>> getHighlightedContent(File file) throws IOException {
        validateXlsx(file);
        List<Map<String, Object>> highlights = new ArrayList<>();
        try (Workbook workbook = WorkbookFactory.create(new FileInputStream(file))) {
            for (int s = 0; s < workbook.getNumberOfSheets(); s++) {
                Sheet sheet = workbook.getSheetAt(s);
                String sheetName = sheet.getSheetName();
                for (Row row : sheet) {
                    for (Cell cell : row) {
                        if (isCellYellow(cell)) {
                            Map<String, Object> map = new LinkedHashMap<>();
                            map.put("sheetName", sheetName);
                            map.put("address", cell.getAddress().formatAsString());
                            map.put("value", getCellValueAsString(cell));
                            map.put("hexColor", getCellHexColor(cell));
                            highlights.add(map);
                        }
                    }
                }
            }
        }
        return highlights;
    }

    private void validateXlsx(File file) {
        String name = file.getName().toLowerCase();
        if (name.endsWith(".xls")) {
            throw new IllegalArgumentException(".xls file format is not supported yet. Supported spreadsheet formats: .xlsx. (.xls support is planned for future improvements).");
        }
    }

    private List<String> getSheetNames(File file) throws IOException {
        List<String> sheets = new ArrayList<>();
        try (Workbook workbook = WorkbookFactory.create(new FileInputStream(file))) {
            for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                sheets.add(workbook.getSheetName(i));
            }
        }
        return sheets;
    }

    private List<String> getHeaders(Sheet sheet) {
        List<String> headers = new ArrayList<>();
        Row headerRow = sheet.getRow(0);
        if (headerRow != null) {
            for (int c = 0; c < headerRow.getLastCellNum(); c++) {
                Cell cell = headerRow.getCell(c);
                String val = getCellValueAsString(cell).trim();
                if (val.isEmpty()) {
                    val = "Column " + convertNumToColString(c);
                }
                headers.add(val);
            }
        }
        return headers;
    }

    private boolean isCellYellow(Cell cell) {
        if (cell == null) return false;
        CellStyle style = cell.getCellStyle();
        if (style == null) return false;
        if (style.getFillPattern() == FillPatternType.NO_FILL) return false;

        if (style instanceof XSSFCellStyle xssfStyle) {
            XSSFColor color = xssfStyle.getFillForegroundXSSFColor();
            if (color != null) {
                if (color.isIndexed()) {
                    short idx = color.getIndex();
                    if (idx == IndexedColors.YELLOW.getIndex() || idx == IndexedColors.LIGHT_YELLOW.getIndex() || idx == IndexedColors.LEMON_CHIFFON.getIndex()) {
                        return true;
                    }
                } else {
                    byte[] rgb = color.getRGB();
                    if (rgb != null && rgb.length >= 3) {
                        int r = rgb[rgb.length - 3] & 0xFF;
                        int g = rgb[rgb.length - 2] & 0xFF;
                        int b = rgb[rgb.length - 1] & 0xFF;
                        if (r > 200 && g > 185 && b < 180 && Math.abs(r - g) < 60) {
                            return true;
                        }
                    }
                    String hex = color.getARGBHex();
                    if (hex != null && isYellowHex(hex)) {
                        return true;
                    }
                }
            }
        }

        short colorIdx = style.getFillForegroundColor();
        return colorIdx == IndexedColors.YELLOW.getIndex() || colorIdx == IndexedColors.LIGHT_YELLOW.getIndex() || colorIdx == IndexedColors.LEMON_CHIFFON.getIndex();
    }

    private boolean isYellowHex(String hex) {
        if (hex == null) return false;
        if (hex.length() == 8) hex = hex.substring(2);
        else if (hex.length() == 9 && hex.startsWith("#")) hex = hex.substring(3);
        else if (hex.startsWith("#")) hex = hex.substring(1);
        if (hex.length() != 6) return false;
        try {
            int r = Integer.parseInt(hex.substring(0, 2), 16);
            int g = Integer.parseInt(hex.substring(2, 4), 16);
            int b = Integer.parseInt(hex.substring(4, 6), 16);
            return r > 200 && g > 185 && b < 180 && Math.abs(r - g) < 60;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private String getCellHexColor(Cell cell) {
        if (cell == null) return "";
        CellStyle style = cell.getCellStyle();
        if (style == null) return "";

        if (style instanceof XSSFCellStyle xssfStyle) {
            XSSFColor color = xssfStyle.getFillForegroundXSSFColor();
            if (color != null && !color.isIndexed()) {
                String hex = color.getARGBHex();
                if (hex != null) {
                    return hex.length() == 8 ? "#" + hex.substring(2) : "#" + hex;
                }
            }
        }

        short idx = style.getFillForegroundColor();
        IndexedColors indexed = IndexedColors.fromInt(idx);
        return indexed != null ? indexed.name() : "#" + Integer.toHexString(idx & 0xffff);
    }

    private String getCellValueAsString(Cell cell) {
        if (cell == null) return "";
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getDateCellValue().toString();
                }
                double val = cell.getNumericCellValue();
                return val == (long) val ? String.valueOf((long) val) : String.valueOf(val);
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                try {
                    return cell.getStringCellValue();
                } catch (Exception e) {
                    try {
                        double numVal = cell.getNumericCellValue();
                        return numVal == (long) numVal ? String.valueOf((long) numVal) : String.valueOf(numVal);
                    } catch (Exception ex) {
                        return cell.getCellFormula();
                    }
                }
            case BLANK:
                return "";
            default:
                return "";
        }
    }

    private String convertNumToColString(int col) {
        StringBuilder sb = new StringBuilder();
        while (col >= 0) {
            int m = col % 26;
            sb.insert(0, (char) (m + 'A'));
            col = (col / 26) - 1;
        }
        return sb.toString();
    }
}
