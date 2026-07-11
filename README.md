Enterprise AI Knowledge Assistant (Skeleton)

Short project skeleton that demonstrates integrating Spring AI chat clients
with conditional configuration to run against OpenAI or a local LM Studio.

Quick notes
- Default LLM provider: `app.llm.provider` property in `src/main/resources/application.properties` (set to `lmstudio` in this repo)
- Local LM Studio endpoint: `http://127.0.0.1:1234/v1` (configured via `spring.ai.openai.base-url`)

How to build & run

```bash
cd "/Users/workspace/Desktop/workspace/Enterprise AI Knowledge Assistant/enterprise-ai-knowledge-assistant"
mvn -U clean package
java -jar target/enterprise-ai-knowledge-assistant-1.0.0-SNAPSHOT.jar
```

Or run from IntelliJ (Import `pom.xml`, reload Maven, then run `DemoApplication`).

Project structure (important files only)

```
enterprise-ai-knowledge-assistant/
├─ pom.xml
├─ README.md
├─ src/
│  ├─ main/
│  │  ├─ java/com/enterprise/ai/knowledge/assistant/demo/
│  │  │  ├─ DemoApplication.java        # Spring Boot entry
│  │  │  ├─ controller/ChatController.java
│  │  │  └─ config/ChatClientConfig.java # conditional ChatClient beans
│  │  └─ resources/
│  │     └─ application.properties
│  └─ test/
└─ target/
```

Architecture (simple ASCII diagram)

```
 Client (HTTP) --> ChatController (/api/chat) --> ChatClient (injected)
											  /\\
											 /  \\
							  ChatClientConfig/    \\ (conditional)
										   /        \\
								  OpenAI (remote)  LM Studio (http://127.0.0.1:1234)

 Other components: Spring Data JPA -> PostgreSQL (configured in application.properties)
```

Short troubleshooting
- If you get classpath/autoconfigure errors, ensure `pom.xml` Spring Boot version is compatible with `spring-ai` starter.
- If `mvn` command not found, install via Homebrew: `brew install maven`.

If you want, I can: update the POM to a different Spring Boot / spring-ai compatibility, add a health-check endpoint for LM Studio, or add a small integration test that runs against a local LM Studio mock.


