RAG Pipeline — Consolidated Integration Summary

Summary
-------
This document consolidates the project's RAG (Retrieval-Augmented Generation) integration notes and the current, relevant implementation details into a single concise reference.

Goal
----
Provide a single-source summary describing the implemented pipeline in the project:

Question → Embedding → Vector Search (Top 20) → Metadata Filter → Re-ranker → Top 3 → Prompt Builder → LLM → Grounded Answer

Architecture (high level)
-------------------------
ChatController (/api/chat, /api/chat/rag)
  ├─ Retriever
  │   ├─ EmbeddingService (generate query embedding)
  │   └─ VectorStoreService (findNearest(queryVector, k))
  ├─ MetaDataFilter (optional filtering by metadata)
  ├─ ReRanker (embedding-based default, optional LLM-based scoring)
  └─ PromptBuilder (injects retrieved context into prompt)
ChatClient (Spring AI) — sends prompt to configured LLM (lmstudio/openai)

Core components (what is implemented and where)
-----------------------------------------------
- Retriever (src/main/java/.../rag/Retriever.java)
  - Orchestrates retrieval flow
  - Methods: retrieve(String query), retrieve(String query, int k), retrieveAndRerank(String query, Integer vectorTopK, Integer finalTopN), buildContext(List<SearchResult>)

- EmbeddingService (src/main/java/.../embedding/service/EmbeddingService.java)
  - Wraps Spring AI EmbeddingModel and returns EmbeddingResult (float[] vector)

- VectorStoreService + VectorRepository
  - Interface: VectorStoreService (findNearest(float[] query, int k))
  - Postgres-based implementation delegates to VectorRepository / PostgresVectorRepository (pgvector search)
  - Search result DTO: SearchResult (content, score, pageNumber, documentName, chunkIndex)

- MetaDataFilter (src/main/java/.../rag/MetaDataFilter.java)
  - Lightweight component with Criteria (minScore, allowedDocuments, excludedDocuments)
  - Default behavior: pass-through when no criteria provided

- ReRanker (src/main/java/.../rag/ReRanker.java)
  - Default strategy: embedding-based re-ranking
    - Generates embedding for query and candidate contents
    - Computes cosine similarity and combines with stored score
  - Optional strategy: llm-based scoring (configurable via property)
    - Calls ChatClient with a compact scoring prompt; expects numeric scores
    - Falls back to embedding strategy if parsing fails

- PromptBuilder (src/main/java/.../rag/PromptBuilder.java)
  - Builds system prompt and formatted context block for the LLM
  - Methods: buildRagPrompt(String userQuery, List<SearchResult> results), getSystemPrompt()

- ChatController (src/main/java/.../chat/ChatController.java)
  - Endpoints:
    - GET /api/chat?message=<query> — simple LLM chat
    - GET /api/chat/rag?message=<query>&vectorTopK=20&finalTopN=3 — RAG-enhanced chat
  - Updated to call Retriever.retrieveAndRerank(message, vectorTopK, finalTopN)

Data flow (detailed)
--------------------
1. Client calls GET /api/chat/rag?message=<query>&vectorTopK=20&finalTopN=3
2. Retriever.generateEmbedding(query) via EmbeddingService
3. VectorStoreService.findNearest(queryVector, vectorTopK) — initial candidate set (Top 20 recommended)
4. MetaDataFilter.filter(initialCandidates, criteria) — optional metadata-based pruning (default: pass-through)
5. ReRanker.rerank(filteredCandidates, query, finalTopN)
   - Embedding-based: compute candidate embeddings on-the-fly (or reuse stored embeddings) and calculate cosine similarity; combine with repository score; return top finalTopN (Top 3 recommended)
   - LLM-based: optionally call ChatClient to score candidates; parse numeric scores and combine; fallback to embedding-based if needed
6. PromptBuilder.buildRagPrompt(query, finalTopResults) — constructs augmented prompt including context blocks and system instructions
7. ChatClient sends system + user prompt to LLM and returns answer
8. ChatController returns ChatResponse containing answer + citations (document names, page numbers, chunk indices, relevance scores)

