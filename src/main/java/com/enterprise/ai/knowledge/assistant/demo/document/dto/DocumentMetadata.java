package com.enterprise.ai.knowledge.assistant.demo.document.dto;

import java.time.Instant;

/**
 * Lightweight metadata captured during ingestion.
 */
public record DocumentMetadata(
        String documentName,
        String fileExtension,
        String mimeType,
        int pageCount,
        int characterCount,
        String language,
        Instant extractedAt
) {
}

