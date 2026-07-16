package com.enterprise.ai.knowledge.assistant.demo.document.parser;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DocumentParserRegistry {

    private final List<DocumentParser> parsers;

    public DocumentParserRegistry(List<DocumentParser> parsers) {
        this.parsers = parsers;
    }

    public DocumentParser resolve(String fileName) {
        if (parsers == null || parsers.isEmpty()) {
            throw new IllegalStateException("No document parsers available");
        }
        return parsers.stream()
                .filter(parser -> parser.supports(fileName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported file type: " + fileName));
    }
}

