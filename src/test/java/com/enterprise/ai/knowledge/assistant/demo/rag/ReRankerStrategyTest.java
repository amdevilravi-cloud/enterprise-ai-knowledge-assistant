//package com.enterprise.ai.knowledge.assistant.demo.rag;
//
//import com.enterprise.ai.knowledge.assistant.demo.embedding.dto.EmbeddingResult;
//import com.enterprise.ai.knowledge.assistant.demo.embedding.service.EmbeddingService;
//import com.enterprise.ai.knowledge.assistant.demo.rag.strategy.EmbeddingReRanker;
//import com.enterprise.ai.knowledge.assistant.demo.rag.strategy.ReRankStrategy;
//import com.enterprise.ai.knowledge.assistant.demo.repository.SearchResult;
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.Test;
//import org.mockito.Mock;
//import org.mockito.MockitoAnnotations;
//
//import java.util.ArrayList;
//import java.util.List;
//
//import static org.junit.jupiter.api.Assertions.*;
//import static org.mockito.ArgumentMatchers.anyString;
//import static org.mockito.Mockito.when;
//
///**
// * Unit tests for ReRanker strategy pattern.
// * Tests orchestrator and individual strategies.
// */
//public class ReRankerStrategyTest {
//
//    private ReRanker reRanker;
//    private EmbeddingReRanker embeddingReRanker;
//
//    @Mock
//    private EmbeddingService embeddingService;
//
//    @BeforeEach
//    void setUp() {
//        MockitoAnnotations.openMocks(this);
//        embeddingReRanker = new EmbeddingReRanker(embeddingService);
//
//        // Create orchestrator with embedding strategy
//        List<ReRankStrategy> strategies = List.of(embeddingReRanker);
//        reRanker = new ReRanker(strategies, "embedding");
//    }
//
//    /**
//     * Test ReRanker orchestrator uses default strategy.
//     */
//    @Test
//    void testReRankerUsesDefaultStrategy() {
//        String query = "test query";
//        SearchResult r1 = new SearchResult("Content 1", 0.7, 1, "Doc1.pdf", 0);
//        SearchResult r2 = new SearchResult("Content 2", 0.8, 1, "Doc2.pdf", 0);
//        List<SearchResult> candidates = List.of(r1, r2);
//
//        float[] embedding = new float[]{0.1f, 0.2f};
//        EmbeddingResult result = new EmbeddingResult(embedding, 2, "test-model");
//
//        when(embeddingService.generateEmbedding(query)).thenReturn(result);
//        when(embeddingService.generateEmbedding(anyString())).thenReturn(result);
//
//        List<SearchResult> ranked = reRanker.rerank(candidates, query, 2);
//
//        assertNotNull(ranked);
//        assertEquals(2, ranked.size());
//    }
//
//    /**
//     * Test EmbeddingReRanker returns top N results.
//     */
//    @Test
//    void testEmbeddingReRankerTopN() {
//        String query = "test query";
//        List<SearchResult> candidates = new ArrayList<>();
//        for (int i = 0; i < 10; i++) {
//            candidates.add(new SearchResult("Content " + i, 0.5 + i * 0.05, 1, "Doc.pdf", i));
//        }
//
//        float[] embedding = new float[]{0.1f, 0.2f};
//        EmbeddingResult result = new EmbeddingResult(embedding, 2, "test-model");
//
//        when(embeddingService.generateEmbedding(query)).thenReturn(result);
//        when(embeddingService.generateEmbedding(anyString())).thenReturn(result);
//
//        List<SearchResult> ranked = embeddingReRanker.rerank(candidates, query, 3);
//
//        assertEquals(3, ranked.size());
//    }
//
//    /**
//     * Test ReRanker with empty candidates.
//     */
//    @Test
//    void testReRankerWithEmptyCandidates() {
//        List<SearchResult> ranked = reRanker.rerank(new ArrayList<>(), "query", 5);
//
//        assertEquals(0, ranked.size());
//    }
//
//    /**
//     * Test ReRanker with null candidates.
//     */
//    @Test
//    void testReRankerWithNullCandidates() {
//        List<SearchResult> ranked = reRanker.rerank(null, "query", 5);
//
//        assertEquals(0, ranked.size());
//    }
//
//    /**
//     * Test EmbeddingReRanker returns strategy name.
//     */
//    @Test
//    void testStrategyName() {
//        assertEquals("embedding", embeddingReRanker.getName());
//    }
//
//    /**
//     * Test ReRanker lists available strategies.
//     */
//    @Test
//    void testGetAvailableStrategies() {
//        List<String> strategies = reRanker.getAvailableStrategies();
//
//        assertNotNull(strategies);
//        assertTrue(strategies.contains("embedding"));
//    }
//
//    /**
//     * Test ReRanker graceful fallback on null embedding.
//     */
//    @Test
//    void testReRankerFallbackOnNullEmbedding() {
//        String query = "test query";
//        SearchResult r1 = new SearchResult("Content 1", 0.8, 1, "Doc1.pdf", 0);
//        SearchResult r2 = new SearchResult("Content 2", 0.7, 1, "Doc2.pdf", 0);
//        List<SearchResult> candidates = List.of(r1, r2);
//
//        when(embeddingService.generateEmbedding(query)).thenReturn(null);
//
//        List<SearchResult> ranked = reRanker.rerank(candidates, query, 2);
//
//        // Should still return results (best-effort fallback)
//        assertEquals(2, ranked.size());
//    }
//
//    /**
//     * Test EmbeddingReRanker combines stored score and similarity.
//     */
//    @Test
//    void testEmbeddingReRankerScoreCombination() {
//        String query = "test query";
//
//        // Create candidates with different scores
//        SearchResult r1 = new SearchResult("Content 1", 0.9, 1, "Doc1.pdf", 0); // High stored score
//        SearchResult r2 = new SearchResult("Content 2", 0.5, 1, "Doc2.pdf", 0); // Low stored score
//        List<SearchResult> candidates = List.of(r1, r2);
//
//        float[] embedding = new float[]{0.1f, 0.2f};
//        EmbeddingResult result = new EmbeddingResult(embedding, 2, "test-model");
//
//        when(embeddingService.generateEmbedding(query)).thenReturn(result);
//        when(embeddingService.generateEmbedding("Content 1")).thenReturn(result);
//        when(embeddingService.generateEmbedding("Content 2")).thenReturn(result);
//
//        List<SearchResult> ranked = embeddingReRanker.rerank(candidates, query, 2);
//
//        assertNotNull(ranked);
//        assertEquals(2, ranked.size());
//        // First result should be Content 1 (higher combined score)
//        assertTrue(ranked.get(0).getContent().contains("Content 1"));
//    }
//}
//
