package com.enterprise.ai.knowledge.assistant.demo.conversation.dto;

public class ConversationRequest {
    private String message;
    private int historyDepth;

    public ConversationRequest() {}

    public ConversationRequest(String message, int historyDepth) {
        this.message = message;
        this.historyDepth = historyDepth;
    }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public int getHistoryDepth() { return historyDepth; }
    public void setHistoryDepth(int historyDepth) { this.historyDepth = historyDepth; }
}

