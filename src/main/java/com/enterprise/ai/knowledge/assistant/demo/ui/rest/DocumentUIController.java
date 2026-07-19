package com.enterprise.ai.knowledge.assistant.demo.ui.rest;

import com.enterprise.ai.knowledge.assistant.demo.document.dto.DocumentUploadResponse;
import com.enterprise.ai.knowledge.assistant.demo.document.service.DocumentUploadService;
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
public class DocumentUIController {

    private final DocumentUploadService documentUploadService;

    @PostMapping("/upload")
    public Object uploadDocument(
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
    public Object deleteDocument(
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
    public Object reindexDocument(
            @PathVariable String id,
            HttpServletRequest request,
            Model model) {
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
    public Object getDocumentMetadata(
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

