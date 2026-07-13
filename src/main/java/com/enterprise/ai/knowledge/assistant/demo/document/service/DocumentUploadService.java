package com.enterprise.ai.knowledge.assistant.demo.document.service;

import com.enterprise.ai.knowledge.assistant.demo.embedding.dto.EmbeddingResult;
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
import com.enterprise.ai.knowledge.assistant.demo.embedding.service.EmbeddingService;
import com.enterprise.ai.knowledge.assistant.demo.vector.entity.ChunkEntity;
import com.enterprise.ai.knowledge.assistant.demo.vector.service.VectorStoreService;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.UUID;

/**
 * Service responsible for handling document persistence (local filesystem in this skeleton).
 */
@Service
public class DocumentUploadService {

	private final DocumentChunkService chunkService;
	private final EmbeddingService embeddingService;
	private final VectorStoreService vectorStoreService;

	public DocumentUploadService(DocumentChunkService chunkService,
								 EmbeddingService embeddingService,
								 VectorStoreService vectorStoreService) {
		this.chunkService = chunkService;
		this.embeddingService = embeddingService;
		this.vectorStoreService = vectorStoreService;
	}

	private static String sha256Hex(String input) {
		try {
			MessageDigest md = MessageDigest.getInstance("SHA-256");
			byte[] digest = md.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
			StringBuilder sb = new StringBuilder();
			for (byte b : digest) {
				sb.append(String.format("%02x", b));
			}
			return sb.toString();
		} catch (NoSuchAlgorithmException e) {
			// SHA-256 always available in JVM; fallback to plain hashCode string
			return Integer.toHexString(input.hashCode());
		}
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

			// generate embeddings for each chunk and insert into vector store
			int idx = 0;
			for (String chunk : chunkList) {
				try {
					EmbeddingResult embedding = embeddingService.generateEmbedding(chunk);
					if (embedding == null || embedding.vector() == null) continue;
					String hash = sha256Hex(chunk);
					if (vectorStoreService.existsByHash(hash)) {
						idx++;
						continue; // skip duplicates
					}
					ChunkEntity entity = new ChunkEntity(UUID.randomUUID(), originalFilename, 1, idx, chunk, embedding.vector(), Instant.now(), hash);
					vectorStoreService.storeChunk(entity);
					idx++;
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

				int idx = 0;
				for (String chunk : chunkList) {
					try {
						EmbeddingResult embedding = embeddingService.generateEmbedding(chunk);
						if (embedding == null || embedding.vector() == null) continue;
						String hash = sha256Hex(chunk);
						if (vectorStoreService.existsByHash(hash)) { idx++; continue; }
						ChunkEntity entity = new ChunkEntity(UUID.randomUUID(), originalFilename, pages, idx, chunk, embedding.vector(), Instant.now(), hash);
						vectorStoreService.storeChunk(entity);
						idx++;
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

			int idx = 0;
			for (String chunk : chunkList) {
				try {
					EmbeddingResult embedding = embeddingService.generateEmbedding(chunk);
					if (embedding == null || embedding.vector() == null) continue;
					String hash = sha256Hex(chunk);
					if (vectorStoreService.existsByHash(hash)) { idx++; continue; }
					ChunkEntity entity = new ChunkEntity(UUID.randomUUID(), originalFilename, 1, idx, chunk, embedding.vector(), Instant.now(), hash);
					vectorStoreService.storeChunk(entity);
					idx++;
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
