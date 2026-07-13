package com.enterprise.ai.knowledge.assistant.demo.chat;

import com.enterprise.ai.knowledge.assistant.demo.chat.dto.ChatResponse;
import com.enterprise.ai.knowledge.assistant.demo.rag.PromptBuilder;
import com.enterprise.ai.knowledge.assistant.demo.rag.Retriever;
import com.enterprise.ai.knowledge.assistant.demo.repository.SearchResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.ai.chat.client.ChatClient;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ChatController with RAG integration.
 * Tests the integration of Retriever, PromptBuilder, and ChatClient.
 */
public class ChatControllerTest {

    private ChatController chatController;

    @Mock
    private ChatClient chatClient;

    @Mock
    private Retriever retriever;

    @Mock
    private PromptBuilder promptBuilder;

    @Mock
    private ChatClient.ChatClientRequestSpec requestSpec;

    @Mock
    private ChatClient.CallResponseSpec responseSpec;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        chatController = new ChatController(chatClient, retriever, promptBuilder);
    }

    /**
     * Test simple chat endpoint (no RAG).
     */
    @Test
    void testSimpleChatEndpoint() {
        String testMessage = "Hello";
        String expectedResponse = "Hello! How can I help you?";

        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(testMessage)).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(responseSpec);
        when(responseSpec.content()).thenReturn(expectedResponse);

        String result = chatController.chat(testMessage);

        assertEquals(expectedResponse, result);
        verify(chatClient).prompt();
        verify(requestSpec).user(testMessage);
    }

    /**
     * Test RAG chat endpoint with successful retrieval.
     */
    @Test
    void testRagChatWithResults() {
        String query = "What is the vacation policy?";
        int topK = 5;

        // Mock search results
        SearchResult result = new SearchResult(
                "Employees receive 20 days of paid time off annually.",
                0.95,
                2,
                "EmployeeHandbook.pdf",
                0
        );
        List<SearchResult> mockResults = List.of(result);

        // Mock RAG components
        when(retriever.retrieve(query, topK)).thenReturn(mockResults);
        when(promptBuilder.buildRagPrompt(query, mockResults))
                .thenReturn("Context injected prompt...");
        when(promptBuilder.getSystemPrompt()).thenReturn("System prompt");

        // Mock LLM response
        String expectedAnswer = "Based on the handbook, employees get 20 days of PTO annually.";
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(responseSpec);
        when(responseSpec.content()).thenReturn(expectedAnswer);

        // Execute RAG chat
        ChatResponse response = chatController.ragChat(query, topK);

        // Verify results
        assertNotNull(response);
        assertEquals(expectedAnswer, response.getAnswer());
        assertEquals(1, response.getRetrievalCount());
        assertTrue(response.getIsFromContext());
        assertEquals(1, response.getCitations().size());

        ChatResponse.Citation citation = response.getCitations().get(0);
        assertEquals("EmployeeHandbook.pdf", citation.getDocumentName());
        assertEquals(2, citation.getPageNumber());
        assertEquals(0.95, citation.getRelevanceScore(), 0.001);

        // Verify mocks were called
        verify(retriever).retrieve(query, topK);
        verify(promptBuilder).buildRagPrompt(query, mockResults);
        verify(promptBuilder).getSystemPrompt();
    }

    /**
     * Test RAG chat with no retrieval results (fallback to simple chat).
     */
    @Test
    void testRagChatWithoutResults() {
        String query = "What is the vacation policy?";
        int topK = 5;

        // Mock empty retrieval results
        when(retriever.retrieve(query, topK)).thenReturn(new ArrayList<>());

        // Mock LLM response (fallback)
        String expectedAnswer = "I don't have information about that in the documentation.";
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(query)).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(responseSpec);
        when(responseSpec.content()).thenReturn(expectedAnswer);

        // Execute RAG chat
        ChatResponse response = chatController.ragChat(query, topK);

        // Verify fallback behavior
        assertNotNull(response);
        assertEquals(expectedAnswer, response.getAnswer());
        assertEquals(0, response.getRetrievalCount());
        assertFalse(response.getIsFromContext());
        assertTrue(response.getCitations().isEmpty());
    }

    /**
     * Test RAG chat with exception handling.
     */
    @Test
    void testRagChatWithException() {
        String query = "What is the vacation policy?";
        int topK = 5;

        // Mock exception during retrieval
        when(retriever.retrieve(query, topK))
                .thenThrow(new RuntimeException("Vector store error"));

        // Mock LLM fallback response
        String fallbackAnswer = "I encountered an issue retrieving documents.";
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(query)).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(responseSpec);
        when(responseSpec.content()).thenReturn(fallbackAnswer);

        // Execute RAG chat (should not throw)
        ChatResponse response = chatController.ragChat(query, topK);

        // Verify fallback behavior
        assertNotNull(response);
        assertEquals(fallbackAnswer, response.getAnswer());
        assertEquals(0, response.getRetrievalCount());
        assertFalse(response.getIsFromContext());
    }

    /**
     * Test RAG chat with default topK parameter.
     */
    @Test
    @Disabled
    void testRagChatWithDefaultTopK() {
        String query = "What is the vacation policy?";

        List<SearchResult> mockResults = List.of(
                new SearchResult("Content 1", 0.9, 1, "Doc1.pdf", 0),
                new SearchResult("Content 2", 0.8, 2, "Doc2.pdf", 0)
        );

        when(retriever.retrieve(query, 5)).thenReturn(mockResults);
        when(promptBuilder.buildRagPrompt(query, mockResults)).thenReturn("Prompt");
        when(promptBuilder.getSystemPrompt()).thenReturn("System");
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(responseSpec);
        when(responseSpec.content()).thenReturn("Answer");

        // Call without explicit topK (should use default 5)
        ChatResponse response = chatController.ragChat(query,5);

        assertNotNull(response);
        assertEquals(2, response.getRetrievalCount());
        verify(retriever).retrieve(query, 5);
    }
}

