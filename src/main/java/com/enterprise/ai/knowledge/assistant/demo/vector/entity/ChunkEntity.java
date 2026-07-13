package com.enterprise.ai.knowledge.assistant.demo.vector.entity;

import java.time.Instant;
import java.util.UUID;

/**
 * Simple DTO representing a document chunk and its metadata to persist in the vector store.
 */
public class ChunkEntity {
    private UUID id;
    private String documentName;
    private Integer pageNumber;
    private Integer chunkIndex;
    private String content;
    private float[] embedding;
    private Instant createdAt;
    private String hash;

    public ChunkEntity() {}

    public ChunkEntity(UUID id, String documentName, Integer pageNumber, Integer chunkIndex, String content, float[] embedding, Instant createdAt, String hash) {
        this.id = id;
        this.documentName = documentName;
        this.pageNumber = pageNumber;
        this.chunkIndex = chunkIndex;
        this.content = content;
        this.embedding = embedding;
        this.createdAt = createdAt;
        this.hash = hash;
    }

    // getters and setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getDocumentName() { return documentName; }
    public void setDocumentName(String documentName) { this.documentName = documentName; }
    public Integer getPageNumber() { return pageNumber; }
    public void setPageNumber(Integer pageNumber) { this.pageNumber = pageNumber; }
    public Integer getChunkIndex() { return chunkIndex; }
    public void setChunkIndex(Integer chunkIndex) { this.chunkIndex = chunkIndex; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public float[] getEmbedding() { return embedding; }
    public void setEmbedding(float[] embedding) { this.embedding = embedding; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public String getHash() { return hash; }
    public void setHash(String hash) { this.hash = hash; }
}

