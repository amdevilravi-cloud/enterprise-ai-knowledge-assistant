package com.enterprise.ai.knowledge.assistant.demo.chat;

import com.enterprise.ai.knowledge.assistant.demo.chat.dto.ChatResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * Chat REST API Controller with Swagger Documentation
 *
 * Provides endpoints for:
 * - Simple LLM chat (no RAG)
 * - RAG-enhanced chat with document context and citations
 * - Conversation management
 */
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
@Slf4j
@Tag(
    name = "Chat API",
    description = "Chat endpoints for simple LLM queries and RAG-enhanced context-aware responses"
)
public class ChatRestController {

    /**
     * Simple chat endpoint (No RAG)
     *
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
        // Implementation would call ChatClient directly
        return "Response from LLM";
    }

    /**
     * RAG-Enhanced Chat endpoint
     *
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
        @RequestParam(defaultValue = "5") Integer topK
    ) {
        log.info("RAG chat query: {} with topK: {}", message, topK);
        // Implementation would call Retriever -> PromptBuilder -> ChatClient
        return ChatResponse.builder()
                .answer("Response based on documents")
                .isFromContext(true)
                .retrievalCount(topK)
                .build();
    }

    /**
     * Start a new conversation
     *
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
     * Continue an existing conversation
     *
     * Adds a message to an existing conversation thread.
     */
    @PostMapping("/converse")
    @Operation(
        summary = "Continue Conversation",
        description = "Add a message to an existing conversation. " +
                     "Use the conversationId from /converse/start endpoint.",
        tags = {"Chat API"}
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Message processed and response returned",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ChatResponse.class)
            )
        ),
        @ApiResponse(
            responseCode = "400",
            description = "Invalid conversation ID or request body"
        ),
        @ApiResponse(
            responseCode = "404",
            description = "Conversation not found"
        )
    })
    public ChatResponse continueConversation(@RequestBody Object request) {
        log.info("Continuing conversation");
        return ChatResponse.builder()
                .answer("Conversation response")
                .isFromContext(true)
                .build();
    }
}

