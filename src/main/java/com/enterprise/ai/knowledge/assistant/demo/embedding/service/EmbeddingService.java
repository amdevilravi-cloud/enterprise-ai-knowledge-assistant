package com.enterprise.ai.knowledge.assistant.demo.embedding.service;

import com.enterprise.ai.knowledge.assistant.demo.embedding.dto.EmbeddingResult;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;

@Service
public class EmbeddingService {

    private final EmbeddingModel embeddingModel;

    public EmbeddingService(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    public EmbeddingResult generateEmbedding(String text) {
        float[] vector = embeddingModel.embed(text);
        int dims = vector == null ? 0 : vector.length;
        // Model name not available from EmbeddingModel interface in this skeleton; provide best-effort
        String modelName = embeddingModel.getClass().getSimpleName();
        return new EmbeddingResult(vector, dims, modelName);
    }
}
