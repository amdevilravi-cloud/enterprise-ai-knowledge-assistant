package com.enterprise.ai.knowledge.assistant.demo.chat;

import com.enterprise.ai.knowledge.assistant.demo.chat.dto.ChatResponse;
import com.enterprise.ai.knowledge.assistant.demo.rag.PromptBuilder;
import com.enterprise.ai.knowledge.assistant.demo.rag.Retriever;
import com.enterprise.ai.knowledge.assistant.demo.rag.dto.RagPrompt;
import com.enterprise.ai.knowledge.assistant.demo.repository.SearchResult;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

/**
 * REST Controller for chat operations.
 *
 * Supports both OpenAI and local LM Studio LLMs based on configuration.
 * See ChatClientConfig for conditional bean initialization.
 *
 * Endpoints:
 * - /api/chat (legacy) - Simple chat without RAG
 * - /api/chat/rag - RAG-enhanced chat with document retrieval and context injection
 */
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatClient chatClient;
    private final Retriever retriever;
    private final PromptBuilder promptBuilder;

    /**
     * Constructor-based dependency injection for ChatClient, Retriever, and PromptBuilder.
     *
     * The ChatClient bean is conditionally created by ChatClientConfig:
     * - Uses OpenAI when app.llm.provider=openai (default)
     * - Uses LM Studio when app.llm.provider=lmstudio
     */
    public ChatController(ChatClient chatClient, Retriever retriever, PromptBuilder promptBuilder) {
        this.chatClient = chatClient;
        this.retriever = retriever;
        this.promptBuilder = promptBuilder;
    }

    /**
     * Chat endpoint that sends a message to the configured LLM (without RAG).
     *
     * @param message The user's message
     * @return The LLM's response
     */
    @GetMapping
    public String chat(@RequestParam String message) {
        return chatClient.prompt()
                .user(message)
                .call()
                .content();
    }

    /**
     * RAG-enhanced chat endpoint that retrieves context before sending to the LLM.
     *
     * Process:
     * 1. Two-stage retrieval: vector search (top 20) → filter → re-rank → top 3
     * 2. Build a prompt with the retrieved context injected
     * 3. Send the augmented prompt to the LLM
     * 4. Return the response with citations to source documents
     *
     * @param message The user's question/message
     * @param vectorTopK Number of context chunks to retrieve from vector store (default: 20)
     * @param finalTopN  Number of final candidates after re-ranking (default: 3)
     * @return ChatResponse with answer and citations
     */
    @GetMapping("/rag")
    public ChatResponse ragChat(@RequestParam String message,
                                @RequestParam(name = "vectorTopK", defaultValue = "20") int vectorTopK,
                                @RequestParam(name = "finalTopN", defaultValue = "3") int finalTopN) {
        try {
            // Step 1-4: Two-stage retrieval + re-ranking
            List<SearchResult> results = retriever.retrieveAndRerank(message, vectorTopK, finalTopN);

            // Step 2: Build RAG prompt with context (returns first-class RagPrompt object)
            RagPrompt ragPrompt = promptBuilder.buildRagPrompt(message, results);

            // Log metadata for observability
            System.out.println("RAG Prompt Metadata: " + ragPrompt.metadata());

            // Step 3: Send augmented prompt to LLM
            String answer = chatClient.prompt()
                    .system(ragPrompt.systemPrompt())
                    .user(ragPrompt.userPrompt())
                    .call()
                    .content();

            // Step 4: Extract citations from results
            List<ChatResponse.Citation> citations = results.stream()
                    .map(r -> new ChatResponse.Citation(
                            r.getDocumentName(),
                            r.getPageNumber(),
                            r.getChunkIndex(),
                            r.getScore(),
                            r.getContent()
                    ))
                    .collect(Collectors.toList());

            return new ChatResponse(answer, citations, !results.isEmpty(), results.size());
        } catch (Exception e) {
            // Fallback to simple chat on error
            String answer = chatClient.prompt()
                    .user(message)
                    .call()
                    .content();
            return new ChatResponse(answer, List.of(), false, 0);
        }
    }
}