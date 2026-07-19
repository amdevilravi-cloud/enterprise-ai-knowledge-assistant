package com.enterprise.ai.knowledge.assistant.demo.ui.rest;

import com.enterprise.ai.knowledge.assistant.demo.chat.dto.ChatResponse;
import com.enterprise.ai.knowledge.assistant.demo.conversation.service.ConversationService;
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
public class ChatUIController {

    private final ConversationService conversationService;


    @PostMapping("/message")
    public Object sendMessage(
            @RequestParam UUID conversationId,
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
    public Object getMessages(
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
    public Object deleteConversation(
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
    public Object ragChat(
            @RequestParam String message,
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
    public SseEmitter streamMessage(@RequestParam UUID conversationId, @RequestParam String message) {
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
    public Object getCitation(
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



