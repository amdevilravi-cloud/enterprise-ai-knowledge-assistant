package com.enterprise.ai.knowledge.assistant.demo.chat.dto;

import java.util.List;

/**
 * Response DTO for chat endpoints with enriched RAG metadata.
 */
public class ChatResponse {
    private String answer;
    private List<Citation> citations;
    private Boolean isFromContext;
    private Integer retrievalCount;

    public ChatResponse() {}

    public ChatResponse(String answer, List<Citation> citations, Boolean isFromContext, Integer retrievalCount) {
        this.answer = answer;
        this.citations = citations;
        this.isFromContext = isFromContext;
        this.retrievalCount = retrievalCount;
    }

    public String getAnswer() { return answer; }
    public void setAnswer(String answer) { this.answer = answer; }

    public List<Citation> getCitations() { return citations; }
    public void setCitations(List<Citation> citations) { this.citations = citations; }

    public Boolean getIsFromContext() { return isFromContext; }
    public void setIsFromContext(Boolean isFromContext) { this.isFromContext = isFromContext; }

    public Integer getRetrievalCount() { return retrievalCount; }
    public void setRetrievalCount(Integer retrievalCount) { this.retrievalCount = retrievalCount; }

    /**
     * Citation sub-DTO for tracking retrieved document sources.
     */
    public static class Citation {
        private String documentName;
        private Integer pageNumber;
        private Integer chunkIndex;
        private Double relevanceScore;

        public Citation() {}

        public Citation(String documentName, Integer pageNumber, Integer chunkIndex, Double relevanceScore) {
            this.documentName = documentName;
            this.pageNumber = pageNumber;
            this.chunkIndex = chunkIndex;
            this.relevanceScore = relevanceScore;
        }

        public String getDocumentName() { return documentName; }
        public void setDocumentName(String documentName) { this.documentName = documentName; }

        public Integer getPageNumber() { return pageNumber; }
        public void setPageNumber(Integer pageNumber) { this.pageNumber = pageNumber; }

        public Integer getChunkIndex() { return chunkIndex; }
        public void setChunkIndex(Integer chunkIndex) { this.chunkIndex = chunkIndex; }

        public Double getRelevanceScore() { return relevanceScore; }
        public void setRelevanceScore(Double relevanceScore) { this.relevanceScore = relevanceScore; }
    }
}

