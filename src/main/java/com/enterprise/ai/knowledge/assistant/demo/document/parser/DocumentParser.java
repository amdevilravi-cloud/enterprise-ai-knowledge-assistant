package com.enterprise.ai.knowledge.assistant.demo.document.parser;

import com.enterprise.ai.knowledge.assistant.demo.document.dto.ParsedDocument;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Contract for extracting plain text from a supported document format.
 */
public interface DocumentParser {

    boolean supports(String fileName);

    ParsedDocument parse(Path filePath) throws IOException;
}

