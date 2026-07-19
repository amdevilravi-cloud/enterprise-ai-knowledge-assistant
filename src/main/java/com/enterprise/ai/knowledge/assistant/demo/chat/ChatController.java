package com.enterprise.ai.knowledge.assistant.demo.chat;

import com.enterprise.ai.knowledge.assistant.demo.chat.dto.ChatResponse;
import com.enterprise.ai.knowledge.assistant.demo.conversation.dto.ConversationRequest;
import com.enterprise.ai.knowledge.assistant.demo.conversation.dto.ConversationStartResponse;
import com.enterprise.ai.knowledge.assistant.demo.conversation.service.ConversationService;
import com.enterprise.ai.knowledge.assistant.demo.rag.PromptBuilder;
import com.enterprise.ai.knowledge.assistant.demo.rag.Retriever;
import com.enterprise.ai.knowledge.assistant.demo.rag.dto.RagPrompt;
import com.enterprise.ai.knowledge.assistant.demo.repository.SearchResult;
import org.springframework.ai.chat.client.ChatClient;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;
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
@RequestMapping("/api/chat/v1")
@Tag(
        name = "Chat API v1",
        description = "Advanced chat endpoints with versioning support. Provides simple LLM chat and RAG-enhanced context-aware responses."
)
public class ChatController {


    private final ChatClient chatClient;
    private final Retriever retriever;
    private final PromptBuilder promptBuilder;
    private final ConversationService conversationService;

    /**
     * Constructor-based dependency injection for ChatClient, Retriever, and PromptBuilder.
     *
     * The ChatClient bean is conditionally created by ChatClientConfig:
     * - Uses OpenAI when app.llm.provider=openai (default)
     * - Uses LM Studio when app.llm.provider=lmstudio
     */
    public ChatController(ChatClient chatClient, Retriever retriever, PromptBuilder promptBuilder,
                          ConversationService conversationService) {
        this.chatClient = chatClient;
        this.retriever = retriever;
        this.promptBuilder = promptBuilder;
        this.conversationService = conversationService;
    }

    /**
            @Operation(
                summary = "Simple Chat (No RAG)",
                description = "Send a message directly to the LLM without any document context retrieval. " +
                             "Best for general knowledge questions or when no documents are relevant.",
                tags = {"Chat API v1"}
            )
            @ApiResponses(value = {
                @ApiResponse(
                    responseCode = "200",
                    description = "Successful response from LLM",
                    content = @Content(
                        mediaType = "application/json",
                        schema = @Schema(type = "string", example = "This is a response from the LLM.")
                    )
                ),
                @ApiResponse(
                    responseCode = "400",
                    description = "Missing required message parameter"
                ),
                @ApiResponse(
                    responseCode = "500",
                    description = "LLM service error or connection failure"
                )
            })
     * Chat endpoint that sends a message to the configured LLM (without RAG).
                @Parameter(
                    name = "message",
                    description = "The user query or message to send to the LLM",
                    required = true,
                    example = "What is the capital of France?"
                )
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
            @Operation(
                summary = "RAG-Enhanced Chat (With Citations)",
                description = "Send a query with semantic search and retrieval-augmented generation. " +
                             "Retrieves relevant document chunks, injects them as context, and returns " +
                             "grounded answers with source citations. " +
                             "Two-stage retrieval: vector search → re-ranking for improved relevance.",
                tags = {"Chat API v1"}
            )
            @ApiResponses(value = {
                @ApiResponse(
                    responseCode = "200",
                    description = "Successful RAG response with citations and source documents",
                    content = @Content(
                        mediaType = "application/json",
                        schema = @Schema(implementation = ChatResponse.class)
                    )
                ),
                @ApiResponse(
                    responseCode = "400",
                    description = "Invalid parameters (e.g., negative topK values)"
                ),
                @ApiResponse(
                    responseCode = "500",
                    description = "Error during retrieval, embedding, or LLM call"
                )
            })
     * @param vectorTopK Number of context chunks to retrieve from vector store (default: 20)
                @Parameter(
                    name = "message",
                    description = "User query for document-based retrieval and generation",
                    required = true,
                    example = "What is the company vacation policy?"
                )
     * @param finalTopN  Number of final candidates after re-ranking (default: 3)
                @Parameter(
                    name = "vectorTopK",
                    description = "Number of document chunks to initially retrieve from vector store via semantic search. " +
                                 "Higher values provide broader context but slower performance. Default: 20",
                    example = "20"
                )
     * @return ChatResponse with answer and citations
                @Parameter(
                    name = "finalTopN",
                    description = "Number of final candidates after re-ranking and filtering. " +
                                 "Lower values focus on most relevant documents. Default: 3",
                    example = "3"
                )
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

    /**
     * Conversation endpoint that starts a new conversation.
     *
            @Operation(
                summary = "Continue Multi-Turn Conversation",
                description = "Send a message in an existing conversation thread with history context. " +
                             "Uses conversation history (configurable depth) to provide context for responses. " +
                             "Returns RAG-enhanced answer with citations.",
                tags = {"Chat API v1"}
            )
            @ApiResponses(value = {
                @ApiResponse(
                    responseCode = "200",
                    description = "Successful response with conversation context",
                    content = @Content(
                        mediaType = "application/json",
                        schema = @Schema(implementation = ChatResponse.class)
                    )
                ),
                @ApiResponse(
                    responseCode = "400",
                    description = "Invalid request parameters or body"
                ),
                @ApiResponse(
                    responseCode = "404",
                    description = "Conversation not found"
                ),
                @ApiResponse(
                    responseCode = "500",
                    description = "Error processing conversation or retrieving history"
                )
            })
     * @param request The conversation request
                @Parameter(
                    name = "conversationId",
                    description = "Unique identifier of the conversation to continue",
                    required = true,
                    example = "123e4567-e89b-12d3-a456-426614174000"
                )
     * @return The conversation start response
                @Parameter(
                    description = "Conversation request containing message and optional history depth"
                )
  */
    @PostMapping("/converse")
    public ChatResponse converse(@RequestParam UUID conversationId,
                                  @RequestBody ConversationRequest request) {
        int historyDepth = request.getHistoryDepth() > 0 ? request.getHistoryDepth() : 5;
        return conversationService.chat(conversationId, request.getMessage(), historyDepth);
    }
}