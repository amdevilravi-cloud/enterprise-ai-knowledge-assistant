# RAG Chat Integration Guide

## Overview

This document explains the integration of ChatController with VectorStoreService and EmbeddingService to provide RAG (Retrieval-Augmented Generation) capabilities.

## Architecture

The RAG pipeline consists of the following components:

```
ChatController
    ├─ Retriever (retrieves relevant chunks)
    │   ├─ EmbeddingService (generates embeddings)
    │   └─ VectorStoreService (searches vector store)
    ├─ PromptBuilder (builds augmented prompts)
    └─ ChatClient (sends prompt to LLM)
```

## Components

### 1. **Retriever** (`rag/Retriever.java`)
- Encapsulates the retrieval logic
- Takes a user query and returns the top-K most relevant document chunks
- Methods:
  - `retrieve(String query)` - Retrieves top-5 chunks (default)
  - `retrieve(String query, int k)` - Retrieves top-K chunks
  - `buildContext(List<SearchResult> results)` - Formats results as context

### 2. **PromptBuilder** (`rag/PromptBuilder.java`)
- Builds RAG prompts by injecting retrieved context
- Formats the LLM prompt with source document information
- Methods:
  - `buildRagPrompt(String userQuery, String context)` - Builds prompt with context
  - `buildRagPrompt(String userQuery, List<SearchResult> results)` - Builds prompt from results
  - `getSystemPrompt()` - Returns the system prompt for the LLM

### 3. **ChatResponse** (`chat/dto/ChatResponse.java`)
- Response DTO containing:
  - `answer` - The LLM's generated answer
  - `citations` - List of source documents cited in the answer
  - `isFromContext` - Whether the answer used retrieved context
  - `retrievalCount` - Number of documents retrieved

### 4. **ChatController** (`chat/ChatController.java`)
- REST endpoints:
  - `GET /api/chat?message=<query>` - Simple chat without RAG (legacy)
  - `GET /api/chat/rag?message=<query>&topK=5` - RAG-enhanced chat

## Usage Examples

### Simple Chat (No RAG)
```bash
curl "http://localhost:8080/api/chat?message=What%20is%20Spring%20Boot"
```

Response: Plain string answer

### RAG Chat
```bash
curl "http://localhost:8080/api/chat/rag?message=What%20are%20the%20vacation%20policies&topK=5"
```

Response:
```json
{
  "answer": "Based on the company documents, employees receive 20 days of paid time off annually...",
  "citations": [
    {
      "documentName": "EmployeeHandbook.pdf",
      "pageNumber": 2,
      "chunkIndex": 0,
      "relevanceScore": 0.98
    }
  ],
  "isFromContext": true,
  "retrievalCount": 1
}
```

## Data Flow

### Step 1: Document Upload
1. User uploads a document via `POST /api/documents`
2. DocumentUploadService:
   - Extracts text from the document
   - Chunks text into overlapping segments
   - Generates embeddings for each chunk
   - Stores chunks in vector database with metadata

### Step 2: RAG Chat Query
1. User sends query: `GET /api/chat/rag?message="Find vacation policies"`
2. Retriever retrieves relevant chunks:
   - EmbeddingService generates embedding for the query
   - VectorStoreService finds nearest chunks (using pgvector distance operator)
3. PromptBuilder builds augmented prompt:
   ```
   [System] You are an enterprise knowledge assistant...
   [Context] Based on retrieved documents:
   [Document 1] Employee Handbook (Page 2)
   Content: Employees receive 20 days of paid time off...
   
   User Question: Find vacation policies
   ```
4. ChatClient sends to LLM and gets response
5. ChatResponse returns answer with citations

## Metadata Stored Per Chunk

- `documentName` - Source document file name
- `pageNumber` - Page number in original document
- `chunkIndex` - Index of chunk within document
- `content` - Chunk text
- `embedding` - Vector embedding (pgvector format)
- `hash` - SHA-256 hash for deduplication
- `createdAt` - Timestamp

## Error Handling

The RAG endpoint gracefully handles errors:
- If retrieval fails → Falls back to simple LLM chat
- If embedding generation fails → Returns empty context
- If LLM call fails → Returns error response

## Configuration

### Embedding Provider
Configure via `application.properties`:
```properties
# LLM Provider (openai, lmstudio)
app.llm.provider=lmstudio
spring.ai.openai.base-url=http://127.0.0.1:1234

# Or for OpenAI
spring.ai.openai.api-key=${OPENAI_API_KEY}
```

### Vector Store (PostgreSQL + pgvector)
```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/enterprise_ai
spring.datasource.username=postgres
spring.datasource.password=password
```

## Testing

### Test Data Flow
1. Upload document with vacation policies
2. Query with different phrasings to test retrieval
3. Verify retrieved chunks are relevant
4. Verify citations in response match retrieved documents

### Example Test Queries
- "How many vacation days do I get?"
- "What's the time-off policy?"
- "PTO allowance?"
- "Paid vacation"

Each should retrieve similar/same documents due to embedding similarity.

## Future Enhancements

1. **Conversation Memory**
   - Store chat history and context
   - Reference previous answers in follow-up queries

2. **Citation Builder**
   - Auto-format citations in response
   - Add reference hyperlinks

3. **Multi-Provider Vector Stores**
   - Support Pinecone, Qdrant, Milvus, Weaviate
   - Switch via configuration

4. **Advanced Retrieval**
   - Hybrid search (semantic + keyword)
   - Re-ranking of results
   - Query expansion

5. **Evaluation**
   - Track answer accuracy
   - Measure retrieval quality
   - Log hallucinations

## Files Modified/Created

- **Modified:**
  - `chat/ChatController.java` - Added RAG endpoint
  - `document/service/DocumentUploadService.java` - Already integrated

- **Created:**
  - `rag/Retriever.java` - Retrieval component
  - `rag/PromptBuilder.java` - Prompt building component
  - `chat/dto/ChatResponse.java` - Response DTO with citations

