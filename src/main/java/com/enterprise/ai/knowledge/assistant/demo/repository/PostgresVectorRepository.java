package com.enterprise.ai.knowledge.assistant.demo.repository;

import com.enterprise.ai.knowledge.assistant.demo.vector.entity.ChunkEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class PostgresVectorRepository implements VectorRepository {

    private final JdbcTemplate jdbcTemplate;

    public PostgresVectorRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void ensureTable() {
        try {
            jdbcTemplate.execute("CREATE EXTENSION IF NOT EXISTS vector");
        } catch (Exception ignored) {}

        String sql = "CREATE TABLE IF NOT EXISTS embeddings (" +
                "id UUID PRIMARY KEY, " +
                "document_name TEXT, " +
                "page_number INT, " +
                "chunk_index INT, " +
                "content TEXT, " +
                "embedding vector, " +
                "hash TEXT, " +
                "created_at TIMESTAMP" +
                ")";
        jdbcTemplate.execute(sql);
    }

    @Override
    public void insertChunk(ChunkEntity chunk) {
        UUID id = chunk.getId() == null ? UUID.randomUUID() : chunk.getId();
        Timestamp now = chunk.getCreatedAt() == null ? Timestamp.from(Instant.now()) : Timestamp.from(chunk.getCreatedAt());

        String sql = "INSERT INTO embeddings (id, document_name, page_number, chunk_index, content, embedding, hash, created_at) VALUES (?, ?, ?, ?, ?, ?::vector, ?, ?)";

        PreparedStatementCreator psc = con -> {
            PreparedStatement ps = con.prepareStatement(sql);
            ps.setObject(1, id);
            ps.setString(2, chunk.getDocumentName());
            if (chunk.getPageNumber() == null) ps.setNull(3, java.sql.Types.INTEGER); else ps.setInt(3, chunk.getPageNumber());
            if (chunk.getChunkIndex() == null) ps.setNull(4, java.sql.Types.INTEGER); else ps.setInt(4, chunk.getChunkIndex());
            ps.setString(5, chunk.getContent());
            ps.setString(6, toPgVectorString(chunk.getEmbedding()));
            ps.setString(7, chunk.getHash());
            ps.setTimestamp(8, now);
            return ps;
        };

        jdbcTemplate.update(psc);
    }

    @Override
    public List<SearchResult> findNearest(float[] query, int k) {
        String sql = "SELECT content, page_number, document_name, chunk_index, embedding <-> (?::vector) AS distance FROM embeddings ORDER BY distance ASC LIMIT ?";

        PreparedStatementCreator psc = con -> {
            PreparedStatement ps = con.prepareStatement(sql);
            ps.setString(1, toPgVectorString(query));
            ps.setInt(2, k);
            return ps;
        };

        RowMapper<SearchResult> mapper = (rs, rowNum) -> {
            String content = rs.getString("content");
            Integer pageNumber = rs.getObject("page_number") == null ? null : rs.getInt("page_number");
            String documentName = rs.getString("document_name");
            Integer chunkIndex = rs.getObject("chunk_index") == null ? null : rs.getInt("chunk_index");
            double distance = rs.getDouble("distance");
            return new SearchResult(content, distance, pageNumber, documentName, chunkIndex);
        };

        try {
            return jdbcTemplate.query(psc, mapper);
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private String toPgVectorString(float[] embedding) {
        if (embedding == null) return "[]";
        StringBuilder sb = new StringBuilder();
        sb.append('[');
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(Float.toString(embedding[i]));
        }
        sb.append(']');
        return sb.toString();
    }

    @Override
    public boolean existsByHash(String hash) {
        try {
            Integer count = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM embeddings WHERE hash = ?", Integer.class, hash);
            return count != null && count > 0;
        } catch (Exception e) {
            return false;
        }
    }
}

