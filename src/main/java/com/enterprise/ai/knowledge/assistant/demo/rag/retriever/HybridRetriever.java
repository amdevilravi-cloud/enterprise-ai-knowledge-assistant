package com.enterprise.ai.knowledge.assistant.demo.rag.retriever;

import com.enterprise.ai.knowledge.assistant.demo.rag.fusion.ReciprocalRankFusion;
import com.enterprise.ai.knowledge.assistant.demo.rag.rewriter.QueryRewriter;
import com.enterprise.ai.knowledge.assistant.demo.repository.SearchResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class HybridRetriever {

    private final VectorRetriever vectorRetriever;
    private final KeywordRetriever keywordRetriever;
    private final ReciprocalRankFusion fusion;
    private final QueryRewriter queryRewriter;

    @Value("${app.rag.enableHybridSearch:false}")
    private boolean enableHybridSearch;

    @Value("${app.rag.vectorTopK:20}")
    private int vectorTopK;

    @Value("${app.rag.keywordTopK:20}")
    private int keywordTopK;

    public HybridRetriever(VectorRetriever vectorRetriever,
                           KeywordRetriever keywordRetriever,
                           ReciprocalRankFusion fusion,
                           QueryRewriter queryRewriter) {
        this.vectorRetriever = vectorRetriever;
        this.keywordRetriever = keywordRetriever;
        this.fusion = fusion;
        this.queryRewriter = queryRewriter;
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
}
