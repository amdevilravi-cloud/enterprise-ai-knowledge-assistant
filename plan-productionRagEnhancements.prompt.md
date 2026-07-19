# Production RAG Enhancement Plan — Phases 1–6

**Status**: Analysis Complete | Date: 2026-07-19
**Objective**: Transform current RAG system from single-turn QA to production-grade conversational system with hybrid search, context intelligence, and multi-document reasoning.

---

## Executive Summary

Your foundation is **already strong**:
- ✅ PDF ingestion & chunking  
- ✅ Embeddings & pgvector storage  
- ✅ Vector search with re-ranking  
- ✅ RAG prompting with citations  
- ✅ PostgreSQL backend  

**Roadmap**: **5 phases over ~10–12 weeks**, each adding measurable UX/performance value.

---

## Current Architecture (Baseline)

**Data Flow**:
```
Document Upload
    ↓
DocumentIngestionOrchestrator → DocumentChunkService (1000 chars)
    ↓
EmbeddingService (LLM) → PostgresVectorRepository (pgvector)
    ↓
ChatController (/api/chat/rag)
    ├→ Retriever.retrieveAndRerank(query, vectorTopK=20, finalTopN=3)
    ├→ PromptBuilder.buildRagPrompt(query, results)
    ├→ ChatClient.prompt().system(...).user(...).call()
    └→ ChatResponse (answer + citations)
```

**Current Limitations**:
1. ❌ No conversation state — each query is independent
2. ❌ Vector-only retrieval — misses exact keyword matches (e.g., "HR-401")
3. ❌ No query rewriting — "How many?" won't expand to "How many vacation days?"
4. ❌ Full chunks in prompts — tokens wasted on irrelevant sentences
5. ❌ Single-document bias — citations but no cross-document reasoning

---

## PHASE 1: Conversational Memory (Weeks 1–2)

### Goal
Enable multi-turn conversation where "How many?" understands prior context.

### Database Changes
**New Migration**: `V005__phase1_conversation_memory.sql`

```sql
CREATE TABLE conversation_messages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id UUID NOT NULL,
    message_order INT NOT NULL,
    role VARCHAR(20) NOT NULL CHECK (role IN ('user', 'assistant')),
    message TEXT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    metadata JSONB DEFAULT '{}'::jsonb
);
CREATE INDEX idx_conversation_id ON conversation_messages(conversation_id);
CREATE INDEX idx_conversation_order ON conversation_messages(conversation_id, message_order);

CREATE TABLE conversations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    metadata JSONB DEFAULT '{}'::jsonb
);
```

### New Components

| Component | File | Role |
|-----------|------|------|
| `ConversationMessage` | `conversation/entity/ConversationMessage.java` | Entity for storing turns |
| `ConversationRepository` | `conversation/repository/ConversationRepository.java` | Interface for persistence |
| `PostgresConversationRepository` | `conversation/repository/PostgresConversationRepository.java` | JDBC implementation |
| `MemoryManager` | `conversation/service/MemoryManager.java` | Format history for prompts |
| `ConversationService` | `conversation/service/ConversationService.java` | Orchestrates conversation flow |

### API Changes

**New Endpoints**:
```
POST /api/chat/converse/start
    → Returns: { conversationId: UUID }

POST /api/chat/converse
    Body: { message: "string", historyDepth: 5 }
    → Returns: ChatResponse with conversation history context
```

### PromptBuilder Enhancement

```java
public RagPrompt buildRagPromptWithHistory(String query, 
    List<SearchResult> results, String conversationHistory) {
    // Include history in system/user prompts
    // E.g., "Previous context: [last 5 exchanges]"
}
```

### Acceptance Criteria
- [ ] User starts conversation via `POST /api/chat/converse/start`
- [ ] Each message persists with order + role
- [ ] Last 5 exchanges loaded before retrieval
- [ ] Follow-up questions resolve pronouns (test: "Can I carry them forward?")
- [ ] `/api/chat/rag` still works (backwards compatible)

**Effort**: 1 week backend + 3 days testing

---

## PHASE 2: Hybrid Search + RRF (Weeks 3–5)

### Goal
Combine vector similarity with keyword matching to catch both semantic and exact matches.

