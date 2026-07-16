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
              SYSTEM
            
            You are an Enterprise AI Knowledge Assistant.
            
       
            
            Rules:
            
            1. Answer ONLY using the retrieved context.
            2. Never infer missing information.
            3. If the retrieved context contains placeholders,
               unresolved values, optional text, or alternatives
               such as:
                  [can/cannot]
                  [Company Name]
                  [TBD]
                  [Optional]
                  
                  quote it exactly.
            
         
            
            4. When the source contains ambiguous wording,
               quote it exactly.
            
            5. Do not rewrite ambiguous statements.
            
            6. Never fabricate information.
            
            7. Cite the supporting document and page.
            
             """;

    private static final String CONTEXT_TEMPLATE = """
            
            
            --------------------
            CONTEXT
            
            %s
            
            --------------------
            QUESTION
            
            %s
            
            --------------------
            ANSWER
            
           
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
            contextBuilder.append("""
                    ====================================
                    Document : %s
                    Page     : %d
                    Chunk    : %d
                    Score    : %.3f
                    
                    Content:
                    %s
                    
                    ====================================
                    
                    """.formatted(
                    result.getDocumentName(),
                    result.getPageNumber(),
                    result.getChunkIndex(),
                    result.getScore(),
                    result.getContent()
            ));
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

