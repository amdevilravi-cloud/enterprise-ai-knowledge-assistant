package com.enterprise.ai.knowledge.assistant.demo.document.service;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.charset.StandardCharsets;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import com.enterprise.ai.knowledge.assistant.demo.document.dto.DocumentUploadResponse;
import java.util.List;
import com.enterprise.ai.knowledge.assistant.demo.embedding.EmbeddingService;
import com.enterprise.ai.knowledge.assistant.demo.embedding.PostgresService;

/**
 * Service responsible for handling document persistence (local filesystem in this skeleton).
 */
@Service
public class DocumentUploadService {

	private final DocumentChunkService chunkService;
	private final EmbeddingService embeddingService;
	private final PostgresService postgresService;

	public DocumentUploadService(DocumentChunkService chunkService,
								 EmbeddingService embeddingService,
								 PostgresService postgresService) {
		this.chunkService = chunkService;
		this.embeddingService = embeddingService;
		this.postgresService = postgresService;
	}

	private static final Path DEFAULT_UPLOAD_DIR = Paths.get(System.getProperty("java.io.tmpdir"), "enterprise-ai-uploads");

	static {
		try {
			Files.createDirectories(DEFAULT_UPLOAD_DIR);
		} catch (IOException e) {
			// Intentionally silent for skeleton; in real app log properly.
		}
	}

	/**
	 * Save the uploaded MultipartFile to the default upload directory.
	 * Returns a map with file metadata on success.
	 */
	public DocumentUploadResponse save(MultipartFile file) throws IOException {
		if (file == null || file.isEmpty()) {
			return DocumentUploadResponse.builder()
					.documentName(file == null ? null : file.getOriginalFilename())
					.pages(0)
					.characters(0)
					.chunks(0)
					.text("")
					.chunkContents(java.util.Collections.emptyList())
					.isUploadSuccess(false)
					.build();
		}

		String originalFilename = file.getOriginalFilename();
		Path destination = DEFAULT_UPLOAD_DIR.resolve(System.currentTimeMillis() + "-" + (originalFilename == null ? "upload" : originalFilename));

		Files.copy(file.getInputStream(), destination, StandardCopyOption.REPLACE_EXISTING);
		// Attempt to extract text and page count for supported types
		String lower = originalFilename == null ? "" : originalFilename.toLowerCase();

		final int CHUNK_SIZE = 1000; // simple chunking heuristic

		if (lower.endsWith(".txt")) {
			String text = Files.readString(destination, StandardCharsets.UTF_8);
			int characters = text.length();
			int pages = 1; // plain text - treat as single 'page'
			List<String> chunkList = chunkService.chunkText(text, CHUNK_SIZE, DocumentChunkService.DEFAULT_OVERLAP);
			int chunks = chunkList.size();

			// generate embeddings for each chunk and insert into Postgres/vector DB
			for (String chunk : chunkList) {
				try {
					float[] embedding = embeddingService.generateEmbedding(chunk);
					postgresService.insertEmbedding(chunk, embedding);
				} catch (Exception ex) {
					// best-effort: continue on errors
				}
			}

			return DocumentUploadResponse.builder()
					.documentName(originalFilename)
					.pages(pages)
					.characters(characters)
					.chunks(chunks)
					.text("")
					.chunkContents(chunkList)
					.isUploadSuccess(true)
					.build();
		}

		if (lower.endsWith(".pdf")) {
			try (PDDocument pdf = PDDocument.load(destination.toFile())) {
				PDFTextStripper stripper = new PDFTextStripper();
				String text = stripper.getText(pdf);
				int pages = pdf.getNumberOfPages();
				int characters = text.length();
				List<String> chunkList = chunkService.chunkText(text, CHUNK_SIZE, DocumentChunkService.DEFAULT_OVERLAP);
				int chunks = chunkList.size();

				for (String chunk : chunkList) {
					try {
						float[] embedding = embeddingService.generateEmbedding(chunk);
						postgresService.insertEmbedding(chunk, embedding);
					} catch (Exception ex) {
						// continue on error
					}
				}

				return DocumentUploadResponse.builder()
						.documentName(originalFilename)
						.pages(pages)
						.characters(characters)
						.chunks(chunks)
						.text("")
						.chunkContents(chunkList)
						.isUploadSuccess(true)
						.build();
			}
		}

		// Fallback: try to read as UTF-8 text; if fails, return minimal metadata in the DTO
		try {
			String text = Files.readString(destination, StandardCharsets.UTF_8);
			int characters = text.length();
			List<String> chunkList = chunkService.chunkText(text, CHUNK_SIZE, DocumentChunkService.DEFAULT_OVERLAP);
			int chunks = chunkList.size();

			for (String chunk : chunkList) {
				try {
					float[] embedding = embeddingService.generateEmbedding(chunk);
					postgresService.insertEmbedding(chunk, embedding);
				} catch (Exception ex) {
					// continue on error
				}
			}

			return DocumentUploadResponse.builder()
					.documentName(originalFilename)
					.pages(1)
					.characters(characters)
					.chunks(chunks)
					.text("")
					.chunkContents(chunkList)
					.isUploadSuccess(true)
					.build();
		} catch (IOException ex) {
			// Non-text/binary file - return basic metadata in DTO (text empty)
			return DocumentUploadResponse.builder()
					.documentName(destination.getFileName().toString())
					.pages(0)
					.characters(0)
					.chunks(0)
					.text("")
					.chunkContents(java.util.Collections.emptyList())
					.isUploadSuccess(false)
					.build();
		}
	}

}
