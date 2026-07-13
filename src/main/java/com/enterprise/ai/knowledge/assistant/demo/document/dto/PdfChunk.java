package com.enterprise.ai.knowledge.assistant.demo.document.dto;

import lombok.Builder;

@Builder
public record PdfChunk(int pageNumber, int chunkIndex, String text) {

}