Configuration (defaults)
------------------------
Add or override in src/main/resources/application.properties:

app.rag.vectorTopK=20      # how many candidates to fetch from vector store
app.rag.finalTopN=3        # how many candidates to keep after re-ranking
app.reranker.strategy=embedding  # 'embedding' (default) or 'llm'
app.reranker.maxCandidates=50    # optional cap for re-ranker

LLM provider configuration remains in application.properties (app.llm.provider=openai|lmstudio)

Behavior and error handling
---------------------------
- If embedding generation fails for the query → Retriever returns empty context and ChatController falls back to simple chat.
- If vector search fails → Retriever returns empty context (or best-effort subset) and ChatController falls back to simple chat.
- If re-ranker fails (LLM parsing or embedding errors) → fallback to embedding-based or original candidate ordering and return top N.
- All stages are implemented with best-effort graceful degradation; errors don't crash the REST endpoint.

Performance & operational notes
-------------------------------
- Candidate embedding generation is performed on-the-fly in the current implementation; consider persisting chunk embeddings in the vector store (ChunkEntity) to avoid recomputing embeddings for re-ranking.
- Add a small in-memory LRU cache for candidate embeddings if throughput/latency become a concern.
- LLM-based re-ranking is higher-latency and costlier; use it only when higher precision is required.

Endpoint examples
-----------------
Default (uses configured defaults):

curl 'http://localhost:8080/api/chat/rag?message=What%20is%20the%20vacation%20policy'

Explicit:

curl 'http://localhost:8080/api/chat/rag?message=What%20is%20the%20vacation%20policy&vectorTopK=20&finalTopN=3'

Response shape (ChatResponse)
-----------------------------
{
  "answer": "...",
  "citations": [
    { "documentName": "EmployeeHandbook.pdf", "pageNumber": 2, "chunkIndex": 0, "relevanceScore": 0.98, "content": "...excerpt..." }
  ],
  "isFromContext": true,
  "retrievalCount": 3
}

Recommendations / next actions
------------------------------
- Persist chunk embeddings in the vector store (if not already) so the re-ranker can reuse them instead of regenerating.
- Add a small embedding cache for re-ranking to reduce repeated calls to the embedding model.
- Add Testcontainers-based integration tests using Postgres + pgvector to validate end-to-end behavior.
- Consider returning structured LLM scores (JSON) for LLM-based re-ranking to simplify parsing and robustness.
- Optionally expose metadata filtering parameters via the RAG endpoint to allow clients to control allowed/excluded documents or minimum relevance thresholds.

Location of important files (project)
-------------------------------------
- Retriever: src/main/java/com/enterprise/ai/knowledge/assistant/demo/rag/Retriever.java
- ReRanker: src/main/java/com/enterprise/ai/knowledge/assistant/demo/rag/ReRanker.java
- MetaDataFilter: src/main/java/com/enterprise/ai/knowledge/assistant/demo/rag/MetaDataFilter.java
- PromptBuilder: src/main/java/com/enterprise/ai/knowledge/assistant/demo/rag/PromptBuilder.java
- ChatController: src/main/java/com/enterprise/ai/knowledge/assistant/demo/chat/ChatController.java
- EmbeddingService: src/main/java/com/enterprise/ai/knowledge/assistant/demo/embedding/service/EmbeddingService.java
- Vector store: src/main/java/com/enterprise/ai/knowledge/assistant/demo/vector/service (PostgresVectorStoreService)

This file intentionally keeps only the most relevant, up-to-date details of the RAG integration to serve as a quick reference for developers. For full background and historical notes, refer to the project's README and the RAG integration docs in the repository.

Architecture Diagram (ASCII)
----------------------------
High-level components and data flow:

