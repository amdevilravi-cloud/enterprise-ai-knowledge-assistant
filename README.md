Enterprise AI Knowledge Assistant (Skeleton)

This project is a lightweight skeleton demonstrating:
- Spring AI chat + embedding usage
- Storing embeddings in PostgreSQL using the pgvector extension
- A simple document upload pipeline that chunks text, generates embeddings, and stores them in the vector DB

Quick notes
- Default LLM provider: configure `app.llm.provider` in `src/main/resources/application.properties` (this repo defaults to `lmstudio`).
- Local LM Studio endpoint: configured with `spring.ai.openai.base-url` (example: `http://127.0.0.1:1234`).

What changed / important features
- `EmbeddingService` wraps a Spring AI `EmbeddingModel` to produce float[] embeddings.
- `PostgresService` creates an `embeddings` table (if missing) and stores vectors using Postgres `vector` type (pgvector). It also exposes a `findNearest(...)` method that uses the pgvector `<->` operator for nearest-neighbor search.
- `DocumentUploadService` now:
  - extracts text from uploaded files (.txt, .pdf, fallback to UTF-8 text),
  - chunks documents with `DocumentChunkService`,
  - generates embeddings for each chunk using `EmbeddingService`, and
  - stores each chunk + vector in Postgres via `PostgresService`.

Prerequisites for full functionality
- PostgreSQL server (12+) with the pgvector extension installed. Example to enable the extension:

```sql
-- run as a superuser or a role that can create extensions
CREATE EXTENSION IF NOT EXISTS vector;
```

- Database connection properties are in `src/main/resources/application.properties`. Example:

```
spring.datasource.url=jdbc:postgresql://localhost:5432/enterprise_ai
spring.datasource.username=workspace
spring.datasource.password=MyStrongPassword123!
spring.jpa.hibernate.ddl-auto=update
```

Notes about tests and running locally
- The test profile uses H2 in PostgreSQL compatibility mode. H2 does not support the pgvector extension, so `PostgresService.findNearest(...)` will return an empty list when running tests against H2. The application tolerates this and continues to operate, but nearest-neighbor search requires a real Postgres + pgvector for correct behavior.
- If you want reproducible integration tests for pgvector, consider adding Testcontainers with a Postgres image that has pgvector installed (I can add this if you want).

## GitHub Codespaces Support 🚀

This project is fully configured to run in GitHub Codespaces with automatic PostgreSQL + pgvector setup and OpenAI integration.

### Prerequisites for Codespaces

