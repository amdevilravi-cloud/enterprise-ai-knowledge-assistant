package com.enterprise.ai.knowledge.assistant.demo.document.service;

import com.enterprise.ai.knowledge.assistant.demo.document.dto.DocumentMetadata;
import com.enterprise.ai.knowledge.assistant.demo.document.dto.ParsedDocument;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class MetadataExtractor {

    public DocumentMetadata extract(String documentName, ParsedDocument parsedDocument) {
        String lower = documentName == null ? "" : documentName.toLowerCase();
        String extension = "";
        int dot = lower.lastIndexOf('.');
        if (dot >= 0) {
            extension = lower.substring(dot + 1);
        }

        return new DocumentMetadata(
                null, // documentId - generated later
                documentName,
                null, // documentHash - generated later
                0, // chunkCount - set during ingestion
                0L, // fileSize - not available yet
                parsedDocument == null ? 0 : parsedDocument.pageCount(),
                parsedDocument == null || parsedDocument.text() == null ? 0 : parsedDocument.text().length(),
                Instant.now(), // uploadedAt
                null // indexedAt - set during indexing
        );
    }

    private String detectLanguage(String text) {
        if (text == null || text.isBlank()) {
            return "unknown";
        }
        return text.chars().anyMatch(ch -> ch > 127) ? "unknown" : "en";
    }
}
