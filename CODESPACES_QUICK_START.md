# Quick Start: GitHub Codespaces

## For First-Time Users

### 1️⃣ Set up OpenAI Secret (One-time)
```bash
# Go to: https://github.com/settings/codespaces
# Create secret: OPENAI_API_KEY = <your-openai-api-key>
```

### 2️⃣ Create Codespace
Click: **Code** → **Codespaces** → **Create codespace on main**

### 3️⃣ Wait for Setup
The dev container will automatically:
- Start PostgreSQL 16 + pgvector
- Download Maven dependencies
- Build the application

### 4️⃣ Access Application
```
http://localhost:8080
```

---

## Useful Commands

```bash
# View application logs
tail -f /tmp/spring-boot.log

# Rebuild application
mvn clean package

# Run tests
mvn test

# Check database connection
psql -h db -U workspace -d enterprise_ai

# See running services
docker ps
```

---

## Key Environment Variables

| Variable | Value | Set By |
|----------|-------|--------|
| `SPRING_PROFILES_ACTIVE` | `codespace` | Dev container |
| `OPENAI_API_KEY` | Your key | GitHub secret |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://db:5432/enterprise_ai` | Config |
| `APP_LLM_PROVIDER` | `openai` | Docker compose |

---

## API Endpoints

```bash
# Chat (with RAG context)
curl "http://localhost:8080/api/chat/rag?message=What%20is%20in%20the%20documents"

# Upload Document
curl -X POST http://localhost:8080/api/documents -F "file=@document.pdf"

# Simple Chat (no RAG)
curl "http://localhost:8080/api/chat?message=Hello"

# Health Check
curl http://localhost:8080/actuator/health
```

---

## Troubleshooting

### ❌ "OPENAI_API_KEY not found"
→ Create secret at https://github.com/settings/codespaces before creating Codespace

### ❌ "Cannot connect to database"
→ Wait 30s, check container: `docker ps -a`

### ❌ "Port 8080 already in use"
→ Restart Codespace or kill process: `kill -9 $(lsof -t -i :8080)`

### ❌ "pgvector not found"
→ Check init-db.sql ran: `docker exec enterprise-ai-db psql -U workspace -d enterprise_ai -c "CREATE EXTENSION IF NOT EXISTS vector"`

---

## For Local Development (Alternative)

If running locally with **LM Studio**:

```bash
# Start LM Studio on your machine
# Set environment variable
export LM_STUDIO_URL=http://your-ip:1234

# Run normally
mvn spring-boot:run
```

---

## Documentation Files

- **README.md** — Main project documentation
- **CODESPACES_IMPLEMENTATION.md** — Detailed setup guide
- **IMPLEMENTATION_VERIFICATION.md** — Verification checklist
- **plan-githubCodespacesSupport.prompt.md** — Original implementation plan

---

**Status:** ✅ Ready for GitHub Codespaces
**Profile:** codespace (auto-activated)
**LLM:** OpenAI (gpt-4o-mini)
**Database:** PostgreSQL 16 + pgvector

