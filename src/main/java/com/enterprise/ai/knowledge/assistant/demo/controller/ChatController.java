package com.enterprise.ai.knowledge.assistant.demo.controller;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST Controller for chat operations.
 *
 * Supports both OpenAI and local LM Studio LLMs based on configuration.
 * See ChatClientConfig for conditional bean initialization.
 */
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatClient chatClient;

    /**
     * Constructor-based dependency injection for ChatClient.
     *
     * The ChatClient bean is conditionally created by ChatClientConfig:
     * - Uses OpenAI when app.llm.provider=openai (default)
     * - Uses LM Studio when app.llm.provider=lmstudio
     */
    public ChatController(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    /**
     * Chat endpoint that sends a message to the configured LLM.
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

    /*
    // OLD CODE - Now uses conditional configuration instead
    // Previously, the ChatClient was built directly from ChatClient.Builder
    // public ChatController(ChatClient.Builder builder) {
    //     this.chatClient = builder.build();
    // }
    */
}