**Example**: "What is policy HR-401?"
- Vector search: low match (embeddings don't capture IDs)
- Keyword search (FTS): high match (matches "HR-401" exactly)
- Hybrid result: recovers HR-401 document ✅

### Database Changes
**New Migration**: `V006__phase2_hybrid_search.sql`

```sql
ALTER TABLE embeddings
ADD COLUMN IF NOT EXISTS search_vector tsvector
GENERATED ALWAYS AS (to_tsvector('english', content)) STORED;

CREATE INDEX idx_embeddings_fts ON embeddings USING GIN(search_vector);

CREATE TABLE search_metrics (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    query TEXT,
    vector_score NUMERIC,
    keyword_score NUMERIC,
    fusion_score NUMERIC,
    retrieval_time_ms INT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

### New Components

| Component | File | Role |
|-----------|------|------|
| `VectorRetriever` | `rag/retriever/VectorRetriever.java` | Existing vector search logic |
| `KeywordRetriever` | `rag/retriever/KeywordRetriever.java` | PostgreSQL FTS queries |
| `ReciprocalRankFusion` | `rag/fusion/ReciprocalRankFusion.java` | Merge ranked lists via RRF |
| `HybridRetriever` | `rag/retriever/HybridRetriever.java` | Orchestrate vector + keyword |

### Reciprocal Rank Fusion (RRF) Algorithm

```
For each document d in {vector results} ∪ {keyword results}:
    RRF(d) = Σ 1/(k + rank(d))
    where k=60 (tuning parameter)

Sort by RRF score and return top-N
```

**Example**:
```
Vector: [A(rank 1), B(rank 2), C(rank 3)]
Keyword: [B(rank 1), D(rank 2), A(rank 3)]

RRF scores:
  A: 1/61 + 1/63 = 0.0313
  B: 1/62 + 1/61 = 0.0328 ← highest
  C: 1/63 = 0.0159
  D: 1/62 = 0.0161

Result: [B, A, D, C] ✅
```

### Integration with Existing Code

Modify `Retriever.retrieveAndRerank()` to support optional hybrid mode:

```java
@Component
public class Retriever {
    private final HybridRetriever hybridRetriever;
    @Value("${app.rag.enableHybridSearch:false}") 
    private boolean enableHybridSearch;
    
    public List<SearchResult> retrieveAndRerank(String query, Integer vectorTopK, Integer finalTopN) {
        if (enableHybridSearch) {
            return hybridRetriever.retrieve(query, finalTopN);
        }
        // Fallback to vector-only
        return vectorSearchOnly(query, vectorTopK, finalTopN);
    }
}
```

### Application Properties

```properties
app.rag.enableHybridSearch=false    # Start disabled, toggle for Phase 2+
app.rag.vectorTopK=20
app.rag.keywordTopK=20
app.rag.finalTopN=3
app.rag.rrfK=60                     # RRF tuning parameter
```

### Test Case: Exact Match Recovery

```java
@Test
public void testKeywordCatchesExactMatch() {
    // Query: "HR-401" (exact policy ID)
    List<SearchResult> result = hybridRetriever.retrieve("HR-401", 3);
    assertTrue(result.stream()
        .anyMatch(r -> r.getContent().contains("HR-401")));
    // Vector alone would fail; hybrid succeeds ✅
}
```

### Acceptance Criteria
- [ ] FTS index exists; queries complete <100ms
- [ ] Vector + Keyword run in parallel
- [ ] RRF correctly weights both signals
- [ ] "HR-401" query returns policy document
- [ ] Backwards compatible (toggle via config)

**Effort**: 1.5 weeks backend + 1 week testing + 1 week tuning = **3.5 weeks**

---

## PHASE 3: Query Rewriting (Weeks 6–7)

### Goal
Rewrite ambiguous follow-up questions using conversation context before retrieval.

**Example**:
```
Turn 1: "What is the leave policy?"
Turn 2: "How many?" 
  ↓ (rewrite using history)
  "How many leave days are provided in the vacation policy?"
  ↓ (search on rewritten query)
  → Better retrieval accuracy ✅
```

### New Component

**`QueryRewriter`** (`rag/rewriter/QueryRewriter.java`):

```java
@Component
public class QueryRewriter {
    private final ChatClient chatClient;
    
    public String rewrite(String query, String conversationHistory) {
        if (conversationHistory == null) return query; // No context
        
        String prompt = String.format("""
            Given conversation history, rewrite the latest question 
            to be standalone. If already clear, return unchanged.
            
            History: %s
            Latest: %s
            Rewritten:
            """, conversationHistory, query);
        
        return chatClient.prompt()
            .system("Rewrite queries for clarity.")
            .user(prompt)
            .call()
            .content()
            .trim();
    }
}
```

### Integration

Modify `HybridRetriever`:

```java
public List<SearchResult> retrieve(String query, String history, int topK) {
    String finalQuery = query;
    if (enableQueryRewriting && history != null) {
        finalQuery = queryRewriter.rewrite(query, history);
        log.info("Rewritten: '{}' → '{}'", query, finalQuery);
    }
    return retrieveHybrid(finalQuery, topK);
}
```

### Application Properties

```properties
app.rag.enableQueryRewriting=false  # Start disabled
```

### Acceptance Criteria
- [ ] Ambiguous queries rewritten correctly (test suite)
- [ ] Retrieval precision +10%
- [ ] Query rewriting latency <500ms (configurable)

**Effort**: 2 weeks (including LLM prompt tuning)

---

## PHASE 4: Context Compression (Weeks 7–8)

### Goal
Extract only relevant sentences from chunks to reduce tokens and improve quality.

**Before**: 1000-char chunk → full text sent to LLM
**After**: Extract 3–5 most relevant sentences → **30% token reduction**

### New Component

**`ContextCompressor`** (`rag/compression/ContextCompressor.java`):

```java
@Component
public class ContextCompressor {
    
    public String compressChunk(String chunk, String query, int topSentences) {
        // 1. Split into sentences
        String[] sentences = chunk.split("[.!?]+");
        
        // 2. Score each sentence against query
        List<ScoredSentence> scored = new ArrayList<>();
        for (int i = 0; i < sentences.length; i++) {
            double score = calculateRelevance(sentences[i], query);
            scored.add(new ScoredSentence(sentences[i], score, i));
        }
        
        // 3. Select top-K, preserve order
        return scored.stream()
            .sorted((a, b) -> Double.compare(b.score, a.score))
            .limit(topSentences)
            .sorted((a, b) -> Integer.compare(a.position, b.position))
            .map(s -> s.sentence)
            .collect(Collectors.joining(" "));
    }
    
    private double calculateRelevance(String sentence, String query) {
        // Keyword overlap heuristic
        String[] queryTerms = query.toLowerCase().split("\\s+");
        String sentenceLower = sentence.toLowerCase();
        int matches = Arrays.stream(queryTerms)
            .filter(sentenceLower::contains)
            .toArray().length;
        return matches / (double) queryTerms.length;
    }
}
```

### Integration with `PromptBuilder`

```java
public RagPrompt buildRagPrompt(String query, List<SearchResult> results) {
    List<SearchResult> compressed = results.stream()
        .map(r -> enableCompression 
            ? compressResult(r, query) 
            : r)
        .collect(Collectors.toList());
    
    // Build prompt with compressed results
    return buildPromptInternal(query, compressed);
}
```

### Application Properties

```properties
app.rag.enableContextCompression=false
app.rag.compressionSentenceCount=3
```

### Test Case

```java
@Test
public void testExtractsRelevantSentences() {
    String chunk = "Offices in NY, London, Tokyo. " +
                   "Employees in NY get 20 days PTO. " +
                   "Lunch provided Tuesdays.";
    String query = "vacation policy";
    
    String result = compressor.compressChunk(chunk, query, 2);
    
    assertThat(result).contains("20 days");
    assertThat(result).doesNotContain("Lunch"); // Not relevant
}
```

### Acceptance Criteria
- [ ] Token usage reduced 30%
- [ ] Answer quality unchanged/improved
- [ ] Sentence splitting handles edge cases (abbreviations, etc.)

**Effort**: 1 week backend + 3 days testing

---

## PHASE 5: Multi-Document Retrieval & Enhanced Citations (Weeks 9–10)

### Goal
Enable answers spanning multiple documents with clear per-document citations.

**Use Case**:
```
Query: "What benefits do I get?"
→ Retrieves from Employee Handbook, Benefits Guide, HR Policy
→ Answer: "According to the Benefits Guide and HR Policy, you get [...]"
→ Citations grouped per document ✅
```

### Enhanced Data Structures

**Modified `ChatResponse`**:

```java
public class ChatResponse {
    private String answer;
    private List<Citation> citations;
    private List<DocumentSource> sourceDocuments;  // NEW
    private Boolean isFromContext;
    private Integer retrievalCount;
    private Map<String, Object> metadata;  // NEW: token count, etc.
    
    public static class DocumentSource {
        private String documentName;
        private String documentId;
        private List<Citation> citationsFromThisDoc;
        private int chunkCount;
    }
}
```

### New Component

**`DocumentGroupingService`** (`rag/service/DocumentGroupingService.java`):

```java
@Component
public class DocumentGroupingService {
    
    public List<ChatResponse.DocumentSource> groupResultsByDocument(
        List<SearchResult> results,
        List<ChatResponse.Citation> citations) {
        
        Map<String, ChatResponse.DocumentSource> docMap = new HashMap<>();
        
        for (ChatResponse.Citation c : citations) {
            String docName = c.getDocumentName();
            docMap.computeIfAbsent(docName, 
                k -> new ChatResponse.DocumentSource(k))
                .addCitation(c);
        }
        
        return new ArrayList<>(docMap.values());
    }
}
```

### Enhanced `PromptBuilder`

```java
public RagPrompt buildMultiDocPrompt(String query, List<SearchResult> results) {
    // Group by document
    Map<String, List<SearchResult>> byDocument = results.stream()
        .collect(Collectors.groupingBy(SearchResult::getDocumentName));
    
    // Build context with headers
    StringBuilder context = new StringBuilder();
    for (Map.Entry<String, List<SearchResult>> entry : byDocument.entrySet()) {
        context.append("\n=== Document: ").append(entry.getKey()).append(" ===\n");
        for (SearchResult r : entry.getValue()) {
            context.append("- ").append(r.getContent()).append("\n");
        }
    }
    
    return new RagPrompt(..., context.toString(), ...);
}
```

### Acceptance Criteria
- [ ] Multiple documents retrieved and cited correctly
- [ ] Citations grouped per document in response
- [ ] Answer explicitly mentions source documents
- [ ] Citation accuracy >95%

**Effort**: 1 week backend + 3 days testing

---

## Implementation Timeline

| Phase | Duration | Key Deliverables | Risk Level |
|-------|----------|------------------|-----------|
| 1: Memory | 2 weeks | Conversation table, ConversationService, API | 🟢 Low |
| 2: Hybrid | 3 weeks | FTS index, VectorRetriever, RRF | 🟡 Medium (tuning) |
| 3: Rewriting | 2 weeks | QueryRewriter, LLM integration | 🟡 Medium (latency) |
| 4: Compression | 2 weeks | ContextCompressor, sentence extraction | 🟢 Low |
| 5: Multi-doc | 2 weeks | DocumentGrouping, citations | 🟢 Low |
| **Total** | **~11 weeks** | Production RAG system | 🟢 Manageable |

---

## New File Structure

```
src/main/java/com/enterprise/ai/knowledge/assistant/demo/
├── conversation/
│   ├── entity/
│   │   ├── Conversation.java
│   │   └── ConversationMessage.java
│   ├── repository/
│   │   ├── ConversationRepository.java
│   │   └── PostgresConversationRepository.java
│   └── service/
│       ├── ConversationService.java
│       └── MemoryManager.java
│
├── rag/
│   ├── retriever/
│   │   ├── VectorRetriever.java (new)
│   │   ├── KeywordRetriever.java (new)
│   │   └── HybridRetriever.java (new)
│   ├── fusion/
│   │   └── ReciprocalRankFusion.java (new)
│   ├── rewriter/
│   │   └── QueryRewriter.java (new)
│   ├── compression/
│   │   └── ContextCompressor.java (new)
│   └── service/
│       └── DocumentGroupingService.java (new)

src/main/resources/db/migration/
├── V005__phase1_conversation_memory.sql
├── V006__phase2_hybrid_search.sql

src/test/java/com/enterprise/ai/knowledge/assistant/demo/
├── conversation/
│   └── ConversationServiceTest.java
├── rag/
│   ├── HybridRetrieverTest.java
│   ├── QueryRewriterTest.java
│   ├── ContextCompressorTest.java
│   └── MultiDocumentRetrievalTest.java
```

---

## Success Metrics

| Phase | Metric | Target |
|-------|--------|--------|
| 1 | Multi-turn accuracy (pronouns resolved) | 90%+ |
| 1 | Conversation latency | <500ms |
| 2 | Exact match recovery (HR-401) | Yes |
| 2 | Retrieval precision improvement | +15% |
| 3 | Ambiguous query resolution | 85%+ |
| 4 | Token usage reduction | -30% |
| 4 | Answer quality (human eval) | No degradation |
| 5 | Cross-document answers | 100% |
| 5 | Citation accuracy | >95% |

---

## Rollout Strategy

### Feature Flags
```properties
app.features.conversationalMemory=true       # Phase 1
app.features.hybridSearch=false              # Phase 2
app.features.queryRewriting=false            # Phase 3
app.features.contextCompression=false        # Phase 4
app.features.multiDocumentRetrieval=false    # Phase 5
```

### Environment Progression
1. **Local dev** (1 week) — feature development + unit tests
2. **Staging** (1 week) — integration tests, performance baseline
3. **Canary prod** (1 week) — 5% traffic, A/B test with existing endpoint
4. **Full prod** — gradual rollout, monitoring

### Backwards Compatibility
- ✅ `/api/chat` (stateless) — unchanged
- ✅ `/api/chat/rag` (vector-only) — unchanged
- 🆕 `/api/chat/converse` (stateful, multi-feature) — new in Phase 1

---

## Risk Mitigation

| Risk | Mitigation |
|------|-----------|
| DB migration failure | Test on staging; have rollback script |
| LLM latency (rewriting/reranking) | Feature flags; fallback to simple path |
| RRF parameter tuning | A/B test K=40, 60, 80; metrics dashboard |
| FTS performance | Monitor index size; partition if needed |
| Token budget overrun | Context compression (Phase 4) reduces costs |

---

## Next Steps
## Phase 6 (UI): Interactive Web Interface (Weeks 11–12)
UI Implementation Plan: Thymeleaf + HTMX + REST Endpoints
Quick Summary
Build an interactive web UI with:
Thymeleaf for server-side template rendering
HTMX for dynamic interactions without page reloads
REST endpoints for AJAX/HTMX integration
Bootstrap 5 for responsive styling
Separation: UI Controllers (render templates) + REST Controllers (JSON)

Architecture Overview
Browser (HTMX) → UI Controller (templates) → Service Layer
↓
REST API (JSON) → Service Layer
UI Controller: New layer that renders Thymeleaf templates
REST API: Enhanced to detect HTMX requests and return HTML fragments
Services: Unchanged - reused by both UI and REST layers

Implementation Plan (5 Phases)
Phase A: Setup & Dependencies (1-2 days)
Add to pom.xml:
<dependency>
<groupId>org.springframework.boot</groupId>
<artifactId>spring-boot-starter-thymeleaf</artifactId>
</dependency>
<dependency>
<groupId>org.webjars</groupId>
<artifactId>bootstrap</artifactId>
<version>5.3.0</version>
</dependency>
Create directory structure:
src/main/resources/
├── templates/
│   ├── layout/base.html (master template)
│   ├── chat/
│   │   ├── index.html
│   │   └── message-item.html (fragment)
│   ├── documents/
│   │   └── index.html
│   └── fragments/
│       ├── navbar.html
│       ├── sidebar.html
│       └── footer.html
└── static/
├── css/style.css
├── js/app.js
└── images/
Update application.properties:
spring.thymeleaf.cache=false
spring.thymeleaf.prefix=classpath:/templates/
spring.thymeleaf.suffix=.html

Phase B: UI Controllers & Templates (3-4 days)
Create UIController.java:
@Controller
@RequestMapping("/ui")
public class UIController {
@GetMapping("/")
public String index(Model model) {
model.addAttribute("conversationId", UUID.randomUUID());
return "chat/index";
}

    @GetMapping("/conversation/{id}")
    public String viewConversation(@PathVariable UUID id, Model model) {
        model.addAttribute("conversationId", id);
        return "chat/conversation";
    }
    
    @GetMapping("/documents")
    public String documents(Model model) {
        return "documents/index";
    }
}
Create templates:
layout/base.html — Master template with navbar, footer
chat/index.html — Main chat interface with HTMX forms
chat/message-item.html — Individual message fragment
documents/index.html — Document management

Phase C: REST Endpoints for HTMX (2-3 days)
Create ChatRestController.java (NEW - for JSON/HTMX responses):
@RestController
@RequestMapping("/api/chat")
public class ChatRestController {
@PostMapping("/message")
public Object sendMessage(
@RequestParam UUID conversationId,
@RequestParam String message,
HttpServletRequest request) {

        ChatResponse response = conversationService.chat(conversationId, message);
        
        // Return HTML fragment if HTMX request
        if ("true".equals(request.getHeader("HX-Request"))) {
            return renderMessageFragment(response);
        }
        return response; // JSON
    }
}
Endpoints needed:
POST /api/chat/message → HTML fragment or JSON
GET /api/chat/messages → JSON array
GET /api/chat/conversations → JSON array
POST /documents/upload → JSON response

Phase D: Interactive Features with HTMX (2-3 days)
Message submission with HTMX:
<form hx-post="/api/chat/message" 
      hx-target="#messageList" 
      hx-swap="beforeend"
      hx-on::after-request="this.reset()">
    <textarea name="message" required></textarea>
    <button type="submit">Send</button>
</form>
Auto-refresh conversation:
<div id="messageList" 
     hx-get="/api/chat/messages?conversationId=${conversationId}"
     hx-trigger="load, newMessage from:body"
     hx-poll="5s">
</div>
File upload with progress:
<form hx-post="/documents/upload" 
      hx-target="#uploadedDocuments"
      hx-swap="beforeend">
    <input type="file" name="file">
</form>
Citations in modal:
<a href="#" 
   hx-get="/api/chat/citation/${citation.chunkHash}"
   hx-target="#citationModal"
   data-bs-toggle="modal"
   data-bs-target="#citationModal">
   View Citation
</a>

Phase E: Advanced Features (Optional, 2-3 days)
Server-Sent Events (SSE) for streaming responses
Conversation search with real-time filtering
Settings panel to toggle features (compression, hybrid search, etc.)
Keyboard shortcuts for chat
Dark mode toggle

REST Endpoints Reference
Method
Endpoint
Purpose
Returns
GET
/ui/
Chat interface
HTML page
GET
/ui/conversation/{id}
View conversation
HTML page
GET
/ui/documents
Document manager
HTML page
POST
/api/chat/message
Send message
HTML fragment (HTMX) or JSON
GET
/api/chat/messages
Load history
JSON array
POST
/api/chat/converse/start
New conversation
JSON: {conversationId}
POST
/documents/upload
Upload file
JSON: {success, documentId, chunks}

Key HTMX Patterns Used
hx-post → Form submission without page reload
hx-target → Where to insert response
hx-swap → How to swap (beforeend, innerHTML, replace, etc.)
hx-trigger → When to send request (load, keyup, change, polling)
hx-select → Extract specific elements from response
hx-on → Handle events (after-request, after-swap, etc.)

File Creation Checklist
Java Files:
UIController.java — Routes to UI pages
ChatRestController.java — JSON/HTMX endpoints
DocumentRestController.java — File upload JSON
Template Files:
templates/layout/base.html — Master template
templates/chat/index.html — Chat page
templates/chat/message-item.html — Message fragment
templates/documents/index.html — Documents page
templates/fragments/navbar.html — Navigation
templates/fragments/sidebar.html — Sidebar
Static Files:
static/css/style.css — Custom styles
static/css/chat.css — Chat styles
static/js/app.js — App initialization
static/js/chat.js — Chat interactions

Timeline
Phase A (Setup): 1-2 days
Phase B (Templates): 3-4 days
Phase C (REST): 2-3 days
Phase D (HTMX): 2-3 days
Phase E (Optional): 2-3 days
Total: 10-15 days for full working UI

Key Benefits
✅ No build process (HTMX/Bootstrap via CDN)
✅ REST endpoints remain for API clients
✅ Thymeleaf fragments reusable with HTMX
✅ Progressive enhancement (works without JS)
✅ Minimal JavaScript code
✅ Server-side state management
✅ Easy to integrate with existing services

