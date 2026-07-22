package com.enterprise.ai.knowledge.assistant.demo.document.controller;

import com.enterprise.ai.knowledge.assistant.demo.document.dto.DocumentMetadata;
import com.enterprise.ai.knowledge.assistant.demo.document.dto.DocumentUploadResponse;
import com.enterprise.ai.knowledge.assistant.demo.document.service.DocumentUploadService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Document Management REST API Controller with Swagger Documentation
 *
 * Provides endpoints for:
 * - Uploading documents (PDF, TXT, DOCX)
 * - Listing uploaded documents
 * - Deleting documents
 * - Re-indexing documents
 * - Retrieving document metadata
 */
@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
@Slf4j
@Tag(
    name = "Document API",
    description = "Endpoints for document management, upload, and indexing. " +
                  "Documents are automatically chunked, embedded, and stored in the vector database."
)
public class DocumentRestController {

    private final DocumentUploadService documentUploadService;

    /**
     * Upload a document
     *
     * Accepts PDF, TXT, or DOCX files. The document is:
     * 1. Parsed and text extracted
     * 2. Split into overlapping chunks
     * 3. Embedded using the configured embedding model
     * 4. Stored in PostgreSQL with pgvector
     */
    @PostMapping("/upload")
    @Operation(
        summary = "Upload Document",
        description = "Upload a document to the knowledge base. " +
                     "Supported formats: PDF (.pdf), Text (.txt), Word (.docx). " +
                     "Maximum file size: 50MB. " +
                     "The document is automatically chunked, embedded, and indexed.",
        tags = {"Document API"}
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Document uploaded and indexed successfully",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = DocumentUploadResponse.class)
            )
        ),
        @ApiResponse(
            responseCode = "400",
            description = "Invalid file format or file too large"
        ),
        @ApiResponse(
            responseCode = "500",
            description = "Error during file processing or embedding"
        )
    })
    public ResponseEntity<DocumentUploadResponse> uploadDocument(
        @Parameter(
            name = "file",
            description = "Document file to upload (PDF, TXT, or DOCX)",
            required = true
        )
        @RequestParam("file") MultipartFile file
    ) {
        if (file == null || file.isEmpty()) {
            DocumentUploadResponse response = new DocumentUploadResponse();
            response.setDocumentName(file == null ? null : file.getOriginalFilename());
            response.setPages(0);
            response.setCharacters(0);
            response.setChunks(0);
            response.setText("");
            response.setUploadSuccess(false);
            response.setChunkContents(Collections.emptyList());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }

        try {
            DocumentUploadResponse saved = documentUploadService.save(file);
            if (saved == null) {
                DocumentUploadResponse errResponse = new DocumentUploadResponse();
                errResponse.setDocumentName(file.getOriginalFilename());
                errResponse.setPages(0);
                errResponse.setCharacters(0);
                errResponse.setChunks(0);
                errResponse.setText("");
                errResponse.setUploadSuccess(false);
                errResponse.setChunkContents(Collections.emptyList());
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errResponse);
            }
            return ResponseEntity.ok(saved);
        } catch (IOException e) {
            DocumentUploadResponse errResponse = new DocumentUploadResponse();
            errResponse.setDocumentName(file.getOriginalFilename());
            errResponse.setPages(0);
            errResponse.setCharacters(0);
            errResponse.setChunks(0);
            errResponse.setText("Failed to save file: " + e.getMessage());
            errResponse.setUploadSuccess(false);
            errResponse.setChunkContents(Collections.emptyList());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errResponse);
        }
    }

    /**
     * List all documents
     *
     * Retrieves metadata for all uploaded documents.
     */
    @GetMapping
    @Operation(
        summary = "List Documents",
        description = "Retrieve a list of all uploaded documents with metadata " +
                     "(size, upload date, chunk count, etc.)",
        tags = {"Document API"}
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "List of documents retrieved",
            content = @Content(
                mediaType = "application/json",
                array = @ArraySchema(schema = @Schema(implementation = DocumentMetadata.class))
            )
        ),
        @ApiResponse(
            responseCode = "500",
            description = "Error retrieving documents"
        )
    })
    public ResponseEntity<List<DocumentMetadata>> listDocuments() {
        try {
            log.info("Listing all documents");
            List<DocumentMetadata> documents = documentUploadService.listDocuments();
            return ResponseEntity.ok(documents);
        } catch (Exception e) {
            log.error("Error listing documents", e);
            return ResponseEntity.status(500).build();
        }
    }

    /**
     * Delete a document
     *
     * Removes the document and all associated chunks from the vector store.
     */
    @DeleteMapping("/{documentId}")
    @Operation(
        summary = "Delete Document",
        description = "Delete a document and all its associated chunks from the knowledge base. " +
                     "This action is permanent and will affect RAG query results.",
        tags = {"Document API"}
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Document deleted successfully"
        ),
        @ApiResponse(
            responseCode = "404",
            description = "Document not found"
        ),
        @ApiResponse(
            responseCode = "500",
            description = "Error deleting document"
        )
    })
    public ResponseEntity<Map<String, Object>> deleteDocument(
        @Parameter(
            name = "documentId",
            description = "ID of the document to delete",
            required = true,
            example = "doc-123"
        )
        @PathVariable String documentId
    ) {
        try {
            log.info("Deleting document: {}", documentId);
            documentUploadService.deleteDocument(documentId);
            return ResponseEntity.ok(Map.of("status", "deleted", "documentId", documentId));
        } catch (Exception e) {
            log.error("Error deleting document", e);
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Re-index a document
     *
     * Re-chunks and re-embeds a document, updating the vector store.
     * Useful if chunking strategy or embedding model changes.
     */
    @PostMapping("/{documentId}/reindex")
    @Operation(
        summary = "Re-index Document",
        description = "Re-process a document by re-chunking and re-embedding it. " +
                     "Useful after updating the chunking strategy or embedding model. " +
                     "This will update all document chunks in the vector store.",
        tags = {"Document API"}
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Re-indexing started",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(type = "object")
            )
        ),
        @ApiResponse(
            responseCode = "404",
            description = "Document not found"
        ),
        @ApiResponse(
            responseCode = "500",
            description = "Error during re-indexing"
        )
    })
    public ResponseEntity<Map<String, Object>> reindexDocument(
        @Parameter(
            name = "documentId",
            description = "ID of the document to re-index",
            required = true,
            example = "doc-123"
        )
        @PathVariable String documentId
    ) {
        try {
            log.info("Re-indexing document: {}", documentId);
            documentUploadService.reindexDocument(documentId);
            return ResponseEntity.ok(Map.of("status", "reindexing", "documentId", documentId));
        } catch (Exception e) {
            log.error("Error reindexing document", e);
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Get document metadata
     *
     * Retrieves detailed metadata for a specific document.
     */
    @GetMapping("/{documentId}/metadata")
    @Operation(
        summary = "Get Document Metadata",
        description = "Retrieve detailed metadata for a specific document including " +
                     "file size, upload date, chunk count, and embedding model version.",
        tags = {"Document API"}
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Document metadata retrieved",
            content = @Content(mediaType = "application/json")
        ),
        @ApiResponse(
            responseCode = "404",
            description = "Document not found"
        )
    })
    public ResponseEntity<Map<String, Object>> getDocumentMetadata(
        @Parameter(
            name = "documentId",
            description = "ID of the document",
            required = true,
            example = "doc-123"
        )
        @PathVariable String documentId
    ) {
        try {
            log.info("Getting metadata for document: {}", documentId);
            Map<String, Object> metadata = documentUploadService.getDocumentMetadata(documentId);
            return ResponseEntity.ok(metadata);
        } catch (Exception e) {
            log.error("Error fetching document metadata", e);
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }
}
