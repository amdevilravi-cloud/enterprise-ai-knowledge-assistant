
1.Additional improvements (future)
       - Add conversation history/memory to track multi-turn RAG queries
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
   - re-ranking of retrieved results for improved relevance

