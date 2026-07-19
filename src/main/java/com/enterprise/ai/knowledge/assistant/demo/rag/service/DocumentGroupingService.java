package com.enterprise.ai.knowledge.assistant.demo.rag.service;

import com.enterprise.ai.knowledge.assistant.demo.chat.dto.ChatResponse;
import com.enterprise.ai.knowledge.assistant.demo.repository.SearchResult;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class DocumentGroupingService {

    public List<ChatResponse.DocumentSource> groupResultsByDocument(List<SearchResult> results) {
        if (results == null || results.isEmpty()) {
            return List.of();
        }

        Map<String, ChatResponse.DocumentSource> docMap = new HashMap<>();

        for (SearchResult result : results) {
            String docName = result.getDocumentName();
            String docId = result.getDocumentId();

            ChatResponse.DocumentSource docSource = docMap.computeIfAbsent(docName, key -> {
                ChatResponse.DocumentSource ds = new ChatResponse.DocumentSource();
                ds.setDocumentName(docName);
                ds.setDocumentId(docId);
                ds.setCitations(new ArrayList<>());
                ds.setChunkCount(0);
                return ds;
            });

            ChatResponse.Citation citation = new ChatResponse.Citation(
                    result.getDocumentName(),
                    result.getPageNumber(),
                    result.getChunkIndex(),
                    result.getScore(),
                    result.getContent()
            );

            docSource.getCitations().add(citation);
            docSource.setChunkCount(docSource.getChunkCount() + 1);
        }

        return new ArrayList<>(docMap.values());
    }

    public String buildDocumentSummary(List<ChatResponse.DocumentSource> sources) {
        if (sources == null || sources.isEmpty()) {
            return "";
        }

        StringBuilder summary = new StringBuilder();
        for (int i = 0; i < sources.size(); i++) {
            ChatResponse.DocumentSource source = sources.get(i);
            summary.append(source.getDocumentName());
            if (i < sources.size() - 1) {
                summary.append(", ");
            }
        }
        return summary.toString();
    }
}

