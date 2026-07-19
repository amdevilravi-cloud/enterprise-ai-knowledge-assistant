//package com.enterprise.ai.knowledge.assistant.demo.document;
//
//import com.enterprise.ai.knowledge.assistant.demo.document.service.DocumentUploadService;
//import io.swagger.v3.oas.annotations.Operation;
//import io.swagger.v3.oas.annotations.Parameter;
//import io.swagger.v3.oas.annotations.media.Content;
//import io.swagger.v3.oas.annotations.media.Schema;
//import io.swagger.v3.oas.annotations.responses.ApiResponse;
//import io.swagger.v3.oas.annotations.responses.ApiResponses;
//import io.swagger.v3.oas.annotations.tags.Tag;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.web.bind.annotation.*;
//import org.springframework.web.multipart.MultipartFile;
//
///**
// * Document Management REST API Controller with Swagger Documentation
// *
// * Provides endpoints for:
// * - Uploading documents (PDF, TXT, DOCX)
// * - Listing uploaded documents
// * - Deleting documents
// * - Re-indexing documents
// * - Retrieving document metadata
// */
//@RestController
//@RequestMapping("/api/documents")
//@RequiredArgsConstructor
//@Slf4j
//@Tag(
//    name = "Document API",
//    description = "Endpoints for document management, upload, and indexing. " +
//                  "Documents are automatically chunked, embedded, and stored in the vector database."
//)
//public class DocumentRestController {
//
//    private final DocumentUploadService documentUploadService;
//
//    /**
//     * Upload a document
//     *
//     * Accepts PDF, TXT, or DOCX files. The document is:
//     * 1. Parsed and text extracted
//     * 2. Split into overlapping chunks
//     * 3. Embedded using the configured embedding model
//     * 4. Stored in PostgreSQL with pgvector
//     */
//    @PostMapping("/upload")
//    @Operation(
//        summary = "Upload Document",
//        description = "Upload a document to the knowledge base. " +
//                     "Supported formats: PDF (.pdf), Text (.txt), Word (.docx). " +
//                     "Maximum file size: 50MB. " +
//                     "The document is automatically chunked, embedded, and indexed.",
//        tags = {"Document API"}
//    )
//    @ApiResponses(value = {
//        @ApiResponse(
//            responseCode = "200",
//            description = "Document uploaded and indexed successfully",
//            content = @Content(
//                mediaType = "application/json",
//                schema = @Schema(type = "object",
//                    example = "{ \"documentId\": \"123\", \"fileName\": \"policy.pdf\", " +
//                             "\"fileSize\": 102400, \"uploadedAt\": \"2026-07-19T10:30:00Z\", " +
//                             "\"chunksCreated\": 15 }")
//            )
//        ),
//        @ApiResponse(
//            responseCode = "400",
//            description = "Invalid file format or file too large"
//        ),
//        @ApiResponse(
//            responseCode = "500",
//            description = "Error during file processing or embedding"
//        )
//    })
//    public Object uploadDocument(
//        @Parameter(
//            name = "file",
//            description = "Document file to upload (PDF, TXT, or DOCX)",
//            required = true
//        )
//        @RequestParam("file") MultipartFile file
//    ) {
//        log.info("Uploading document: {}", file.getOriginalFilename());
//        // Implementation calls DocumentUploadService.uploadDocument(file)
//        return new java.util.HashMap<String, Object>() {{
//            put("documentId", "doc-123");
//            put("fileName", file.getOriginalFilename());
//            put("fileSize", file.getSize());
//            put("chunksCreated", 15);
//        }};
//    }
//
//    /**
//     * List all documents
//     *
//     * Retrieves metadata for all uploaded documents.
//     */
//    @GetMapping
//    @Operation(
//        summary = "List Documents",
//        description = "Retrieve a list of all uploaded documents with metadata " +
//                     "(size, upload date, chunk count, etc.)",
//        tags = {"Document API"}
//    )
//    @ApiResponses(value = {
//        @ApiResponse(
//            responseCode = "200",
//            description = "List of documents retrieved",
//            content = @Content(
//                mediaType = "application/json",
//                schema = @Schema(type = "array")
//            )
//        ),
//        @ApiResponse(
//            responseCode = "500",
//            description = "Error retrieving documents"
//        )
//    })
//    public Object listDocuments() {
//        log.info("Listing all documents");
//        return new java.util.ArrayList<>();
//    }
//
//    /**
//     * Delete a document
//     *
//     * Removes the document and all associated chunks from the vector store.
//     */
//    @DeleteMapping("/{documentId}")
//    @Operation(
//        summary = "Delete Document",
//        description = "Delete a document and all its associated chunks from the knowledge base. " +
//                     "This action is permanent and will affect RAG query results.",
//        tags = {"Document API"}
//    )
//    @ApiResponses(value = {
//        @ApiResponse(
//            responseCode = "200",
//            description = "Document deleted successfully"
//        ),
//        @ApiResponse(
//            responseCode = "404",
//            description = "Document not found"
//        ),
//        @ApiResponse(
//            responseCode = "500",
//            description = "Error deleting document"
//        )
//    })
//    public Object deleteDocument(
//        @Parameter(
//            name = "documentId",
//            description = "ID of the document to delete",
//            required = true,
//            example = "doc-123"
//        )
//        @PathVariable String documentId
//    ) {
//        log.info("Deleting document: {}", documentId);
//        return new java.util.HashMap<String, Object>() {{
//            put("status", "deleted");
//            put("documentId", documentId);
//        }};
//    }
//
//    /**
//     * Re-index a document
//     *
//     * Re-chunks and re-embeds a document, updating the vector store.
//     * Useful if chunking strategy or embedding model changes.
//     */
//    @PostMapping("/{documentId}/reindex")
//    @Operation(
//        summary = "Re-index Document",
//        description = "Re-process a document by re-chunking and re-embedding it. " +
//                     "Useful after updating the chunking strategy or embedding model. " +
//                     "This will update all document chunks in the vector store.",
//        tags = {"Document API"}
//    )
//    @ApiResponses(value = {
//        @ApiResponse(
//            responseCode = "200",
//            description = "Re-indexing started",
//            content = @Content(
//                mediaType = "application/json",
//                schema = @Schema(type = "object",
//                    example = "{ \"status\": \"reindexing\", \"documentId\": \"doc-123\" }")
//            )
//        ),
//        @ApiResponse(
//            responseCode = "404",
//            description = "Document not found"
//        ),
//        @ApiResponse(
//            responseCode = "500",
//            description = "Error during re-indexing"
//        )
//    })
//    public Object reindexDocument(
//        @Parameter(
//            name = "documentId",
//            description = "ID of the document to re-index",
//            required = true,
//            example = "doc-123"
//        )
//        @PathVariable String documentId
//    ) {
//        log.info("Re-indexing document: {}", documentId);
//        return new java.util.HashMap<String, Object>() {{
//            put("status", "reindexing");
//            put("documentId", documentId);
//        }};
//    }
//
//    /**
//     * Get document metadata
//     *
//     * Retrieves detailed metadata for a specific document.
//     */
//    @GetMapping("/{documentId}/metadata")
//    @Operation(
//        summary = "Get Document Metadata",
//        description = "Retrieve detailed metadata for a specific document including " +
//                     "file size, upload date, chunk count, and embedding model version.",
//        tags = {"Document API"}
//    )
//    @ApiResponses(value = {
//        @ApiResponse(
//            responseCode = "200",
//            description = "Document metadata retrieved",
//            content = @Content(mediaType = "application/json")
//        ),
//        @ApiResponse(
//            responseCode = "404",
//            description = "Document not found"
//        )
//    })
//    public Object getDocumentMetadata(
//        @Parameter(
//            name = "documentId",
//            description = "ID of the document",
//            required = true,
//            example = "doc-123"
//        )
//        @PathVariable String documentId
//    ) {
//        log.info("Getting metadata for document: {}", documentId);
//        return new java.util.HashMap<String, Object>() {{
//            put("documentId", documentId);
//            put("fileName", "policy.pdf");
//            put("fileSize", 102400);
//            put("uploadedAt", "2026-07-19T10:30:00Z");
//            put("chunksCreated", 15);
//        }};
//    }
//}
//
