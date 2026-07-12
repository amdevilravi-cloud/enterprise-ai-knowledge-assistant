package com.enterprise.ai.knowledge.assistant.demo.document.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@Builder
@NoArgsConstructor
public class DocumentUploadResponse {

    private String documentName;
    private int pages;
    private int characters;
    private int chunks;
    private String text;
    private boolean isUploadSuccess;
    private java.util.List<String> chunkContents;
}
