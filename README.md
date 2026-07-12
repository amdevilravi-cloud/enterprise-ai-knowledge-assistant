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
- Add an HTTP endpoint to run nearest-neighbor queries and return the top-K matching document chunks.
- Add Testcontainers-based integration tests that start Postgres with pgvector to validate insert + nearest-neighbor end-to-end.
- Add metrics/logging around embedding generation and DB insertion failures so you can monitor best-effort behavior.

If you'd like, I can implement any of the enhancements above (endpoint, tests, monitoring) — tell me which one and I'll add it.
