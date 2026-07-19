package com.enterprise.ai.knowledge.assistant.demo.ui;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.UUID;

@Controller
@RequestMapping("/ui")
public class UIController {

    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("conversationId", UUID.randomUUID().toString());
        model.addAttribute("pageTitle", "Chat");
        return "chat/index";
    }

    @GetMapping("/chat")
    public String chat(Model model) {
        model.addAttribute("conversationId", UUID.randomUUID().toString());
        model.addAttribute("pageTitle", "Chat");
        return "chat/index";
    }

    @GetMapping("/conversation/{id}")
    public String viewConversation(@PathVariable String id, Model model) {
        model.addAttribute("conversationId", id);
        model.addAttribute("pageTitle", "Conversation");
        return "chat/conversation";
    }

    @GetMapping("/documents")
    public String documents(Model model) {
        model.addAttribute("pageTitle", "Documents");
        return "documents/index";
    }

    @GetMapping("/conversations")
    public String conversations(Model model) {
        model.addAttribute("pageTitle", "Conversations");
        return "conversations/index";
    }

    @GetMapping("/analytics")
    public String analytics(Model model) {
        model.addAttribute("pageTitle", "Analytics");
        return "analytics/index";
    }

    @GetMapping("/settings")
    public String settings(Model model) {
        model.addAttribute("pageTitle", "Settings");
        return "settings/index";
    }
}

