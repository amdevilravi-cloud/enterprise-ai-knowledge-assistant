package com.enterprise.ai.knowledge.assistant.demo.ui.rest;

import com.enterprise.ai.knowledge.assistant.demo.chat.dto.ChatResponse;
import com.enterprise.ai.knowledge.assistant.demo.conversation.service.ConversationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import jakarta.servlet.http.HttpServletRequest;

import java.io.IOException;
import java.util.*;

@Slf4j
@Controller
@RequestMapping("/api/ui")
@RequiredArgsConstructor
@Tag(
    name = "Chat UI API",
    description = "HTMX-integrated REST API for web UI interactions. " +
                 "Supports dual-mode responses: HTMX HTML fragments (with HX-Request header) or JSON (REST clients). " +
                 "Used by the web interface at /ui/* routes."
)
public class ChatUIController {

    private final ConversationService conversationService;


    @PostMapping("/message")
    @Operation(
        summary = "Send Chat Message",
        description = "Post a message to a conversation. Returns HTML fragment for HTMX or JSON for REST clients. " +
                     "Include 'HX-Request: true' header for HTMX HTML response.",
        tags = {"Chat UI API"}
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Message processed successfully"),
        @ApiResponse(responseCode = "400", description = "Missing required parameters"),
        @ApiResponse(responseCode = "500", description = "Error processing message")
    })
    public Object sendMessage(
        @Parameter(name = "conversationId", description = "Unique conversation identifier", required = true)
        @RequestParam UUID conversationId,
        @Parameter(name = "message", description = "Message text to send", required = true)
        @RequestParam String message,
        HttpServletRequest request,
        Model model) {
        try {
            ChatResponse response = conversationService.chat(conversationId, message);

            // Check if HTMX request
            if (isHtmxRequest(request)) {
                model.addAttribute("response", response);
                model.addAttribute("conversationId", conversationId);
                return "chat/message-item :: message";
            }

            // Return JSON for REST API clients
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error processing message", e);
            if (isHtmxRequest(request)) {
                model.addAttribute("error", e.getMessage());
                return "chat/error :: error";
            }
            return ResponseEntity.status(500).body(
                    Map.of("error", e.getMessage())
            );
        }
    }

    @GetMapping("/messages")
    @Operation(
        summary = "Get Message History",
        description = "Retrieve all messages in a conversation. Returns HTML fragment for HTMX or JSON array for REST clients.",
        tags = {"Chat UI API"}
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Messages retrieved successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid or missing conversationId"),
        @ApiResponse(responseCode = "404", description = "Conversation not found")
    })
    public Object getMessages(
        @Parameter(name = "conversationId", description = "Conversation ID to retrieve messages from", required = true)
        @RequestParam UUID conversationId,
        HttpServletRequest request,
        Model model) {
        try {
            List<ChatResponse> messages = conversationService.getConversationHistory(conversationId);

            if (isHtmxRequest(request)) {
                model.addAttribute("messages", messages);
                return "chat/message-list :: messages";
            }

            return ResponseEntity.ok(messages);
        } catch (Exception e) {
            log.error("Error fetching messages", e);
            return ResponseEntity.status(500).body(
                    Map.of("error", e.getMessage())
            );
        }
    }

    @PostMapping("/converse/start")
    @Operation(
        summary = "Start New Conversation",
        description = "Create a new conversation session. Returns conversation ID for use in subsequent requests.",
        tags = {"Chat UI API"}
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Conversation created successfully"),
        @ApiResponse(responseCode = "500", description = "Error creating conversation")
    })
    public Object startConversation(HttpServletRequest request, Model model) {
        try {
            UUID conversationId = conversationService.startConversation();

            if (isHtmxRequest(request)) {
                model.addAttribute("conversationId", conversationId.toString());
                return "chat/conversation-started :: conversation";
            }

            return ResponseEntity.ok(Map.of("conversationId", conversationId));
        } catch (Exception e) {
            log.error("Error starting conversation", e);
            return ResponseEntity.status(500).body(
                    Map.of("error", e.getMessage())
            );
        }
    }

    @GetMapping("/conversations")
    @Operation(
        summary = "List All Conversations",
        description = "Retrieve all conversations with metadata. Returns HTML list for HTMX or JSON array for REST clients.",
        tags = {"Chat UI API"}
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Conversations retrieved successfully"),
        @ApiResponse(responseCode = "500", description = "Error retrieving conversations")
    })
    public Object getConversations(HttpServletRequest request, Model model) {
        try {
            List<Map<String, Object>> conversations = conversationService.getAllConversations();

            if (isHtmxRequest(request)) {
                model.addAttribute("conversations", conversations);
                return "conversations/list :: conversations";
            }

            return ResponseEntity.ok(conversations);
        } catch (Exception e) {
            log.error("Error fetching conversations", e);
            return ResponseEntity.status(500).body(
                    Map.of("error", e.getMessage())
            );
        }
    }

    @DeleteMapping("/conversation/{id}")
    @Operation(
        summary = "Delete Conversation",
        description = "Delete a conversation and all associated messages. This action is permanent.",
        tags = {"Chat UI API"}
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Conversation deleted successfully"),
        @ApiResponse(responseCode = "404", description = "Conversation not found"),
        @ApiResponse(responseCode = "500", description = "Error deleting conversation")
    })
    public Object deleteConversation(
        @Parameter(name = "id", description = "Conversation ID to delete", required = true)
        @PathVariable UUID id,
        HttpServletRequest request) {
        try {
            conversationService.deleteConversation(id);

            if (isHtmxRequest(request)) {
                return ResponseEntity.ok().build();
            }

            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            log.error("Error deleting conversation", e);
            return ResponseEntity.status(500).body(
                    Map.of("error", e.getMessage())
            );
        }
    }

    @GetMapping("/rag")
    @Operation(
        summary = "RAG Query (UI API)",
        description = "Send a RAG query from the UI. Retrieves documents and returns grounded response with citations.",
        tags = {"Chat UI API"}
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "RAG query processed successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid query parameters"),
        @ApiResponse(responseCode = "500", description = "Error during RAG processing")
    })
    public Object ragChat(
        @Parameter(name = "message", description = "Query message for document retrieval", required = true)
        @RequestParam String message,
        @Parameter(name = "topK", description = "Number of documents to retrieve. Default: 5")
        @RequestParam(defaultValue = "5") Integer topK,
        HttpServletRequest request) {
        try {
            ChatResponse response = conversationService.ragChat(message, topK);

            if (isHtmxRequest(request)) {
                return ResponseEntity.ok().build();
            }

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error in RAG chat", e);
            return ResponseEntity.status(500).body(
                    Map.of("error", e.getMessage())
            );
        }
    }

    @GetMapping("/stream")
    @Operation(
        summary = "Stream Chat Response (SSE)",
        description = "Stream chat response using Server-Sent Events (SSE). Allows real-time message streaming for better UX.",
        tags = {"Chat UI API"}
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "SSE stream established successfully"),
        @ApiResponse(responseCode = "400", description = "Missing required parameters"),
        @ApiResponse(responseCode = "500", description = "Error streaming response")
    })
    public SseEmitter streamMessage(
        @Parameter(name = "conversationId", description = "Conversation ID for streaming", required = true)
        @RequestParam UUID conversationId,
        @Parameter(name = "message", description = "Message to stream response for", required = true)
        @RequestParam String message) {
        SseEmitter emitter = new SseEmitter(60000L);
        String emitterId = conversationId + "-" + System.currentTimeMillis();

        new Thread(() -> {
            try {
                ChatResponse response = conversationService.chat(conversationId, message);
                emitter.send(SseEmitter.event()
                        .id(emitterId)
                        .name("message")
                        .data(response)
                        .build());
                emitter.complete();
            } catch (IOException e) {
                log.error("Error streaming message", e);
                emitter.completeWithError(e);
            }
        }).start();

        return emitter;
    }

    @GetMapping("/citation/{chunkHash}")
    @Operation(
        summary = "Get Citation Details",
        description = "Retrieve full details of a citation/document chunk by its hash. " +
                     "Used to display source document content in citation modal.",
        tags = {"Chat UI API"}
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Citation details retrieved successfully"),
        @ApiResponse(responseCode = "404", description = "Citation not found"),
        @ApiResponse(responseCode = "500", description = "Error retrieving citation details")
    })
    public Object getCitation(
        @Parameter(name = "chunkHash", description = "SHA-256 hash of the document chunk", required = true)
        @PathVariable String chunkHash,
        HttpServletRequest request,
        Model model) {
        try {
            Map<String, Object> citation = conversationService.getCitationDetails(chunkHash);

            if (isHtmxRequest(request)) {
                model.addAttribute("citation", citation);
                return "chat/citation-modal :: citation";
            }

            return ResponseEntity.ok(citation);
        } catch (Exception e) {
            log.error("Error fetching citation", e);
            return ResponseEntity.status(500).body(
                    Map.of("error", e.getMessage())
            );
        }
    }

    private boolean isHtmxRequest(HttpServletRequest request) {
        return "true".equalsIgnoreCase(request.getHeader("HX-Request"));
    }
}
