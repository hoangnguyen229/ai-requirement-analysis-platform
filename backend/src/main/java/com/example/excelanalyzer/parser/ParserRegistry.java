package com.example.excelanalyzer.parser;

import org.springframework.stereotype.Component;

import java.io.File;

@Component
public class ParserRegistry {

    private final DocumentDocumentParser excelParser;
    private final DocxDocumentParser docxParser;
    private final TextDocumentParser textParser;
    private final MarkdownDocumentParser markdownParser;

    public ParserRegistry(DocumentDocumentParser excelParser, DocxDocumentParser docxParser,
                          TextDocumentParser textParser, MarkdownDocumentParser markdownParser) {
        this.excelParser = excelParser;
        this.docxParser = docxParser;
        this.textParser = textParser;
        this.markdownParser = markdownParser;
    }

    /**
     * Resolves the appropriate parser based on the file extension.
     * Throws an exception for unsupported formats or MVP exclusions.
     */
    public DocumentParser getParser(File file) {
        String name = file.getName().toLowerCase();
        
        if (name.endsWith(".xlsx")) {
            return excelParser;
        } else if (name.endsWith(".docx")) {
            return docxParser;
        } else if (name.endsWith(".txt")) {
            return textParser;
        } else if (name.endsWith(".md")) {
            return markdownParser;
        } else if (name.endsWith(".xls") || name.endsWith(".doc") || name.endsWith(".pdf")) {
            String format = name.substring(name.lastIndexOf(".")).toUpperCase();
            throw new IllegalArgumentException(format + " files are not supported in this MVP. Supported document formats are: .xlsx, .docx, .txt, and .md. (" + format + " support is planned for future improvements).");
        } else {
            throw new IllegalArgumentException("Unsupported file type: " + file.getName() + ". Supported document formats are: .xlsx, .docx, .txt, and .md.");
        }
    }
}
