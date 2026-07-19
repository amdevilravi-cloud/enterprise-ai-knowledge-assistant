package com.enterprise.ai.knowledge.assistant.demo.chat.dto;

import java.util.List;
import java.util.Map;

/**
 * Response DTO for chat endpoints with enriched RAG metadata.
 */
public class ChatResponse {
    private String answer;
    private List<Citation> citations;
    private Boolean isFromContext;
    private Integer retrievalCount;
    private List<DocumentSource> sourceDocuments;
    private Map<String, Object> metadata;

    public ChatResponse() {}

    public ChatResponse(String answer, List<Citation> citations, Boolean isFromContext, Integer retrievalCount) {
        this.answer = answer;
        this.citations = citations;
        this.isFromContext = isFromContext;
        this.retrievalCount = retrievalCount;
    }

    public ChatResponse(String answer, List<Citation> citations, Boolean isFromContext, Integer retrievalCount,
                        List<DocumentSource> sourceDocuments, Map<String, Object> metadata) {
        this.answer = answer;
        this.citations = citations;
        this.isFromContext = isFromContext;
        this.retrievalCount = retrievalCount;
        this.sourceDocuments = sourceDocuments;
        this.metadata = metadata;
    }

    public String getAnswer() { return answer; }
    public void setAnswer(String answer) { this.answer = answer; }

    public List<Citation> getCitations() { return citations; }
    public void setCitations(List<Citation> citations) { this.citations = citations; }

    public Boolean getIsFromContext() { return isFromContext; }
    public void setIsFromContext(Boolean isFromContext) { this.isFromContext = isFromContext; }

    public Integer getRetrievalCount() { return retrievalCount; }
    public void setRetrievalCount(Integer retrievalCount) { this.retrievalCount = retrievalCount; }

    public List<DocumentSource> getSourceDocuments() { return sourceDocuments; }
    public void setSourceDocuments(List<DocumentSource> sourceDocuments) { this.sourceDocuments = sourceDocuments; }

    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }

    /**
     * Citation sub-DTO for tracking retrieved document sources.
     */
    public static class Citation {
        private String documentName;
        private Integer pageNumber;
        private Integer chunkIndex;
        private Double relevanceScore;
        private String content;

        public Citation() {}

        public Citation(String documentName, Integer pageNumber, Integer chunkIndex, Double relevanceScore, String excerpt) {
            this.documentName = documentName;
            this.pageNumber = pageNumber;
            this.chunkIndex = chunkIndex;
            this.relevanceScore = relevanceScore;
            this.content = excerpt;
        }

        public String getDocumentName() { return documentName; }
        public void setDocumentName(String documentName) { this.documentName = documentName; }

        public Integer getPageNumber() { return pageNumber; }
        public void setPageNumber(Integer pageNumber) { this.pageNumber = pageNumber; }

        public Integer getChunkIndex() { return chunkIndex; }
        public void setChunkIndex(Integer chunkIndex) { this.chunkIndex = chunkIndex; }

        public Double getRelevanceScore() { return relevanceScore; }
        public void setRelevanceScore(Double relevanceScore) { this.relevanceScore = relevanceScore; }

        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
    }

    public static class DocumentSource {
        private String documentName;
        private String documentId;
        private List<Citation> citations;
        private Integer chunkCount;

        public DocumentSource() {}

        public DocumentSource(String documentName, String documentId) {
            this.documentName = documentName;
            this.documentId = documentId;
        }

        public String getDocumentName() { return documentName; }
        public void setDocumentName(String documentName) { this.documentName = documentName; }

        public String getDocumentId() { return documentId; }
        public void setDocumentId(String documentId) { this.documentId = documentId; }

        public List<Citation> getCitations() { return citations; }
        public void setCitations(List<Citation> citations) { this.citations = citations; }

        public Integer getChunkCount() { return chunkCount; }
        public void setChunkCount(Integer chunkCount) { this.chunkCount = chunkCount; }
    }
}
