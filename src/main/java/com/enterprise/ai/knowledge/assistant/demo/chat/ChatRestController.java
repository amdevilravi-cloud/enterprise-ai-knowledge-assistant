package com.enterprise.ai.knowledge.assistant.demo.chat;

import com.enterprise.ai.knowledge.assistant.demo.chat.dto.ChatResponse;
import com.enterprise.ai.knowledge.assistant.demo.chat.dto.Citation;
import com.enterprise.ai.knowledge.assistant.demo.chat.dto.DocumentSource;
import com.enterprise.ai.knowledge.assistant.demo.conversation.dto.ConversationRequest;
import com.enterprise.ai.knowledge.assistant.demo.conversation.service.ConversationService;
import com.enterprise.ai.knowledge.assistant.demo.rag.PromptBuilder;
import com.enterprise.ai.knowledge.assistant.demo.rag.Retriever;
import com.enterprise.ai.knowledge.assistant.demo.rag.dto.RagPrompt;
import com.enterprise.ai.knowledge.assistant.demo.rag.retriever.HybridRetriever;
import com.enterprise.ai.knowledge.assistant.demo.repository.SearchResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Chat REST API Controller with Swagger Documentation
 * <p>
 * Provides endpoints for:
 * - Simple LLM chat (no RAG)
 * - RAG-enhanced chat with document context and citations
 * - Conversation management
 */
@RestController
@RequestMapping("/api/chat")
@Slf4j
@Tag(
        name = "Chat API",
        description = "Chat endpoints for simple LLM queries and RAG-enhanced context-aware responses"
)
public class ChatRestController {

    private final ChatClient chatClient;
    private final Retriever retriever;
    private final HybridRetriever hybridRetriever;
    private final PromptBuilder promptBuilder;
    private final ConversationService conversationService;

    public ChatRestController(ChatClient chatClient, Retriever retriever, HybridRetriever hybridRetriever, PromptBuilder promptBuilder,
                              ConversationService conversationService) {
        this.chatClient = chatClient;
        this.retriever = retriever;
        this.hybridRetriever = hybridRetriever;
        this.promptBuilder = promptBuilder;
        this.conversationService = conversationService;
    }

