package com.enterprise.ai.knowledge.assistant.demo.rag;

import com.enterprise.ai.knowledge.assistant.demo.embedding.dto.EmbeddingResult;
import com.enterprise.ai.knowledge.assistant.demo.embedding.service.EmbeddingService;
import com.enterprise.ai.knowledge.assistant.demo.repository.SearchResult;
import com.enterprise.ai.knowledge.assistant.demo.vector.service.VectorStoreService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

/**
 * Unit tests for Retriever component.
 * Tests the retrieval logic and context building.
 */
public class RetrieverTest {

    private Retriever retriever;

    @Mock
    private EmbeddingService embeddingService;

    @Mock
    private VectorStoreService vectorStoreService;

    @Mock
    private MetaDataFilter metaDataFilter;

    @Mock
    private ReRanker reRanker;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        retriever = new Retriever(embeddingService, vectorStoreService, metaDataFilter, reRanker, 20, 3);
    }

    /**
     * Test retrieve with default K (5).
     */
    @Test
    void testRetrieveWithDefaultK() {
        String query = "What is the vacation policy?";
        float[] embedding = new float[]{0.1f, 0.2f, 0.3f};
        EmbeddingResult embeddingResult = new EmbeddingResult(embedding, 3, "TestModel");

        SearchResult result1 = new SearchResult("Content 1", 0.95, 1, "Doc1.pdf", 0);
        SearchResult result2 = new SearchResult("Content 2", 0.85, 2, "Doc2.pdf", 0);
        List<SearchResult> expectedResults = List.of(result1, result2);

        when(embeddingService.generateEmbedding(query)).thenReturn(embeddingResult);
        when(vectorStoreService.findNearest(embedding, 5)).thenReturn(expectedResults);

        List<SearchResult> results = retriever.retrieve(query);

        assertEquals(2, results.size());
        assertEquals("Content 1", results.get(0).getContent());
        assertEquals(0.95, results.get(0).getScore());
        verify(embeddingService).generateEmbedding(query);
        verify(vectorStoreService).findNearest(embedding, 5);
    }

    /**
     * Test retrieve with custom K.
     */
    @Test
    void testRetrieveWithCustomK() {
        String query = "What is the vacation policy?";
        int customK = 10;
        float[] embedding = new float[]{0.1f, 0.2f, 0.3f};
        EmbeddingResult embeddingResult = new EmbeddingResult(embedding, 3, "TestModel");

        List<SearchResult> expectedResults = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            expectedResults.add(new SearchResult(
                    "Content " + i,
                    0.9 - (i * 0.01),
                    1,
                    "Doc.pdf",
                    i
            ));
        }

        when(embeddingService.generateEmbedding(query)).thenReturn(embeddingResult);
        when(vectorStoreService.findNearest(embedding, customK)).thenReturn(expectedResults);

        List<SearchResult> results = retriever.retrieve(query, customK);

        assertEquals(10, results.size());
        verify(vectorStoreService).findNearest(embedding, customK);
    }

    /**
     * Test retrieve with null embedding result (graceful degradation).
     */
    @Test
    void testRetrieveWithNullEmbedding() {
        String query = "What is the vacation policy?";

        when(embeddingService.generateEmbedding(query)).thenReturn(null);

        List<SearchResult> results = retriever.retrieve(query, 5);

        assertEquals(0, results.size());
        verify(vectorStoreService, never()).findNearest(any(), anyInt());
    }

    /**
     * Test retrieve with null vector in embedding result.
     */
    @Test
    void testRetrieveWithNullVector() {
        String query = "What is the vacation policy?";
        EmbeddingResult embeddingResult = new EmbeddingResult(null, 0, "TestModel");

        when(embeddingService.generateEmbedding(query)).thenReturn(embeddingResult);

        List<SearchResult> results = retriever.retrieve(query, 5);

        assertEquals(0, results.size());
        verify(vectorStoreService, never()).findNearest(any(), anyInt());
    }

    /**
     * Test retrieve with empty search results.
     */
    @Test
    void testRetrieveWithEmptyResults() {
        String query = "Obscure question that returns no results";
        float[] embedding = new float[]{0.1f, 0.2f, 0.3f};
        EmbeddingResult embeddingResult = new EmbeddingResult(embedding, 3, "TestModel");

        when(embeddingService.generateEmbedding(query)).thenReturn(embeddingResult);
        when(vectorStoreService.findNearest(embedding, 5)).thenReturn(new ArrayList<>());

        List<SearchResult> results = retriever.retrieve(query);

        assertEquals(0, results.size());
    }

    /**
     * Test buildContext with results.
     */
    @Test
    void testBuildContextWithResults() {
        SearchResult result1 = new SearchResult(
                "Employees receive 20 days of PTO.",
                0.95,
                2,
                "EmployeeHandbook.pdf",
                0
        );
        SearchResult result2 = new SearchResult(
                "Additional unpaid leave available upon request.",
                0.85,
                3,
                "EmployeeHandbook.pdf",
                1
        );
        List<SearchResult> results = List.of(result1, result2);

        String context = retriever.buildContext(results);

        assertNotNull(context);
        assertTrue(context.contains("EmployeeHandbook.pdf"));
        assertTrue(context.contains("Employees receive 20 days of PTO"));
        assertTrue(context.contains("Additional unpaid leave"));
        assertTrue(context.contains("0.95"));
        assertTrue(context.contains("0.85"));
    }

    /**
     * Test buildContext with empty results.
     */
    @Test
    void testBuildContextWithEmptyResults() {
        String context = retriever.buildContext(new ArrayList<>());

        assertEquals("", context);
    }

    /**
     * Test buildContext formats multiple documents correctly.
     */
    @Test
    void testBuildContextFormatting() {
        SearchResult result1 = new SearchResult("Content 1", 0.9, 1, "Doc1.pdf", 0);
        SearchResult result2 = new SearchResult("Content 2", 0.8, null, "Doc2.pdf", null);
        List<SearchResult> results = List.of(result1, result2);

        String context = retriever.buildContext(results);

        // Should handle null page numbers gracefully
        assertTrue(context.contains("Doc1.pdf"));
        assertTrue(context.contains("Doc2.pdf"));
        assertTrue(context.contains("Content 1"));
        assertTrue(context.contains("Content 2"));
    }

    /**
     * Test retrieve with exception from vector store (graceful degradation).
     */
    @Test
    void testRetrieveWithVectorStoreException() {
        String query = "What is the vacation policy?";
        float[] embedding = new float[]{0.1f, 0.2f, 0.3f};
        EmbeddingResult embeddingResult = new EmbeddingResult(embedding, 3, "TestModel");

        when(embeddingService.generateEmbedding(query)).thenReturn(embeddingResult);
        when(vectorStoreService.findNearest(embedding, 5))
                .thenThrow(new RuntimeException("Vector store unavailable"));

        // Should not throw, gracefully degrade
        List<SearchResult> results = retriever.retrieve(query);

        assertEquals(0, results.size());
    }
}
