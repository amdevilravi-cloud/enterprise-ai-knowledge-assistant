package com.enterprise.ai.knowledge.assistant.demo.ui.rest;

import com.enterprise.ai.knowledge.assistant.demo.document.dto.DocumentUploadResponse;
import com.enterprise.ai.knowledge.assistant.demo.document.service.DocumentUploadService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

@Slf4j
@Controller
@RequestMapping("/ui/documents/v2")
@RequiredArgsConstructor
@Tag(
    name = "Document UI API",
    description = "HTMX-integrated REST API for document management via web UI. " +
                 "Supports dual-mode responses: HTMX HTML fragments or JSON. " +
                 "Used by the document management interface at /ui/documents."
)
public class DocumentUIController {

    private final DocumentUploadService documentUploadService;

    @PostMapping("/upload")
    @Operation(
        summary = "Upload Document",
        description = "Upload a document file (PDF, TXT, DOCX) for indexing. " +
                     "Returns HTML card for HTMX or JSON for REST clients.",
        tags = {"Document UI API"}
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Document uploaded and indexed successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid file format or file too large"),
        @ApiResponse(responseCode = "500", description = "Error during file processing")
    })
    public Object uploadDocument(
        @Parameter(name = "file", description = "Document file to upload (PDF, TXT, or DOCX, max 50MB)", required = true)
        @RequestParam("file") MultipartFile file,
        HttpServletRequest request,
        Model model) {
        try {
            DocumentUploadResponse response = documentUploadService.save(file);

            if (isHtmxRequest(request)) {
                model.addAttribute("document", response);
                return "documents/document-item :: document";
            }

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error uploading document", e);
            if (isHtmxRequest(request)) {
                model.addAttribute("error", e.getMessage());
                return "documents/error :: error";
            }
            return ResponseEntity.status(400).body(
                Map.of("error", e.getMessage())
            );
        }
    }

    @GetMapping("")
    @Operation(
        summary = "List All Documents",
        description = "Retrieve all uploaded documents with metadata. Returns HTML list for HTMX or JSON array for REST clients.",
        tags = {"Document UI API"}
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Documents retrieved successfully"),
        @ApiResponse(responseCode = "500", description = "Error retrieving documents")
    })
    public Object listDocuments(HttpServletRequest request, Model model) {
        try {
            List<DocumentUploadResponse> documents = documentUploadService.listDocuments();

            if (isHtmxRequest(request)) {
                model.addAttribute("documents", documents);
              //  return "documents/list :: documents";
            }

            return ResponseEntity.ok(documents);
        } catch (Exception e) {
            log.error("Error listing documents", e);
            return ResponseEntity.status(500).body(
                Map.of("error", e.getMessage())
            );
        }
    }

    @DeleteMapping("/{id}")
    @Operation(
        summary = "Delete Document",
        description = "Delete a document and all its indexed chunks from the knowledge base.",
        tags = {"Document UI API"}
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Document deleted successfully"),
        @ApiResponse(responseCode = "404", description = "Document not found"),
        @ApiResponse(responseCode = "500", description = "Error deleting document")
    })
    public Object deleteDocument(
        @Parameter(name = "id", description = "Document ID to delete", required = true)
        @PathVariable String id,
        HttpServletRequest request) {
        try {
            documentUploadService.deleteDocument(id);

            if (isHtmxRequest(request)) {
                return ResponseEntity.ok().build();
            }

            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            log.error("Error deleting document", e);
            return ResponseEntity.status(500).body(
                Map.of("error", e.getMessage())
            );
        }
    }

    @PostMapping("/{id}/reindex")
    @Operation(
        summary = "Re-index Document",
        description = "Re-chunk and re-embed a document to update its vector representation.",
        tags = {"Document UI API"}
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Re-indexing started"),
        @ApiResponse(responseCode = "404", description = "Document not found"),
        @ApiResponse(responseCode = "500", description = "Error during re-indexing")
    })
    public Object reindexDocument(
        @Parameter(name = "id", description = "Document ID to re-index", required = true)
        @PathVariable String id,
        HttpServletRequest request,Model model) {
        try {
            documentUploadService.reindexDocument(id);

            if (isHtmxRequest(request)) {
                model.addAttribute("documentId", id);
                model.addAttribute("status", "reindexing");
                return "documents/status :: status";
            }

            return ResponseEntity.ok(Map.of("status", "reindexing"));
        } catch (Exception e) {
            log.error("Error reindexing document", e);
            return ResponseEntity.status(500).body(
                Map.of("error", e.getMessage())
            );
        }
    }

    @GetMapping("/{id}/metadata")
    @Operation(
        summary = "Get Document Metadata",
        description = "Retrieve detailed metadata for a document including chunk count and embeddings info.",
        tags = {"Document UI API"}
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Metadata retrieved successfully"),
        @ApiResponse(responseCode = "404", description = "Document not found"),
        @ApiResponse(responseCode = "500", description = "Error retrieving metadata")
    })
    public Object getDocumentMetadata(
        @Parameter(name = "id", description = "Document ID", required = true)
        @PathVariable String id,
        HttpServletRequest request,
        Model model) {
        try {
            Map<String, Object> metadata = documentUploadService.getDocumentMetadata(id);

            if (isHtmxRequest(request)) {
                model.addAttribute("metadata", metadata);
                return "documents/metadata :: metadata";
            }

            return ResponseEntity.ok(metadata);
        } catch (Exception e) {
            log.error("Error fetching document metadata", e);
            return ResponseEntity.status(500).body(
                Map.of("error", e.getMessage())
            );
        }
    }

    private boolean isHtmxRequest(HttpServletRequest request) {
        return "true".equalsIgnoreCase(request.getHeader("HX-Request"));
    }
}
