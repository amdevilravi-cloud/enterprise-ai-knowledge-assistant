package com.enterprise.ai.knowledge.assistant.demo.conversation.service;

import com.enterprise.ai.knowledge.assistant.demo.chat.dto.ChatResponse;
import com.enterprise.ai.knowledge.assistant.demo.chat.dto.Citation;
import com.enterprise.ai.knowledge.assistant.demo.chat.dto.DocumentSource;
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

            List<DocumentSource> sourceDocuments =
                    documentGroupingService.groupResultsByDocument(results);

            Map<String, Object> metadata = new HashMap<>(ragPrompt.metadata());
            if (enableMultiDocumentMode) {
                metadata.put("documentCount", sourceDocuments.size());
                metadata.put("multiDocMode", true);
            }

            return new ChatResponse(answer, !results.isEmpty(), results.size(),
                                   sourceDocuments);
        } catch (Exception e) {
            String fallbackAnswer = chatClient.prompt()
                    .user(userMessage)
                    .call()
                    .content();
            return new ChatResponse(fallbackAnswer, false, 0, List.of());
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

            // Step 4: Extract sourceDocuments and citations from results
            // Group SearchResults by documentId to build DocumentSource objects
            List<DocumentSource> sourceDocuments = results.stream()
                    .collect(Collectors.groupingBy(SearchResult::getDocumentId))
                    .entrySet()
                    .stream()
                    .map(entry -> buildDocumentSource(entry.getKey(), entry.getValue()))
                    .collect(Collectors.toList());


            return new ChatResponse(answer, !results.isEmpty(), results.size(), sourceDocuments);
        } catch (Exception e) {

            String fallbackAnswer = chatClient.prompt()
                    .user(message)
                    .call()
                    .content();
            return new ChatResponse(fallbackAnswer, false, 0, List.of());
        }
    }

    public Map<String, Object> getCitationDetails(String chunkHash) {
        return conversationRepository.getCitationDetails(chunkHash);
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
}