    /**
     * Simple chat endpoint (No RAG)
     * <p>
     * Sends a query directly to the LLM without retrieving document context.
     * Best for general knowledge questions.
     */
    @GetMapping
    @Operation(
            summary = "Simple Chat (No RAG)",
            description = "Send a message to the LLM without document retrieval. " +
                    "Returns plain string response with no citations.",
            tags = {"Chat API"}
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Successful response from LLM",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(type = "string", example = "This is the LLM response.")
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid request parameters"
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Internal server error or LLM service unavailable"
            )
    })
    public String chat(
            @Parameter(
                    name = "message",
                    description = "User query to send to the LLM",
                    required = true,
                    example = "What is Spring Boot?"
            )
            @RequestParam String message
    ) {
        log.info("Simple chat query: {}", message);
        return chatClient.prompt()
                .user(message)
                .call()
                .content();
    }

    /**
     * RAG-Enhanced Chat endpoint
     * <p>
     * Retrieves relevant document chunks based on query, injects them as context,
     * and sends augmented prompt to LLM for grounded answers with citations.
     */
    @GetMapping("/rag")
    @Operation(
            summary = "RAG-Enhanced Chat",
            description = "Send a query with document retrieval and context injection. " +
                    "Returns answer with citations from uploaded documents. " +
                    "This endpoint is ideal for questions about uploaded knowledge base documents.",
            tags = {"Chat API"}
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Successful RAG response with citations",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ChatResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid request parameters (e.g., invalid topK)",
                    content = @Content(mediaType = "application/json")
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Internal server error during retrieval or LLM call"
            )
    })
    public ChatResponse ragChat(
            @Parameter(
                    name = "message",
                    description = "User query for document-based retrieval",
                    required = true,
                    example = "What is the vacation policy?"
            )
            @RequestParam String message,

            @Parameter(
                    name = "topK",
                    description = "Number of document chunks to retrieve. Default is 5. " +
                            "Higher values provide more context but may include less relevant chunks.",
                    example = "5"
            )
            @RequestParam(name = "vectorTopK", defaultValue = "20") int vectorTopK,
            @RequestParam(name = "finalTopN", defaultValue = "5") int finalTopN
    ) {
        log.info("RAG chat query: {} with vectorTopK: {} and finalTopN: {}", message, vectorTopK, finalTopN);

        try {
            // Step 1-4: Two-stage retrieval + re-ranking
             List<SearchResult> results = hybridRetriever.retrieveAndRerank(message, vectorTopK, finalTopN);

          //  List<SearchResult> results = hybridRetriever.retrieve(message, vectorTopK);


            // Step 2: Build RAG prompt with context (returns first-class RagPrompt object)
            RagPrompt ragPrompt = promptBuilder.buildRagPrompt(message, results);

            // Log metadata for observability
            System.out.println("RAG Prompt Metadata: " + ragPrompt.metadata());
            System.out.println("RAG System Prompt : " + ragPrompt.systemPrompt());
            System.out.println("RAG User Prompt : " + ragPrompt.userPrompt());

            // Step 3: Send augmented prompt to LLM
            String answer = chatClient.prompt()
                    .system(ragPrompt.systemPrompt())
                    .user(ragPrompt.userPrompt())
                    .call()
                    .content();

            // Step 4: Extract sourceDocuments and citations from results
            // Group SearchResults by documentId to build DocumentSource objects
            List<DocumentSource> sourceDocuments = results.stream()
                    .collect(Collectors.groupingBy(SearchResult::getDocumentId))
                    .entrySet()
                    .stream()
                    .map(entry -> buildDocumentSource(entry.getKey(), entry.getValue()))
                    .collect(Collectors.toList());


            return ChatResponse.builder()
                    .answer(answer)
                    .isFromContext(!results.isEmpty())
                    .retrievalCount(results.size())
                    .sourceDocuments(sourceDocuments)
                    .build();
        } catch (Exception e) {
            log.error("Error processing RAG chat", e);
            // Fallback to simple chat on error
            String answer = chatClient.prompt()
                    .user(message)
                    .call()
                    .content();
            return ChatResponse.builder()
                    .answer(answer)
                    .isFromContext(false)
                    .retrievalCount(0)
                    .sourceDocuments(List.of())
                    .build();
        }

    }

    /**
     * Build a DocumentSource from grouped SearchResults
     * Converts SearchResults from the same document into a DocumentSource with citations
     *
     * @param documentId the document ID (grouping key)
     * @param results list of SearchResults from the same document
     * @return DocumentSource with populated citations
     */
    private DocumentSource buildDocumentSource(String documentId, List<SearchResult> results) {
        if (results == null || results.isEmpty()) {
            return new DocumentSource();
        }

        // Use first result to get document metadata
        SearchResult firstResult = results.get(0);

        // Convert each SearchResult to a Citation
        List<Citation> citations = results.stream()
                .map(result -> Citation.builder()
                        .documentName(result.getDocumentName())
                        .documentId(result.getDocumentId())
                        .pageNumber(result.getPageNumber())
                        .chunkIndex(result.getChunkIndex())
                        .relevanceScore(result.getScore())
                        .content(result.getContent())
                        .chunkHash(result.getChunkHash())
                        .documentHash(result.getDocumentHash())
                        .embeddingModel(result.getEmbeddingModel())
                        .embeddingDimension(result.getEmbeddingDimension())
                        .language(result.getLanguage())
                        .version(result.getVersion())
                        .updatedAt(result.getUpdatedAt())
                        .build())
                .collect(Collectors.toList());

        // Build and return DocumentSource
        return DocumentSource.builder()
                .documentId(documentId)
                .documentName(firstResult.getDocumentName())
                .citations(citations)
                .chunkCount(results.size())
                .build();
    }

    /**
     * Start a new conversation
     * <p>
     * Creates a new conversation session for multi-turn chat.
     */
    @PostMapping("/converse/start")
    @Operation(
            summary = "Start New Conversation",
            description = "Initiate a new conversation session. Returns a conversation ID " +
                    "that can be used for subsequent messages in the conversation.",
            tags = {"Chat API"}
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Conversation created successfully",
                    content = @Content(mediaType = "application/json")
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Failed to create conversation"
            )
    })
    public Object startConversation() {
        log.info("Starting new conversation");
        return new java.util.HashMap<String, String>() {{
            put("conversationId", java.util.UUID.randomUUID().toString());
        }};
    }



    /**
     * Conversation endpoint that starts a new conversation.
     *
     */
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
                    ))
            ,
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
    @Parameter(
            name = "conversationId",
            description = "Unique identifier of the conversation to continue",
            required = true,
            example = "123e4567-e89b-12d3-a456-426614174000"
    )

    @Parameter(
            description = "Conversation request containing message and optional history depth"
    )

    @PostMapping("/converse")
    public ChatResponse converse(@RequestParam UUID conversationId,
                                 @RequestBody ConversationRequest request) {
        int historyDepth = request.getHistoryDepth() > 0 ? request.getHistoryDepth() : 5;
        return conversationService.chat(conversationId, request.getMessage(), historyDepth);
    }

    /**
     * Get all conversations
     */
    @GetMapping("/conversations")
    @Operation(
            summary = "Get All Conversations",
            description = "Retrieve list of all conversations with metadata",
            tags = {"Chat API"}
    )
    public List<java.util.Map<String, Object>> getAllConversations() {
        return conversationService.getAllConversations();
    }

    /**
     * Delete a conversation
     */
    @DeleteMapping("/conversations/{conversationId}")
    @Operation(
            summary = "Delete Conversation",
            description = "Delete a conversation and all its messages",
            tags = {"Chat API"}
    )
    public void deleteConversation(@PathVariable UUID conversationId) {
        conversationService.deleteConversation(conversationId);
    }

    /**
     * Search conversations
     */
    @GetMapping("/conversations/search")
    @Operation(
            summary = "Search Conversations",
            description = "Search conversations by title or message content",
            tags = {"Chat API"}
    )
    public List<java.util.Map<String, Object>> searchConversations(
            @RequestParam String query) {
        return conversationService.searchConversations(query);
    }

    /**
     * Streaming chat endpoint using Server-Sent Events (SSE)
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(
            summary = "Streaming Chat",
            description = "Send a query and receive the response as a stream of text chunks",
            tags = {"Chat API"}
    )
    public SseEmitter streamChat(
            @Parameter(description = "User query", required = true)
            @RequestParam String message) {
        
        SseEmitter emitter = new SseEmitter(30000L); // 30 second timeout
        
        // Run streaming in a separate thread
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                String answer = chatClient.prompt()
                        .user(message)
                        .call()
                        .content();
                
                // Send the complete answer as a single chunk for now
                // In a full implementation, you'd stream token by token
                emitter.send(SseEmitter.event().data(answer));
                emitter.complete();
            } catch (Exception e) {
                log.error("Error in streaming chat", e);
                emitter.completeWithError(e);
            }
        });
        
        return emitter;
    }
}

