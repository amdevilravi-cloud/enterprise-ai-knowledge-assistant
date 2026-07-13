package com.enterprise.ai.knowledge.assistant.demo.vector.service;

import com.enterprise.ai.knowledge.assistant.demo.vector.entity.ChunkEntity;
import com.enterprise.ai.knowledge.assistant.demo.repository.SearchResult;

import java.util.List;

public interface VectorStoreService {
    void storeChunk(ChunkEntity chunk);
    List<SearchResult> findNearest(float[] query, int k);
    boolean existsByHash(String hash);
}

