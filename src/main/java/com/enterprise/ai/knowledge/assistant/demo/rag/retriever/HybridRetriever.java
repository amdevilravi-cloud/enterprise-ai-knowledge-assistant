package com.enterprise.ai.knowledge.assistant.demo.rag.retriever;

import com.enterprise.ai.knowledge.assistant.demo.embedding.dto.EmbeddingResult;
import com.enterprise.ai.knowledge.assistant.demo.embedding.service.EmbeddingService;
import com.enterprise.ai.knowledge.assistant.demo.rag.MetaDataFilter;
import com.enterprise.ai.knowledge.assistant.demo.rag.ReRanker;
import com.enterprise.ai.knowledge.assistant.demo.rag.fusion.ReciprocalRankFusion;
import com.enterprise.ai.knowledge.assistant.demo.rag.rewriter.QueryRewriter;
import com.enterprise.ai.knowledge.assistant.demo.repository.SearchResult;
import com.enterprise.ai.knowledge.assistant.demo.vector.service.VectorStoreService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class HybridRetriever {

    private final VectorRetriever vectorRetriever;
    private final KeywordRetriever keywordRetriever;
    private final ReciprocalRankFusion fusion;
    private final QueryRewriter queryRewriter;
    private final EmbeddingService embeddingService;
    private final MetaDataFilter metaDataFilter;
    private final ReRanker reRanker;
    ;

    @Value("${app.rag.enableHybridSearch:false}")
    private boolean enableHybridSearch;

    @Value("20")
    private int vectorTopK;

    @Value("${app.rag.keywordTopK:20}")
    private int keywordTopK;

    @Value("${app.rag.finalTopN:5}")
    private int finalTopN;

    public HybridRetriever(VectorRetriever vectorRetriever,
                           KeywordRetriever keywordRetriever,
                           ReciprocalRankFusion fusion,
                           QueryRewriter queryRewriter, EmbeddingService embeddingService,  MetaDataFilter metaDataFilter, ReRanker reRanker,@Value("${app.rag.vectorTopK:20}") int defaultVectorTopK,
                           @Value("${app.rag.finalTopN:3}") int defaultFinalTopN) {
        this.vectorRetriever = vectorRetriever;
        this.keywordRetriever = keywordRetriever;
        this.fusion = fusion;
        this.queryRewriter = queryRewriter;
        this.embeddingService = embeddingService;
        this.metaDataFilter = metaDataFilter;
        this.reRanker = reRanker;
        this.vectorTopK = defaultVectorTopK;
        this.keywordTopK = defaultVectorTopK;
        this.finalTopN = defaultFinalTopN;
    }

    public List<SearchResult> retrieve(String query, int topN) {
        return retrieve(query, topN, null);
    }

    public List<SearchResult> retrieve(String query, int topN, String conversationHistory) {
        String finalQuery = query;
        if (queryRewriter.isEnabled() && conversationHistory != null && !conversationHistory.isEmpty()) {
            finalQuery = queryRewriter.rewrite(query, conversationHistory);
        }

        if (!enableHybridSearch) {
            return vectorRetriever.retrieve(finalQuery, topN);
        }

        long startTime = System.currentTimeMillis();

        List<SearchResult> vectorResults = vectorRetriever.retrieve(finalQuery, vectorTopK);
        List<SearchResult> keywordResults = keywordRetriever.retrieve(finalQuery, keywordTopK);

        List<SearchResult> fused = fusion.fuse(vectorResults, keywordResults, topN);

        long retrievalTime = System.currentTimeMillis() - startTime;

        return fused;
    }

    public boolean isEnabled() {
        return enableHybridSearch;
    }

    /**
     * Two-stage retrieval: vector search -> metadata filter -> re-rank -> top N
     */
    public List<SearchResult> retrieveAndRerank(String query, Integer vectorTopK, Integer finalTopN) {
        int k = vectorTopK == null ? this.vectorTopK : vectorTopK;
        int n =  finalTopN == null ? this.finalTopN : finalTopN;

        try {
            List<SearchResult> initial = retrieve(query, k);
            // Apply metadata filter (default pass-through)
          //  List<SearchResult> filtered = metaDataFilter.filter(initial, null);

            // Re-rank and return top N
            List<SearchResult> finalResults = reRanker.rerank(initial, query, n);
            return finalResults;
        } catch (Exception e) {
            // On any error, best-effort: fallback to simple vector search top-n
            try {
                EmbeddingResult embeddingResult = embeddingService.generateEmbedding(query);
                if (embeddingResult == null || embeddingResult.vector() == null) return List.of();
                return vectorRetriever.retrieve(query,k);
            } catch (Exception ex) {
                return List.of();
            }
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
