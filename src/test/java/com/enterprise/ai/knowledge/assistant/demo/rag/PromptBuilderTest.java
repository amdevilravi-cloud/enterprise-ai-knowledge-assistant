//package com.enterprise.ai.knowledge.assistant.demo.rag;
//
//import com.enterprise.ai.knowledge.assistant.demo.rag.dto.RagPrompt;
//import com.enterprise.ai.knowledge.assistant.demo.rag.template.DefaultPromptTemplate;
//import com.enterprise.ai.knowledge.assistant.demo.rag.template.PromptTemplate;
//import com.enterprise.ai.knowledge.assistant.demo.repository.SearchResult;
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.Test;
//
//import java.util.ArrayList;
//import java.util.List;
//import java.util.Map;
//
//import static org.junit.jupiter.api.Assertions.*;
//
///**
// * Unit tests for PromptBuilder component.
// * Tests prompt building with context injection and RagPrompt objects.
// */
//public class PromptBuilderTest {
//
//    private PromptBuilder promptBuilder;
//    private PromptTemplate defaultTemplate;
//
//    @BeforeEach
//    void setUp() {
//        defaultTemplate = new DefaultPromptTemplate();
//        promptBuilder = new PromptBuilder(defaultTemplate);
//    }
//
//    /**
//     * Test system prompt returns consistent content.
//     */
//    @Test
//    void testGetSystemPrompt() {
//        String systemPrompt = promptBuilder.getSystemPrompt();
//
//        assertNotNull(systemPrompt);
//        assertFalse(systemPrompt.isEmpty());
//        assertTrue(systemPrompt.toLowerCase().contains("enterprise ai knowledge assistant"));
//        assertTrue(systemPrompt.toLowerCase().contains("answer"));
//    }
//
//    /**
//     * Test buildRagPrompt returns RagPrompt record with system and user prompts.
//     */
//    @Test
//    void testBuildRagPromptReturnsRagPrompt() {
//        String query = "What is the vacation policy?";
//        SearchResult result1 = new SearchResult(
//                "Employees receive 20 days of paid time off annually.",
//                0.95,
//                2,
//                "EmployeeHandbook.pdf",
//                0
//        );
//        List<SearchResult> results = List.of(result1);
//
//        RagPrompt prompt = promptBuilder.buildRagPrompt(query, results);
//
//        assertNotNull(prompt);
//        assertNotNull(prompt.systemPrompt());
//        assertNotNull(prompt.userPrompt());
//        assertEquals(results, prompt.sources());
//    }
//
//    /**
//     * Test buildRagPrompt with empty results.
//     */
//    @Test
//    void testBuildRagPromptWithEmptyResults() {
//        String query = "What is the vacation policy?";
//
//        RagPrompt prompt = promptBuilder.buildRagPrompt(query, new ArrayList<>());
//
//        assertNotNull(prompt);
//        assertTrue(prompt.userPrompt().contains(query));
//        assertEquals(0, prompt.getSourceCount());
//    }
//
//    /**
//     * Test buildRagPrompt with null results.
//     */
//    @Test
//    void testBuildRagPromptWithNullResults() {
//        String query = "What is the vacation policy?";
//
//        RagPrompt prompt = promptBuilder.buildRagPrompt(query, null);
//
//        assertNotNull(prompt);
//        assertNotNull(prompt.userPrompt());
//        assertEquals(0, prompt.getSourceCount());
//    }
//
//    /**
//     * Test buildRagPrompt with SearchResult list includes context.
//     */
//    @Test
//    void testBuildRagPromptWithResults() {
//        String query = "What is the vacation policy?";
//        SearchResult result1 = new SearchResult(
//                "Employees receive 20 days of paid time off annually.",
//                0.95,
//                2,
//                "EmployeeHandbook.pdf",
//                0
//        );
//        SearchResult result2 = new SearchResult(
//                "Additional unpaid leave available upon request.",
//                0.85,
//                3,
//                "EmployeeHandbook.pdf",
//                1
//        );
//        List<SearchResult> results = List.of(result1, result2);
//
//        RagPrompt prompt = promptBuilder.buildRagPrompt(query, results);
//
//        assertNotNull(prompt);
//        assertTrue(prompt.userPrompt().contains(query));
//        assertTrue(prompt.userPrompt().contains("Employees receive 20 days"));
//        assertTrue(prompt.userPrompt().contains("Additional unpaid leave"));
//        assertTrue(prompt.userPrompt().contains("EmployeeHandbook.pdf"));
//    }
//
//    /**
//     * Test buildRagPrompt metadata contains source count.
//     */
//    @Test
//    void testBuildRagPromptMetadata() {
//        String query = "What is the vacation policy?";
//        SearchResult result1 = new SearchResult("Content 1", 0.95, 2, "Doc1.pdf", 0);
//        SearchResult result2 = new SearchResult("Content 2", 0.85, 3, "Doc2.pdf", 1);
//        List<SearchResult> results = List.of(result1, result2);
//
//        RagPrompt prompt = promptBuilder.buildRagPrompt(query, results);
//
//        assertNotNull(prompt.metadata());
//        assertEquals(2, prompt.metadata().get("sourceCount"));
//        assertTrue(prompt.metadata().containsKey("templateName"));
//        assertTrue(prompt.metadata().containsKey("averageRelevanceScore"));
//    }
//
//    /**
//     * Test buildRagPrompt calculates average relevance score.
//     */
//    @Test
//    void testBuildRagPromptAverageRelevanceScore() {
//        String query = "Test query";
//        SearchResult result1 = new SearchResult("Content 1", 0.9, 1, "Doc1.pdf", 0);
//        SearchResult result2 = new SearchResult("Content 2", 0.8, 2, "Doc2.pdf", 1);
//        List<SearchResult> results = List.of(result1, result2);
//
//        RagPrompt prompt = promptBuilder.buildRagPrompt(query, results);
//
//        double avgScore = (Double) prompt.metadata().get("averageRelevanceScore");
//        assertEquals(0.85, avgScore, 0.001);
//    }
//
//    /**
//     * Test buildRagPrompt includes template name in metadata.
//     */
//    @Test
//    void testBuildRagPromptTemplateMetadata() {
//        String query = "Test query";
//        SearchResult result = new SearchResult("Content", 0.9, 1, "Doc.pdf", 0);
//
//        RagPrompt prompt = promptBuilder.buildRagPrompt(query, List.of(result));
//
//        assertEquals("default", prompt.metadata().get("templateName"));
//    }
//
//    /**
//     * Test getFullPrompt concatenates system and user prompts.
//     */
//    @Test
//    void testGetFullPrompt() {
//        String query = "Test query";
//        SearchResult result = new SearchResult("Content", 0.9, 1, "Doc.pdf", 0);
//
//        RagPrompt prompt = promptBuilder.buildRagPrompt(query, List.of(result));
//        String fullPrompt = prompt.getFullPrompt();
//
//        assertNotNull(fullPrompt);
//        assertTrue(fullPrompt.contains(prompt.systemPrompt()));
//        assertTrue(fullPrompt.contains(prompt.userPrompt()));
//    }
//
//    /**
//     * Test RagPrompt record helpers - getSourceCount and getAverageRelevanceScore.
//     */
//    @Test
//    void testRagPromptHelpers() {
//        String query = "Test query";
//        SearchResult result1 = new SearchResult("Content 1", 0.9, 1, "Doc1.pdf", 0);
//        SearchResult result2 = new SearchResult("Content 2", 0.8, 2, "Doc2.pdf", 1);
//        List<SearchResult> results = List.of(result1, result2);
//
//        RagPrompt prompt = promptBuilder.buildRagPrompt(query, results);
//
//        assertEquals(2, prompt.getSourceCount());
//        assertEquals(0.85, prompt.getAverageRelevanceScore(), 0.001);
//    }
//
//    /**
//     * Test PromptTemplate interface is being used for rendering.
//     */
//    @Test
//    void testPromptTemplateUsage() {
//        String query = "Test query";
//        SearchResult result = new SearchResult("Content", 0.95, 1, "Doc.pdf", 0);
//        List<SearchResult> results = List.of(result);
//
//        RagPrompt prompt = promptBuilder.buildRagPrompt(query, results, defaultTemplate);
//
//        assertNotNull(prompt.systemPrompt());
//        assertNotNull(prompt.userPrompt());
//        assertTrue(prompt.userPrompt().contains(query));
//        assertTrue(prompt.userPrompt().contains("Content"));
//    }
//}
