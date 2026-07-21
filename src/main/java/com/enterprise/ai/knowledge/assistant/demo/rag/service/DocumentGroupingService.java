package com.enterprise.ai.knowledge.assistant.demo.rag.service;

import com.enterprise.ai.knowledge.assistant.demo.chat.dto.ChatResponse;
import com.enterprise.ai.knowledge.assistant.demo.chat.dto.Citation;
import com.enterprise.ai.knowledge.assistant.demo.chat.dto.DocumentSource;
import com.enterprise.ai.knowledge.assistant.demo.repository.SearchResult;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class DocumentGroupingService {

    public List<DocumentSource> groupResultsByDocument(List<SearchResult> results) {
        if (results == null || results.isEmpty()) {
            return List.of();
        }

        Map<String, DocumentSource> docMap = new HashMap<>();

        for (SearchResult result : results) {
            String docName = result.getDocumentName();
            String docId = result.getDocumentId();

            DocumentSource docSource = docMap.computeIfAbsent(docName, key -> {
                DocumentSource ds = new DocumentSource();
                ds.setDocumentName(docName);
                ds.setDocumentId(docId);
                ds.setCitations(new ArrayList<>());
                ds.setChunkCount(0);
                return ds;
            });

            Citation citation =  Citation.builder()
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
                    .build();

            docSource.getCitations().add(citation);
            docSource.setChunkCount(docSource.getChunkCount() + 1);
        }

        return new ArrayList<>(docMap.values());
    }

    public String buildDocumentSummary(List<DocumentSource> sources) {
        if (sources == null || sources.isEmpty()) {
            return "";
        }

        StringBuilder summary = new StringBuilder();
        for (int i = 0; i < sources.size(); i++) {
            DocumentSource source = sources.get(i);
            summary.append(source.getDocumentName());
            if (i < sources.size() - 1) {
                summary.append(", ");
            }
        }
        return summary.toString();
    }
}

