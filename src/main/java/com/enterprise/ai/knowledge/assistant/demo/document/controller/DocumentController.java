package com.enterprise.ai.knowledge.assistant.demo.document.controller;

import com.enterprise.ai.knowledge.assistant.demo.document.dto.DocumentUploadResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

import com.enterprise.ai.knowledge.assistant.demo.document.service.DocumentUploadService;

@RestController
@RequestMapping("/documents")
public class DocumentController {

	private final DocumentUploadService documentUploadService;

	public DocumentController(DocumentUploadService documentUploadService) {
		this.documentUploadService = documentUploadService;
	}

	/**
	 * Upload a document as multipart file.
	 *
	 * POST /documents/upload
	 * Content-Type: multipart/form-data
	 * Form field name: file
	 */
	@PostMapping(path = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<DocumentUploadResponse> uploadDocument(@RequestParam("file") MultipartFile file) {
							DocumentUploadResponse response = new DocumentUploadResponse();
							response.setDocumentName(file == null ? null : file.getOriginalFilename());

							if (file == null || file.isEmpty()) {
								return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
							}

							try {
								DocumentUploadResponse saved = documentUploadService.save(file);
								if (saved == null) {
									// unexpected null - treat as server error
									response.setText("");
									response.setPages(0);
									response.setCharacters(0);
									response.setChunks(0);
									return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
								}
								return ResponseEntity.ok(saved);
							} catch (IOException e) {
								// include error message in the text field of the DTO so caller has context
								response.setText("Failed to save file: " + e.getMessage());
								response.setPages(0);
								response.setCharacters(0);
								response.setChunks(0);
								return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
							}
	}

}
