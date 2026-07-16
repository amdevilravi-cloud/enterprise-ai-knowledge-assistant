package com.enterprise.ai.knowledge.assistant.demo.document.dto;

/**
 * Parsed document payload returned by file parsers.
 */
public record ParsedDocument(
        String text,
        int pageCount,
        boolean pageAware,
        String mimeType
) {
}

