TODO — Make vector store pluggable, add metadata & DTOs

Summary
-------
Applied reviewer recommendations to decouple persistence from business logic, add richer chunk metadata, and prepare for pluggable vector stores. Key code changes already made in this branch include:

- Introduced `VectorRepository` interface (repository layer).
- Added `PostgresVectorRepository` which contains pgvector SQL and richer schema (document_name, page_number, chunk_index, hash, created_at).
- Added `VectorStoreService` abstraction and `PostgresVectorStoreService` implementation delegating to `VectorRepository`.
- Added `ChunkEntity` to represent chunk + metadata to persist.
- Added `EmbeddingResult` record and changed `EmbeddingService.generateEmbedding(...)` to return it.
- Added `SearchResult` DTO (repository layer) returned by nearest-neighbor queries.
- Updated `DocumentUploadService` to depend on `VectorStoreService` (instead of PostgresService), compute SHA-256 hashes for deduplication, and persist `ChunkEntity` objects.

What remains / next tasks
------------------------
These are recommended follow-ups (ranked by priority):

1. Tests (HIGH)
   - Update unit tests to mock `VectorStoreService` and `VectorRepository` where appropriate.
   - Update existing tests that assumed `EmbeddingService` returned `float[]` (we updated one test but check others).
   - Add Testcontainers-based integration tests that start Postgres with the `vector` (pgvector) extension to validate insert + nearest-neighbor end-to-end.

2. Database migrations (HIGH)
   - Add a Flyway/Liquibase migration to create/alter the `embeddings` table to include new columns (`document_name`, `page_number`, `chunk_index`, `hash`, `created_at`).
   - Ensure production migrations are backward-compatible or provide data-migration scripts.

3. API changes / Controllers (MEDIUM) — ✅ COMPLETED
    - ✅ Added RAG-enhanced chat endpoint `/api/chat/rag` with context retrieval and citation support
    - ✅ Created `Retriever` component to handle document retrieval logic
    - ✅ Created `PromptBuilder` component to build RAG prompts with injected context
    - ✅ Added `ChatResponse` DTO with citations, relevance scores, and metadata
    - ✅ Integrated VectorStoreService and EmbeddingService into ChatController
    - ✅ Kept legacy `/api/chat` endpoint for simple LLM queries without RAG

4. Remove legacy code & tidy (LOW)
   - `embedding/PostgresService.java` currently remains in the codebase. Consider removing or keeping it as a compatibility shim.
   - Clean up unused imports and warnings flagged by the IDE.

5. Documentation (MEDIUM)
   - Update `README.md` and architecture diagrams to show: EmbeddingService -> VectorStoreService -> VectorRepository.
   - Document the `SearchResult` and `EmbeddingResult` DTOs and explain the `hash` deduplication behavior.

6. Additional improvements (future)
    - Implement a `VectorRepository` for other providers (Pinecone, Qdrant, Milvus) behind the same interface.
    - Add a `VectorStoreService` strategy chooser configuration (e.g., `app.vector.provider=postgres|pinecone`) with conditional beans.
    - ✅ Add a `PromptBuilder` + `Retriever` component to encapsulate RAG pipeline (retrieve, build prompt, call LLM, format answer + citations).
    - Add conversation history/memory to track multi-turn RAG queries
    - Add re-ranking of retrieved results for improved relevance
    - Add hybrid search (semantic + keyword-based retrieval)

Current Status
--------------
✅ **Phase 1: Architecture & DTOs** (COMPLETE)
   - Decoupled persistence layer from business logic
   - Created repository abstraction for pluggable vector stores
   - Added rich metadata models and DTOs

✅ **Phase 2: RAG Pipeline** (COMPLETE)
   - Integrated ChatController with vector search
   - Created Retriever for semantic search
   - Created PromptBuilder for context injection
   - Implemented `/api/chat/rag` endpoint with citations

Per-file change checklist (what I changed)
---------------------------------------
- `src/main/java/.../repository/VectorRepository.java` — created interface
- `src/main/java/.../repository/PostgresVectorRepository.java` — created Postgres implementation (moved pgvector SQL here)
- `src/main/java/.../vector/service/VectorStoreService.java` — created interface
- `src/main/java/.../vector/service/PostgresVectorStoreService.java` — created service delegating to repository
- `src/main/java/.../vector/entity/ChunkEntity.java` — created chunk metadata model
- `src/main/java/.../embedding/dto/EmbeddingResult.java` — created embedding result record
- `src/main/java/.../repository/SearchResult.java` — created search result DTO
- `src/main/java/.../embedding/service/EmbeddingService.java` — now returns `EmbeddingResult`
- `src/main/java/.../document/service/DocumentUploadService.java` — now uses `VectorStoreService`, computes content hash (SHA-256), stores `ChunkEntity`
- `src/test/java/.../DemoApplicationTests.java` — adapted to `EmbeddingResult`
- ✅ `src/main/java/.../chat/ChatController.java` — added RAG endpoint and integrated services
- ✅ `src/main/java/.../rag/Retriever.java` — created retrieval component
- ✅ `src/main/java/.../rag/PromptBuilder.java` — created prompt building component
- ✅ `src/main/java/.../chat/dto/ChatResponse.java` — created response DTO with citations

How to proceed (recommended next PRs)
-----------------------------------
1. Add DB migrations and Testcontainers integration tests in a follow-up PR.
2. Replace or remove `PostgresService` after tests are green.
3. Add an HTTP search endpoint and update the chat flow to consume enriched `SearchResult` DTOs.

If you want, I can: create the Flyway migration, add Testcontainers integration tests, and add a sample nearest-neighbor HTTP endpoint next. Tell me which to prioritize and I'll implement it.