1. **Add OpenAI API Key as a Codespace Secret:**
   - Go to [github.com/settings/codespaces](https://github.com/settings/codespaces)
   - Click "Codespace secrets" (or "New secret")
   - Create a secret named `OPENAI_API_KEY` with your OpenAI API key
   - **Important:** This secret must be added **before** creating the Codespace for it to be available to the environment

2. **Optional: Set LM Studio URL locally**
   - If running locally with LM Studio, set the environment variable: `export LM_STUDIO_URL=http://your-ip:1234`
   - Default is `http://localhost:1234`

### Launch in Codespaces

1. Click "Code" → "Codespaces" → "Create codespace on main"
2. Wait for the container to start (the dev container will automatically):
   - Start a PostgreSQL 16 + pgvector service
   - Download Maven dependencies
   - Build the application
3. Once ready, the app will be accessible at `http://localhost:8080` (forwarded port)
4. The application uses the `codespace` profile automatically

### Codespaces Configuration Files

- **`docker-compose.yml`** — Defines PostgreSQL + pgvector service for Codespaces
- **`.devcontainer/devcontainer.json`** — Dev container setup with Java 21, Docker-in-Docker, and Spring Boot extensions
- **`application-codespace.properties`** — Profile-specific config (OpenAI endpoint, Docker DB connection)
- **`init-db.sql`** — Initializes pgvector extension on DB startup

How to build & run

From the project root:

```bash
cd "/Users/workspace/Desktop/workspace/Enterprise AI Knowledge Assistant/enterprise-ai-knowledge-assistant"
mvn -U clean package
java -jar target/enterprise-ai-knowledge-assistant-1.0.0-SNAPSHOT.jar
```

Or run from your IDE (import `pom.xml` and run `DemoApplication`).

Developer-focused tips
- The project includes the `spring-ai` starters and a pgvector vector-store starter in `pom.xml`.
- I changed the PostgreSQL JDBC dependency to be available at compile-time so the code can construct vector literals — no further JDBC driver changes should be necessary.
- `PostgresService` attempts to create the `vector` extension at startup but ignores failures (permission issues). Make sure the DB has pgvector available for vector search to work.

Project structure

```
enterprise-ai-knowledge-assistant/
├─ pom.xml
├─ README.md
├─ src/
│  ├─ main/
│  │  ├─ java/com/enterprise/ai/knowledge/assistant/demo/
│  │  │  ├─ DemoApplication.java                # Spring Boot entrypoint
│  │  │  ├─ controller/                          # REST controllers (chat, documents)
│  │  │  ├─ config/                              # Conditional config for chat/llm
│  │  │  ├─ embedding/
│  │  │  │  ├─ EmbeddingService.java            # wraps Spring AI EmbeddingModel
│  │  │  │  └─ PostgresService.java              # pgvector insert + k-NN search
  │  │  │  ├─ document/
│  │  │  │  ├─ document/service/
│  │  │  │  │  ├─ DocumentUploadService.java     # handles file upload, chunking, embedding storage
│  │  │  │  │  └─ DocumentChunkService.java      # chunking utility
│  │  │  └─ ...
│  │  └─ resources/
│  │     └─ application.properties
│  └─ test/
└─ target/
```

Architecture (ASCII diagram)

```
 Client (HTTP)
     |
     v
  DocumentUploadController ---> DocumentUploadService
                                        |
                                        v
                              DocumentChunkService (chunks text)
                                        |
                                        v
                             EmbeddingService (generates float[])
                                        |
                                        v
                              PostgresService (stores vector in pgvector)

  ChatController -> ChatClient (Spring AI) -> EmbeddingService (for vector queries)
  PostgresService.findNearest(queryVector) -> Postgres (pgvector) -> returns nearest chunks
```

Sequence diagram (document upload -> store embedding)

```
User -> HTTP POST /documents : upload file
HTTP POST /documents -> DocumentUploadService : save file
DocumentUploadService -> DocumentChunkService : chunkText(fileText)
DocumentChunkService --> DocumentUploadService : List<chunks>
loop for each chunk
    DocumentUploadService -> EmbeddingService : generateEmbedding(chunk)
    EmbeddingService --> DocumentUploadService : float[] embedding
    DocumentUploadService -> PostgresService : insertEmbedding(chunk, embedding)
    PostgresService -> Postgres DB : INSERT ... VALUES (..., chunk::vector, ...)
end
DocumentUploadService --> HTTP POST /documents : 200 OK + metadata
```

Next possible enhancements
- ✅ Add an HTTP endpoint to run nearest-neighbor queries and return the top-K matching document chunks. → **DONE** (`/api/chat/rag`)
- Add Testcontainers-based integration tests that start Postgres with pgvector to validate insert + nearest-neighbor end-to-end.
- Add metrics/logging around embedding generation and DB insertion failures so you can monitor best-effort behavior.

## RAG (Retrieval-Augmented Generation) Integration ✨

The application now includes a complete RAG pipeline for context-aware AI chat:

### New Endpoints

**RAG-Enhanced Chat** (context-aware)
```
GET /api/chat/rag?message=<query>&topK=5
```
Returns a `ChatResponse` with answer + citations from retrieved documents.

**Legacy Chat** (simple LLM query)
```
GET /api/chat?message=<query>
```
Returns a plain string response (no document retrieval).

### RAG Pipeline Components

**Retriever** (`rag/Retriever.java`)
- Embeds user queries
- Searches vector store for nearest chunks
- Returns top-K relevant documents with metadata

**PromptBuilder** (`rag/PromptBuilder.java`)
- Formats retrieved context
- Injects context into system/user prompts
- Ensures consistent prompt structure

**ChatResponse** (`chat/dto/ChatResponse.java`)
```json
{
  "answer": "...",
  "citations": [
    {
      "documentName": "file.pdf",
      "pageNumber": 2,
      "chunkIndex": 0,
      "relevanceScore": 0.95
    }
  ],
  "isFromContext": true,
  "retrievalCount": 5
}
```

### Example: Context-Aware Chat

1. **Upload document:**
   ```bash
   curl -X POST http://localhost:8080/api/documents \
     -F "file=@CompanyPolicies.pdf"
   ```

2. **Query with context:**
   ```bash
   curl "http://localhost:8080/api/chat/rag?message=What%20is%20the%20vacation%20policy"
   ```

3. **Receive answer with citations:**
   ```json
   {
     "answer": "Based on the company handbook, employees receive 20 days of paid time off annually...",
     "citations": [
       {
         "documentName": "CompanyPolicies.pdf",
         "pageNumber": 2,
         "chunkIndex": 0,
         "relevanceScore": 0.98
       }
     ],
     "isFromContext": true,
     "retrievalCount": 1
   }
   ```

### RAG Benefits

- **Reduced Hallucinations**: LLM answers based on actual documents
- **Traceability**: Citations show source documents
- **Customization**: Easy to add domain-specific documents
- **Transparency**: Relevance scores indicate confidence

### Architecture: Document Upload → RAG Chat

```
1. Document Upload
   ↓
   DocumentUploadService (extract text, chunk, hash)
   ↓
   For each chunk:
     ├─ EmbeddingService (generate embedding)
     └─ VectorStoreService (store with metadata)
   
2. RAG Chat Query
   ↓
   ChatController (/api/chat/rag)
   ├─ Retriever (query embedding + vector search)
   ├─ PromptBuilder (inject context)
   ├─ ChatClient (send to LLM)
   └─ Format response with citations
```

### Configuration

```properties
# In application.properties

# LLM Provider
app.llm.provider=lmstudio
spring.ai.openai.base-url=http://127.0.0.1:1234

# PostgreSQL
spring.datasource.url=jdbc:postgresql://localhost:5432/enterprise_ai
spring.datasource.username=workspace
spring.datasource.password=password
```

### Testing RAG Components

Unit tests included:
- `ChatControllerTest` - RAG endpoint integration
- `RetrieverTest` - Document retrieval logic
- `PromptBuilderTest` - Prompt construction

Run tests:
```bash
mvn test
```

### See Also

- `RAG_INTEGRATION.md` - Detailed integration guide
- `RAG_INTEGRATION_SUMMARY.md` - Architecture overview
- `TODO.md` - Roadmap and remaining tasks

If you'd like, I can implement the following enhancements:
- Testcontainers-based integration tests
- Database migrations (Flyway/Liquibase)
- Alternative vector store providers (Pinecone, Qdrant)
- Conversation memory for multi-turn RAG

