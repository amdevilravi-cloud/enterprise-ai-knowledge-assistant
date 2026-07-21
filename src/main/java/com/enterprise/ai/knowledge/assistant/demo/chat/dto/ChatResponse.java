package com.enterprise.ai.knowledge.assistant.demo.chat.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response DTO for chat endpoints with enriched RAG metadata.
 */
@Builder
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ChatResponse {
    private String answer;
    private Boolean isFromContext;
    private Integer retrievalCount;
    private List<DocumentSource> sourceDocuments;
}
