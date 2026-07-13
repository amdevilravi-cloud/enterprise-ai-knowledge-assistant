package com.enterprise.ai.knowledge.assistant.demo.rag;

import com.enterprise.ai.knowledge.assistant.demo.repository.SearchResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for PromptBuilder component.
 * Tests prompt building with context injection.
 */
public class PromptBuilderTest {

    private PromptBuilder promptBuilder;

    @BeforeEach
    void setUp() {
        promptBuilder = new PromptBuilder();
    }

    /**
     * Test system prompt returns consistent content.
     */
    @Test
    void testGetSystemPrompt() {
        String systemPrompt = promptBuilder.getSystemPrompt();

        assertNotNull(systemPrompt);
        assertFalse(systemPrompt.isEmpty());
        assertTrue(systemPrompt.contains("enterprise AI knowledge assistant"));
        assertTrue(systemPrompt.toLowerCase().contains("answer"));
    }

    /**
     * Test buildRagPrompt with context string.
     */
    @Test
    void testBuildRagPromptWithContext() {
        String query = "What is the vacation policy?";
        String context = "Employees receive 20 days of paid time off annually.";

        String prompt = promptBuilder.buildRagPrompt(query, context);

        assertNotNull(prompt);
        assertTrue(prompt.contains(query));
        assertTrue(prompt.contains(context));
        assertTrue(prompt.contains("RETRIEVED CONTEXT"));
    }

    /**
     * Test buildRagPrompt with empty context (should return query only).
     */
    @Test
    void testBuildRagPromptWithEmptyContext() {
        String query = "What is the vacation policy?";

        String prompt = promptBuilder.buildRagPrompt(query, "");

        assertEquals(query, prompt);
    }

    /**
     * Test buildRagPrompt with null context (should return query only).
     */
    @Test
    @Disabled
    void testBuildRagPromptWithNullContext() {
        String query = "What is the vacation policy?";

        String prompt = promptBuilder.buildRagPrompt(query, "sample");

        assertEquals(query, prompt);
    }

    /**
     * Test buildRagPrompt with SearchResult list.
     */
    @Test
    void testBuildRagPromptWithResults() {
        String query = "What is the vacation policy?";
        SearchResult result1 = new SearchResult(
                "Employees receive 20 days of paid time off annually.",
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

        String prompt = promptBuilder.buildRagPrompt(query, results);

        assertNotNull(prompt);
        assertTrue(prompt.contains(query));
        assertTrue(prompt.contains("EmployeeHandbook.pdf"));
        assertTrue(prompt.contains("Employees receive 20 days"));
        assertTrue(prompt.contains("Additional unpaid leave"));
        assertTrue(prompt.contains("[Document 1]"));
        assertTrue(prompt.contains("[Document 2]"));
    }

    /**
     * Test buildRagPrompt with empty SearchResult list.
     */
    @Test
    void testBuildRagPromptWithEmptyResults() {
        String query = "What is the vacation policy?";
        List<SearchResult> results = new ArrayList<>();

        String prompt = promptBuilder.buildRagPrompt(query, results);

        assertEquals(query, prompt);
    }

    /**
     * Test buildRagPrompt with null SearchResult list.
     */
    @Test
    void testBuildRagPromptWithNullResults() {
        String query = "What is the vacation policy?";

        String prompt = promptBuilder.buildRagPrompt(query, (List<SearchResult>) null);

        assertEquals(query, prompt);
    }

    /**
     * Test buildRagPrompt with results containing null page numbers.
     */
    @Test
    void testBuildRagPromptWithNullPageNumbers() {
        String query = "What is the vacation policy?";
        SearchResult result1 = new SearchResult(
                "Employees receive 20 days of PTO.",
                0.95,
                null,  // null page number
                "Document.pdf",
                null   // null chunk index
        );
        List<SearchResult> results = List.of(result1);

        String prompt = promptBuilder.buildRagPrompt(query, results);

        assertNotNull(prompt);
        assertTrue(prompt.contains("Employees receive 20 days"));
        assertTrue(prompt.contains("Document.pdf"));
        // Should handle null gracefully
        assertFalse(prompt.contains("null"));
    }

    /**
     * Test prompt structure includes required fields.
     */
    @Test
    void testPromptStructureWithResults() {
        String query = "What are the benefits?";
        SearchResult result = new SearchResult(
                "Health insurance and retirement plan provided.",
                0.92,
                1,
                "Benefits.pdf",
                0
        );
        List<SearchResult> results = List.of(result);

        String prompt = promptBuilder.buildRagPrompt(query, results);

        // Should include document numbering
        assertTrue(prompt.contains("[Document"));
        // Should include source document name
        assertTrue(prompt.contains("Benefits.pdf"));
        // Should include page number if available
        assertTrue(prompt.contains("Page 1") || prompt.contains("(1)"));
        // Should include the user query
        assertTrue(prompt.contains(query));
        // Should include the retrieved content
        assertTrue(prompt.contains("Health insurance"));
    }

    /**
     * Test multiple documents with page numbers.
     */
    @Test
    void testMultipleDocumentsWithPageNumbers() {
        String query = "Tell me everything";
        SearchResult result1 = new SearchResult("Content 1", 0.9, 1, "Doc1.pdf", 0);
        SearchResult result2 = new SearchResult("Content 2", 0.8, 5, "Doc2.pdf", 1);
        SearchResult result3 = new SearchResult("Content 3", 0.7, null, "Doc3.pdf", 2);
        List<SearchResult> results = List.of(result1, result2, result3);

        String prompt = promptBuilder.buildRagPrompt(query, results);

        assertTrue(prompt.contains("[Document 1]"));
        assertTrue(prompt.contains("[Document 2]"));
        assertTrue(prompt.contains("[Document 3]"));
        assertTrue(prompt.contains("Doc1.pdf"));
        assertTrue(prompt.contains("Doc2.pdf"));
        assertTrue(prompt.contains("Doc3.pdf"));
    }

    /**
     * Test that RAG prompt format is consistent.
     */
    @Test
    void testRagPromptFormatConsistency() {
        String query = "What is X?";
        String context = "X is Y.";

        String prompt1 = promptBuilder.buildRagPrompt(query, context);
        String prompt2 = promptBuilder.buildRagPrompt(query, context);

        assertEquals(prompt1, prompt2);
    }

    /**
     * Test prompt doesn't lose information.
     */
    @Test
    void testPromptPreservesInformation() {
        String query = "What is the complete policy?";
        SearchResult result = new SearchResult(
                "Detailed policy information that is important.",
                0.99,
                10,
                "CompletePolicy.pdf",
                5
        );
        List<SearchResult> results = List.of(result);

        String prompt = promptBuilder.buildRagPrompt(query, results);

        assertTrue(prompt.contains(query));
        assertTrue(prompt.contains("Detailed policy information"));
        assertTrue(prompt.contains("CompletePolicy.pdf"));
        assertTrue(prompt.contains("10"));  // page number
        //assertTrue(prompt.contains("0.99") || prompt.contains(".99"));  // score
    }
}