```
           +----------------------+          +----------------+
           |      Client / UI     |          |   Admin / Ops  |
           +----------+-----------+          +--------+-------+
                      |                               |
                      v                               v
               +------+-------------------------------+------+
               |             ChatController (REST)           |
               +------+-------------------------------+------+
                      |                               |
          /-----------+-----------\             /-----+------\
         v                       v           v              v
  +------+------+         +------+------+ +------+     +------+ 
  | Retriever |         | PromptBuilder| | Docs |     | Config|
  +------+------+         +------+------+ +------+     +------+ 
     |    |                    |                    
     |    v                    v                    
     |  +-------------------------------+           
     |  | ReRanker (embedding|llm)     |           
     |  +-------------------------------+           
     |                |                             
     v                v                             
 +---------------------------+                      
 |   VectorStoreService      | <---> VectorRepository
 |  (PostgresVectorStoreSvc) |                      
 +---------------------------+                      
            ^    ^                                    
            |    |                                    
      +-----+    +------+                             
      | EmbeddingService  |                           
      +-------------------+                           
```

Sequence Diagram (textual)
--------------------------
1. Client -> ChatController: GET /api/chat/rag?message=Q
2. ChatController -> Retriever: retrieveAndRerank(Q, vectorTopK=20, finalTopN=3)
3. Retriever -> EmbeddingService: generateEmbedding(Q)
4. Retriever -> VectorStoreService: findNearest(queryVector, 20)
5. VectorStoreService -> VectorRepository: SQL/pgvector k-NN
6. VectorRepository -> VectorStoreService -> Retriever: List<SearchResult>
7. Retriever -> MetaDataFilter: filter(results, criteria)
8. Retriever -> ReRanker: rerank(filtered, Q, 3)
   - ReRanker -> EmbeddingService (optional): generateEmbedding(candidate)
   - (or) ReRanker -> ChatClient (optional): scoring prompt
9. ReRanker -> Retriever: top-3 results
10. Retriever -> PromptBuilder: buildRagPrompt(Q, top-3)
11. ChatController -> ChatClient: system + user prompt
12. ChatClient -> LLM -> ChatController: answer
13. ChatController -> Client: ChatResponse(answer, citations)

L1 Diagram (Layered view)
-------------------------
- Presentation Layer
  - ChatController (REST endpoints)

- Application / Orchestration Layer
  - Retriever
  - ReRanker
  - MetaDataFilter
  - PromptBuilder

- Integration Layer
  - EmbeddingService
  - VectorStoreService (service wrapper)
  - ChatClient (Spring AI wrapper for LLM)

- Persistence / Data Layer
  - VectorRepository / PostgresVectorRepository (pgvector)
  - Database: PostgreSQL + pgvector

Project Structure (trimmed to relevant paths)
-------------------------------------------
```
enterprise-ai-knowledge-assistant/
├─ pom.xml
├─ README.md
├─ src/
│  ├─ main/
│  │  ├─ java/com/enterprise/ai/knowledge/assistant/demo/
│  │  │  ├─ DemoApplication.java
│  │  │  ├─ chat/
│  │  │  │  ├─ ChatController.java
│  │  │  │  └─ dto/ChatResponse.java
  │  │  │  ├─ rag/
│  │  │  │  │  ├─ Retriever.java
│  │  │  │  │  ├─ ReRanker.java
│  │  │  │  │  ├─ MetaDataFilter.java
│  │  │  │  │  └─ PromptBuilder.java
│  │  │  │  ├─ embedding/
│  │  │  │  │  ├─ EmbeddingService.java
│  │  │  │  │  └─ PostgresService.java (optional legacy)
│  │  │  │  ├─ vector/
│  │  │  │  │  ├─ service/
│  │  │  │  │  │  ├─ VectorStoreService.java
│  │  │  │  │  │  └─ PostgresVectorStoreService.java
│  │  │  │  │  └─ entity/ChunkEntity.java
│  │  │  │  └─ repository/
│  │  │  │     ├─ VectorRepository.java
│  │  │  │     └─ PostgresVectorRepository.java
│  │  └─ resources/
│  │     └─ application.properties
└─ DOCS_RAG_SUMMARY.md
```

If you want these diagrams exported to PNG/SVG (for docs site or README), I can generate Mermaid diagrams and add a small script to convert them, or produce PNGs and add them to the repo.

