package com.example.excelanalyzer;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.FileOutputStream;
import java.io.IOException;

public class DocumentGenerator {

    public static void main(String[] args) {
        try {
            generateSampleFile("test_requirements.xlsx");
            System.out.println("Excel file generated successfully!");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void generateSampleFile(String outputPath) throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("User Management");

            // Header Style
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());

            CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);

            // Data Style
            CellStyle normalStyle = workbook.createCellStyle();
            normalStyle.setBorderBottom(BorderStyle.THIN);
            normalStyle.setBorderTop(BorderStyle.THIN);
            normalStyle.setBorderLeft(BorderStyle.THIN);
            normalStyle.setBorderRight(BorderStyle.THIN);

            // Yellow Highlight Style
            CellStyle yellowStyle = workbook.createCellStyle();
            yellowStyle.cloneStyleFrom(normalStyle);
            yellowStyle.setFillForegroundColor(IndexedColors.YELLOW.getIndex());
            yellowStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            // Create Headers
            String[] headers = {"Req ID", "Functionality", "Requirement Description", "Priority", "Status"};
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            // Row 1 (Normal)
            Row row1 = sheet.createRow(1);
            createCell(row1, 0, "REQ-001", normalStyle);
            createCell(row1, 1, "Login Page", normalStyle);
            createCell(row1, 2, "The login page must support email and password input.", normalStyle);
            createCell(row1, 3, "High", normalStyle);
            createCell(row1, 4, "Approved", normalStyle);

            // Row 2 (Changed Requirement - SSO Description Highlighted)
            Row row2 = sheet.createRow(2);
            createCell(row2, 0, "REQ-002", normalStyle);
            createCell(row2, 1, "Google SSO", normalStyle);
            createCell(row2, 2, "The system must allow users to log in using Google Single Sign-On (SSO).", yellowStyle);
            createCell(row2, 3, "Critical", normalStyle);
            createCell(row2, 4, "Changed", normalStyle);

            // Row 3 (Normal)
            Row row3 = sheet.createRow(3);
            createCell(row3, 0, "REQ-003", normalStyle);
            createCell(row3, 1, "Password Reset", normalStyle);
            createCell(row3, 2, "The system must support sending reset email links.", normalStyle);
            createCell(row3, 3, "Medium", normalStyle);
            createCell(row3, 4, "Approved", normalStyle);

            // Row 4 (New Requirement - Priority and Status Highlighted)
            Row row4 = sheet.createRow(4);
            createCell(row4, 0, "REQ-004", normalStyle);
            createCell(row4, 1, "Session Timeout", normalStyle);
            createCell(row4, 2, "Sessions must automatically terminate after 30 minutes of inactivity.", normalStyle);
            createCell(row4, 3, "High", yellowStyle);
            createCell(row4, 4, "New", yellowStyle);

            // Resize columns
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            // Save file
            try (FileOutputStream fileOut = new FileOutputStream(outputPath)) {
                workbook.write(fileOut);
            }
        }
    }

    private static void createCell(Row row, int column, String value, CellStyle style) {
        Cell cell = row.createCell(column);
        cell.setCellValue(value);
        cell.setCellStyle(style);
    }
}
