package com.enterprise.ai.knowledge.assistant.demo.rag;

import com.enterprise.ai.knowledge.assistant.demo.rag.dto.RagPrompt;
import com.enterprise.ai.knowledge.assistant.demo.rag.template.PromptTemplate;
import com.enterprise.ai.knowledge.assistant.demo.repository.SearchResult;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * PromptBuilder component for RAG pipeline.
 * Responsible for building first-class RagPrompt objects by injecting retrieved context.
 */
@Component
public class PromptBuilder {

    private final PromptTemplate defaultTemplate;

    public PromptBuilder(PromptTemplate defaultTemplate) {
        this.defaultTemplate = defaultTemplate;
    }

    /**
     * Build a RagPrompt from a query and search results using the default template.
     */
    public RagPrompt buildRagPrompt(String query, List<SearchResult> results) {
        return buildRagPrompt(query, results, defaultTemplate);
    }

    /**
     * Build a RagPrompt from a query and search results using a specified template.
     */
    public RagPrompt buildRagPrompt(String query, List<SearchResult> results, PromptTemplate template) {
        if (template == null) {
            template = defaultTemplate;
        }

        String system = template.renderSystem(results);
        String user = template.renderUser(query, results);

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("templateName", template.getName());
        metadata.put("sourceCount", results == null ? 0 : results.size());
        if (results != null && !results.isEmpty()) {
            double avgScore = results.stream()
                    .mapToDouble(SearchResult::getScore)
                    .average()
                    .orElse(0.0);
            metadata.put("averageRelevanceScore", avgScore);
        }

        return new RagPrompt(system, user, results, metadata);
    }

    /**
     * Legacy method for backward compatibility - returns just the user prompt string.
     * Consider migrating to buildRagPrompt() which returns RagPrompt object.
     */
    @Deprecated(forRemoval = true)
    public String buildRagPromptLegacy(String query, List<SearchResult> results) {
        RagPrompt prompt = buildRagPrompt(query, results);
        return prompt.userPrompt();
    }

    /**
     * Get the system prompt from the default template.
     */
    public String getSystemPrompt() {
        return defaultTemplate.renderSystem(null);
    }
}
