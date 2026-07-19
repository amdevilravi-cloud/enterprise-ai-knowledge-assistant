package com.enterprise.ai.knowledge.assistant.demo.rag.retriever;

import com.enterprise.ai.knowledge.assistant.demo.embedding.dto.EmbeddingResult;
import com.enterprise.ai.knowledge.assistant.demo.embedding.service.EmbeddingService;
import com.enterprise.ai.knowledge.assistant.demo.repository.SearchResult;
import com.enterprise.ai.knowledge.assistant.demo.vector.service.VectorStoreService;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class VectorRetriever {

    private final EmbeddingService embeddingService;
    private final VectorStoreService vectorStoreService;

    public VectorRetriever(EmbeddingService embeddingService, VectorStoreService vectorStoreService) {
        this.embeddingService = embeddingService;
        this.vectorStoreService = vectorStoreService;
    }

    public List<SearchResult> retrieve(String query, int topK) {
        try {
            EmbeddingResult embedding = embeddingService.generateEmbedding(query);
            if (embedding == null || embedding.vector() == null) {
                return List.of();
            }
            return vectorStoreService.findNearest(embedding.vector(), topK);
        } catch (Exception e) {
            return List.of();
        }
    }
}

