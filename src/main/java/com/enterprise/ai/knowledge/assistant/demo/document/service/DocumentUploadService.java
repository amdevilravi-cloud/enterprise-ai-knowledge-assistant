package com.enterprise.ai.knowledge.assistant.demo.document.service;

import com.enterprise.ai.knowledge.assistant.demo.document.dto.DocumentUploadResponse;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Collections;

/**
 * Service responsible for saving uploaded files and delegating ingestion.
 */
@Service
public class DocumentUploadService {

	private final DocumentIngestionOrchestrator ingestionOrchestrator;

	public DocumentUploadService(DocumentIngestionOrchestrator ingestionOrchestrator) {
		this.ingestionOrchestrator = ingestionOrchestrator;
	}

	private static final Path DEFAULT_UPLOAD_DIR = Paths.get(System.getProperty("java.io.tmpdir"), "enterprise-ai-uploads");

	static {
		try {
			Files.createDirectories(DEFAULT_UPLOAD_DIR);
		} catch (IOException e) {
			// Intentionally silent for skeleton
		}
	}

	/**
	 * Save the uploaded MultipartFile and ingest it.
	 */
	public DocumentUploadResponse save(MultipartFile file) throws IOException {
		if (file == null || file.isEmpty()) {
			DocumentUploadResponse response = new DocumentUploadResponse();
			response.setDocumentName(file == null ? null : file.getOriginalFilename());
			response.setPages(0);
			response.setCharacters(0);
			response.setChunks(0);
			response.setText("");
			response.setUploadSuccess(false);
			response.setChunkContents(Collections.emptyList());
			return response;
		}

		String originalFilename = file.getOriginalFilename();
		Path destination = DEFAULT_UPLOAD_DIR.resolve(System.currentTimeMillis() + "-" + (originalFilename == null ? "upload" : originalFilename));
		Files.copy(file.getInputStream(), destination, StandardCopyOption.REPLACE_EXISTING);

		try {
			return ingestionOrchestrator.ingest(destination, originalFilename == null ? destination.getFileName().toString() : originalFilename);
		} catch (IllegalArgumentException ex) {
			// Unsupported type - return minimal response
			DocumentUploadResponse response = new DocumentUploadResponse();
			response.setDocumentName(destination.getFileName().toString());
			response.setPages(0);
			response.setCharacters(0);
			response.setChunks(0);
			response.setText("");
			response.setUploadSuccess(false);
			response.setChunkContents(Collections.emptyList());
			return response;
		}
	}
}
