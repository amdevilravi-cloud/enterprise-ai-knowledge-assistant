package com.enterprise.ai.knowledge.assistant.demo.rag;

import com.enterprise.ai.knowledge.assistant.demo.embedding.dto.EmbeddingResult;
import com.enterprise.ai.knowledge.assistant.demo.embedding.service.EmbeddingService;
import com.enterprise.ai.knowledge.assistant.demo.repository.SearchResult;
import com.enterprise.ai.knowledge.assistant.demo.vector.service.VectorStoreService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Retriever component for RAG (Retrieval-Augmented Generation) pipeline.
 * Responsible for finding relevant context chunks based on a query.
 */
@Component
public class Retriever {

    private final EmbeddingService embeddingService;
    private final VectorStoreService vectorStoreService;
    private static final int DEFAULT_K = 5; // Number of chunks to retrieve

    public Retriever(EmbeddingService embeddingService, VectorStoreService vectorStoreService) {
        this.embeddingService = embeddingService;
        this.vectorStoreService = vectorStoreService;
    }

    /**
     * Retrieve the top-K most relevant chunks for a given query.
     *
     * @param query The user's query text
     * @return List of SearchResult containing relevant chunks
     */
    public List<SearchResult> retrieve(String query) {
        return retrieve(query, DEFAULT_K);
    }

    /**
     * Retrieve the top-K most relevant chunks for a given query.
     *
     * @param query The user's query text
     * @param k     Number of chunks to retrieve
     * @return List of SearchResult containing relevant chunks
     */
    public List<SearchResult> retrieve(String query, int k) {
        try {
            // Generate embedding for the query
            EmbeddingResult embeddingResult = embeddingService.generateEmbedding(query);
            if (embeddingResult == null || embeddingResult.vector() == null) {
                return List.of();
            }

            // Find nearest chunks in the vector store
            return vectorStoreService.findNearest(embeddingResult.vector(), k);
        } catch (Exception e) {
            // Best-effort: return empty list on error
            return List.of();
        }
    }

    /**
     * Build context string from retrieved results for prompt injection.
     *
     * @param results List of SearchResult from retrieve
     * @return Formatted context string to include in the prompt
     */
    public String buildContext(List<SearchResult> results) {
        if (results.isEmpty()) {
            return "";
        }

        return results.stream()
                .map(result -> formatSearchResult(result))
                .collect(Collectors.joining("\n\n"));
    }

    private String formatSearchResult(SearchResult result) {
        StringBuilder sb = new StringBuilder();
        sb.append("Source: ").append(result.getDocumentName());
        if (result.getPageNumber() != null) {
            sb.append(" (Page ").append(result.getPageNumber()).append(")");
        }
        sb.append("\n");
        sb.append("Content: ").append(result.getContent());
        sb.append("\n");
        sb.append("Relevance Score: ").append(String.format("%.4f", result.getScore()));
        return sb.toString();
    }
}

