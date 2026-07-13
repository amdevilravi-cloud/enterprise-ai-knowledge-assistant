package com.enterprise.ai.knowledge.assistant.demo.vector.service;

import com.enterprise.ai.knowledge.assistant.demo.vector.entity.ChunkEntity;
import com.enterprise.ai.knowledge.assistant.demo.repository.VectorRepository;
import com.enterprise.ai.knowledge.assistant.demo.repository.SearchResult;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PostgresVectorStoreService implements VectorStoreService {

    private final VectorRepository repository;

    public PostgresVectorStoreService(VectorRepository repository) {
        this.repository = repository;
        this.repository.ensureTable();
    }

    @Override
    public void storeChunk(ChunkEntity chunk) {
        repository.insertChunk(chunk);
    }

    @Override
    public List<SearchResult> findNearest(float[] query, int k) {
        return repository.findNearest(query, k);
    }

    @Override
    public boolean existsByHash(String hash) {
        return repository.existsByHash(hash);
    }
}

