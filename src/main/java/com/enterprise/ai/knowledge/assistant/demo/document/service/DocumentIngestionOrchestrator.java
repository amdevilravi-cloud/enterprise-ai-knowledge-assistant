package com.enterprise.ai.knowledge.assistant.demo.document.service;

import com.enterprise.ai.knowledge.assistant.demo.document.dto.DocumentMetadata;
import com.enterprise.ai.knowledge.assistant.demo.document.dto.DocumentUploadResponse;
import com.enterprise.ai.knowledge.assistant.demo.document.dto.ParsedDocument;
import com.enterprise.ai.knowledge.assistant.demo.document.dto.PdfChunk;
import com.enterprise.ai.knowledge.assistant.demo.document.parser.DocumentParser;
import com.enterprise.ai.knowledge.assistant.demo.document.parser.DocumentParserRegistry;
import com.enterprise.ai.knowledge.assistant.demo.embedding.dto.EmbeddingResult;
import com.enterprise.ai.knowledge.assistant.demo.embedding.service.EmbeddingService;
import com.enterprise.ai.knowledge.assistant.demo.vector.entity.ChunkEntity;
import com.enterprise.ai.knowledge.assistant.demo.vector.service.VectorStoreService;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Service
public class DocumentIngestionOrchestrator {

    private static final int CHUNK_SIZE = 1000;

    private final DocumentParserRegistry parserRegistry;
    private final DocumentChunkService chunkService;
    private final MetadataExtractor metadataExtractor;
    private final EmbeddingService embeddingService;
    private final VectorStoreService vectorStoreService;

    public DocumentIngestionOrchestrator(DocumentParserRegistry parserRegistry,
                                         DocumentChunkService chunkService,
                                         MetadataExtractor metadataExtractor,
                                         EmbeddingService embeddingService,
                                         VectorStoreService vectorStoreService) {
        this.parserRegistry = parserRegistry;
        this.chunkService = chunkService;
        this.metadataExtractor = metadataExtractor;
        this.embeddingService = embeddingService;
        this.vectorStoreService = vectorStoreService;
    }

    public DocumentUploadResponse ingest(Path filePath, String documentName) throws IOException {
        DocumentParser parser = parserRegistry.resolve(documentName);
        ParsedDocument parsed = parser.parse(filePath);
        DocumentMetadata metadata = metadataExtractor.extract(documentName, parsed);

        // Generate document ID and hash (Phase 4: Enhanced Metadata)
        String documentId = UUID.randomUUID().toString();
        String documentHash = sha256Hex(parsed.text());

        if (parsed.pageAware()) {
            return ingestPdf(filePath, metadata, documentId, documentHash);
        }
        return ingestText(filePath, metadata, documentId, documentHash, parsed);
    }

    private DocumentUploadResponse ingestText(Path filePath, DocumentMetadata metadata,
                                             String documentId, String documentHash, ParsedDocument parsed) throws IOException {
        String text = parsed.text();
        if (text == null) {
            text = Files.readString(filePath, StandardCharsets.UTF_8);
        }

        List<String> chunkList = chunkService.chunkText(text, CHUNK_SIZE, DocumentChunkService.DEFAULT_OVERLAP);
        persistTextChunks(metadata, documentId, documentHash, chunkList);

        DocumentUploadResponse response = new DocumentUploadResponse();
        response.setDocumentName(metadata.documentName());
        response.setPages(metadata.pages());
        response.setCharacters(metadata.characters());
        response.setChunks(chunkList.size());
        response.setText("");
        response.setUploadSuccess(true);
        response.setChunkContents(chunkList);
        return response;
    }

    private DocumentUploadResponse ingestPdf(Path filePath, DocumentMetadata metadata,
                                            String documentId, String documentHash) throws IOException {
        try (PDDocument pdf = PDDocument.load(filePath.toFile())) {
            List<PdfChunk> chunkList = chunkService.chunkPDFText(pdf, CHUNK_SIZE, DocumentChunkService.DEFAULT_OVERLAP);
            persistPdfChunks(metadata, documentId, documentHash, chunkList);

            DocumentUploadResponse response = new DocumentUploadResponse();
            response.setDocumentName(metadata.documentName());
            response.setPages(metadata.pages());
            response.setCharacters(metadata.characters());
            response.setFileSize(metadata.fileSize());
            response.setChunks(chunkList.size());
            response.setText("");
            response.setUploadSuccess(true);
            response.setChunkContents(Collections.emptyList());
            return response;
        }
    }

    private void persistTextChunks(DocumentMetadata metadata, String documentId, String documentHash, List<String> chunkList) {
        int idx = 0;
        for (String chunk : chunkList) {
            try {
                EmbeddingResult embedding = embeddingService.generateEmbedding(chunk);
                if (embedding == null || embedding.vector() == null) {
                    idx++;
                    continue;
                }
                String chunkHash = sha256Hex(chunk);
                if (vectorStoreService.existsByHash(chunkHash)) {
                    idx++;
                    continue;
                }

                String language = detectLanguage(chunk);
                Instant now = Instant.now();

                ChunkEntity entity = new ChunkEntity(
                        UUID.randomUUID(),
                        metadata.documentName(),
                        documentId,
                        documentHash,
                        chunkHash,
                        1,
                        idx,
                        chunk,
                        embedding.vector(),
                        embedding.model(),
                        embedding.dimensions(),
                        language,
                        1,
                        now,
                        now,
                        chunkHash
                );
                vectorStoreService.storeChunk(entity);
            } catch (Exception ignored) {
            }
            idx++;
        }
    }

    private void persistPdfChunks(DocumentMetadata metadata, String documentId, String documentHash, List<PdfChunk> chunkList) {
        for (PdfChunk pdfChunk : chunkList) {
            try {
                EmbeddingResult embedding = embeddingService.generateEmbedding(pdfChunk.text());
                if (embedding == null || embedding.vector() == null) {
                    continue;
                }
                String chunkHash = sha256Hex(pdfChunk.text());
                if (vectorStoreService.existsByHash(chunkHash)) {
                    continue;
                }

                String language = detectLanguage(pdfChunk.text());
                Instant now = Instant.now();

                ChunkEntity entity = new ChunkEntity(
                        UUID.randomUUID(),
                        metadata.documentName(),
                        documentId,
                        documentHash,
                        chunkHash,
                        pdfChunk.pageNumber(),
                        pdfChunk.chunkIndex(),
                        pdfChunk.text(),
                        embedding.vector(),
                        embedding.model(),
                        embedding.dimensions(),
                        language,
                        1,
                        now,
                        now,
                        chunkHash
                );
                vectorStoreService.storeChunk(entity);
            } catch (Exception ignored) {
            }
        }
    }

    private String detectLanguage(String text) {
        if (text == null || text.isBlank()) {
            return "unknown";
        }
        return text.chars().anyMatch(ch -> ch > 127) ? "unknown" : "en";
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            return Integer.toHexString(input.hashCode());
        }
    }
}
