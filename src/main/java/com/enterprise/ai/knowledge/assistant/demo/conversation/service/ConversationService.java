package com.enterprise.ai.knowledge.assistant.demo.conversation.service;

import com.enterprise.ai.knowledge.assistant.demo.chat.dto.ChatResponse;
import com.enterprise.ai.knowledge.assistant.demo.conversation.repository.ConversationRepository;
import com.enterprise.ai.knowledge.assistant.demo.rag.PromptBuilder;
import com.enterprise.ai.knowledge.assistant.demo.rag.Retriever;
import com.enterprise.ai.knowledge.assistant.demo.rag.dto.RagPrompt;
import com.enterprise.ai.knowledge.assistant.demo.rag.retriever.HybridRetriever;
import com.enterprise.ai.knowledge.assistant.demo.rag.service.DocumentGroupingService;
import com.enterprise.ai.knowledge.assistant.demo.repository.SearchResult;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ConversationService {

    private final ConversationRepository conversationRepository;
    private final MemoryManager memoryManager;
    private final Retriever retriever;
    private final HybridRetriever hybridRetriever;
    private final PromptBuilder promptBuilder;
    private final DocumentGroupingService documentGroupingService;
    private final ChatClient chatClient;

    @Value("${app.rag.enableMultiDocumentMode:false}")
    private boolean enableMultiDocumentMode;

    public ConversationService(ConversationRepository conversationRepository,
                                MemoryManager memoryManager,
                                Retriever retriever,
                                HybridRetriever hybridRetriever,
                                PromptBuilder promptBuilder,
                                DocumentGroupingService documentGroupingService,
                                ChatClient chatClient) {
        this.conversationRepository = conversationRepository;
        this.memoryManager = memoryManager;
        this.retriever = retriever;
        this.hybridRetriever = hybridRetriever;
        this.promptBuilder = promptBuilder;
        this.documentGroupingService = documentGroupingService;
        this.chatClient = chatClient;
    }

    public UUID createConversation() {
        return conversationRepository.createConversation("New Conversation");
    }


    public ChatResponse chat(UUID conversationId, String userMessage, int historyDepth) {
        try {
            int messageOrder = memoryManager.getMessageCount(conversationId);
            memoryManager.saveUserMessage(conversationId, userMessage, messageOrder);

            String history = memoryManager.getFormattedHistory(conversationId, historyDepth);

            List<SearchResult> results;
            if (hybridRetriever.isEnabled()) {
                results = hybridRetriever.retrieve(userMessage, 3, history);
            } else {
                results = retriever.retrieveAndRerank(userMessage, 20, 3);
            }

            RagPrompt ragPrompt;
            if (enableMultiDocumentMode) {
                ragPrompt = promptBuilder.buildMultiDocPrompt(userMessage, results, history);
            } else {
                ragPrompt = promptBuilder.buildRagPromptWithHistory(userMessage, results, history);
            }

            String answer = chatClient.prompt()
                    .system(ragPrompt.systemPrompt())
                    .user(ragPrompt.userPrompt())
                    .call()
                    .content();

            memoryManager.saveAssistantMessage(conversationId, answer, messageOrder + 1);

            List<ChatResponse.Citation> citations = results.stream()
                    .map(r -> new ChatResponse.Citation(
                            r.getDocumentName(),
                            r.getPageNumber(),
                            r.getChunkIndex(),
                            r.getScore(),
                            r.getContent()
                    ))
                    .collect(Collectors.toList());

            List<ChatResponse.DocumentSource> sourceDocuments = 
                    documentGroupingService.groupResultsByDocument(results);

            Map<String, Object> metadata = new HashMap<>(ragPrompt.metadata());
            if (enableMultiDocumentMode) {
                metadata.put("documentCount", sourceDocuments.size());
                metadata.put("multiDocMode", true);
            }

            return new ChatResponse(answer, citations, !results.isEmpty(), results.size(), 
                                   sourceDocuments, metadata);
        } catch (Exception e) {
            String fallbackAnswer = chatClient.prompt()
                    .user(userMessage)
                    .call()
                    .content();
            return new ChatResponse(fallbackAnswer, List.of(), false, 0);
        }
    }

    public ChatResponse chat(UUID conversationId, String userMessage) {
        return chat(conversationId, userMessage, 5);
    }

    public UUID startConversation() {
        return createConversation();
    }

    public List<ChatResponse> getConversationHistory(UUID conversationId) {
        return conversationRepository.getConversationHistory(conversationId);
    }

    public List<Map<String, Object>> getAllConversations() {
        return conversationRepository.getAllConversations();
    }

    public void deleteConversation(UUID conversationId) {
        conversationRepository.deleteConversation(conversationId);
    }

    public ChatResponse ragChat(String message, Integer topK) {
        try {
            List<SearchResult> results = retriever.retrieveAndRerank(message, topK, 3);

            RagPrompt ragPrompt = promptBuilder.buildRagPrompt(message, results);

            String answer = chatClient.prompt()
                    .system(ragPrompt.systemPrompt())
                    .user(ragPrompt.userPrompt())
                    .call()
                    .content();

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

            String fallbackAnswer = chatClient.prompt()
                    .user(message)
                    .call()
                    .content();
            return new ChatResponse(fallbackAnswer, List.of(), false, 0);
        }
    }

    public Map<String, Object> getCitationDetails(String chunkHash) {
        return conversationRepository.getCitationDetails(chunkHash);
    }
}
