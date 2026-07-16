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

    // Explicit setters to ensure Lombok generates them
    public void setDocumentName(String documentName) { this.documentName = documentName; }
    public void setPages(int pages) { this.pages = pages; }
    public void setCharacters(int characters) { this.characters = characters; }
    public void setChunks(int chunks) { this.chunks = chunks; }
    public void setText(String text) { this.text = text; }
    public void setUploadSuccess(boolean uploadSuccess) { this.isUploadSuccess = uploadSuccess; }
    public void setChunkContents(java.util.List<String> chunkContents) { this.chunkContents = chunkContents; }

    // Explicit getters
    public String getDocumentName() { return documentName; }
    public int getPages() { return pages; }
    public int getCharacters() { return characters; }
    public int getChunks() { return chunks; }
    public String getText() { return text; }
    public boolean isUploadSuccess() { return isUploadSuccess; }
    public java.util.List<String> getChunkContents() { return chunkContents; }
}
