package com.enterprise.ai.knowledge.assistant.demo.repository;

/**
 * DTO returned by nearest-neighbor searches with enriched metadata.
 */
public class SearchResult {
    private final String content;
    private final double score;
    private final Integer pageNumber;
    private final String documentName;
    private final Integer chunkIndex;


    public SearchResult(String content, double score, Integer pageNumber, String documentName, Integer chunkIndex) {
        this.content = content;
        this.score = score;
        this.pageNumber = pageNumber;
        this.documentName = documentName;
        this.chunkIndex = chunkIndex;
    }

    public String getContent() { return content; }
    public double getScore() { return score; }
    public Integer getPageNumber() { return pageNumber; }
    public String getDocumentName() { return documentName; }
    public Integer getChunkIndex() { return chunkIndex; }

}

