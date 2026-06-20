package com.example.excelanalyzer;

import org.apache.poi.xwpf.usermodel.*;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STHighlightColor;

import java.io.FileOutputStream;
import java.io.IOException;

public class DocxGenerator {

    public static void main(String[] args) {
        try {
            generateSampleFile("test_requirements.docx");
            System.out.println("Docx file generated successfully!");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void generateSampleFile(String outputPath) throws IOException {
        try (XWPFDocument doc = new XWPFDocument()) {
            // Title
            XWPFParagraph title = doc.createParagraph();
            XWPFRun titleRun = title.createRun();
            titleRun.setText("Requirement Specification: User Management");
            titleRun.setBold(true);
            titleRun.setFontSize(16);

            // Normal paragraph
            XWPFParagraph p1 = doc.createParagraph();
            XWPFRun r1 = p1.createRun();
            r1.setText("1. This is a normal paragraph with standard requirements. The system must authenticate users using credentials.");

            // Paragraph with yellow highlight run
            XWPFParagraph p2 = doc.createParagraph();
            XWPFRun r2a = p2.createRun();
            r2a.setText("2. Multi-Factor Authentication: ");
            XWPFRun r2b = p2.createRun();
            r2b.setText("The system must require a one-time passcode (OTP) sent via SMS for all logins.");
            r2b.getCTR().addNewRPr().addNewHighlight().setVal(STHighlightColor.YELLOW);

            // Table
            XWPFTable table = doc.createTable(2, 3);
            
            // Header Row
            XWPFTableRow headerRow = table.getRow(0);
            headerRow.getCell(0).setText("Req ID");
            headerRow.getCell(1).setText("Description");
            headerRow.getCell(2).setText("Status");

            // Data Row
            XWPFTableRow dataRow = table.getRow(1);
            dataRow.getCell(0).setText("REQ-201");
            
            // Highlighted cell
            XWPFTableCell cell2 = dataRow.getCell(1);
            XWPFParagraph cellP = cell2.getParagraphs().get(0);
            XWPFRun cellR = cellP.createRun();
            cellR.setText("Users must be locked out after 5 consecutive failed login attempts.");
            cellR.getCTR().addNewRPr().addNewHighlight().setVal(STHighlightColor.YELLOW);

            dataRow.getCell(2).setText("New");

            // Save file
            try (FileOutputStream fileOut = new FileOutputStream(outputPath)) {
                doc.write(fileOut);
            }
        }
    }
}
