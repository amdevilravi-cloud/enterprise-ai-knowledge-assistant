package com.enterprise.ai.knowledge.assistant.demo.rag;

import com.enterprise.ai.knowledge.assistant.demo.repository.SearchResult;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * PromptBuilder component for RAG pipeline.
 * Responsible for building the final prompt sent to the LLM by injecting retrieved context.
 */
@Component
public class PromptBuilder {

    private static final String DEFAULT_SYSTEM_PROMPT = """
            You are an enterprise AI knowledge assistant. Answer questions based on the provided context.
            If the context doesn't contain relevant information, acknowledge this and provide a helpful response based on your knowledge.
            Always cite the source documents when referencing specific information from the context.
            """;

    private static final String CONTEXT_TEMPLATE = """
            Based on the following retrieved documents, please answer the user's question:
            
            ==== RETRIEVED CONTEXT ====
            %s
            ==== END CONTEXT ====
            
            User Question: %s
            """;

    /**
     * Build a RAG prompt by injecting retrieved context.
     *
     * @param userQuery The original user query
     * @param context   Formatted context from retrieved chunks
     * @return The final prompt to send to the LLM
     */
    public String buildRagPrompt(String userQuery, String context) {
        if (context == null || context.isEmpty()) {
            return userQuery;
        }
        return String.format(CONTEXT_TEMPLATE, context, userQuery);
    }

    /**
     * Build a RAG prompt with results (combines retrieval results into context).
     *
     * @param userQuery The original user query
     * @param results   List of SearchResult from retrieval
     * @return The final prompt to send to the LLM
     */
    public String buildRagPrompt(String userQuery, List<SearchResult> results) {
        if (results == null || results.isEmpty()) {
            return userQuery;
        }

        StringBuilder contextBuilder = new StringBuilder();
        for (int i = 0; i < results.size(); i++) {
            SearchResult result = results.get(i);
            contextBuilder.append(String.format("[Document %d] %s", i + 1, result.getDocumentName()));
            if (result.getPageNumber() != null) {
                contextBuilder.append(String.format(" (Page %d)", result.getPageNumber()));
            }
            contextBuilder.append("\n");
            contextBuilder.append(result.getContent()).append("\n");
            contextBuilder.append("---\n");
        }

        return buildRagPrompt(userQuery, contextBuilder.toString());
    }

    /**
     * Get the default system prompt for the LLM.
     *
     * @return System prompt
     */
    public String getSystemPrompt() {
        return DEFAULT_SYSTEM_PROMPT;
    }
}

