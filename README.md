# AI Incident Copilot

A Spring Boot (Java 21) service that triages microservice incidents. Paste an alert and it:

1. **Redacts** secrets and PII (emails, IPs, JWTs, bearer tokens, AWS keys, `password=...`).
2. **Retrieves** similar past incidents with vector search (RAG).
3. **Investigates** with an LLM agent that calls Java tools: `getRecentDeployments`, `fetchServiceMetrics`, `queryLogs`.
4. **Validates** the answer (required fields, confidence clamped, cited incident ids must really exist) and returns a root-cause hypothesis, remediation steps, evidence and sources.

It runs **fully offline with zero keys** (local embeddings, rule-based analyst, in-memory store), and switches to real components purely through environment variables.

```mermaid
flowchart LR
    A[Alert] --> R[Redactor]
    R --> E[Embedding<br/>local or Gemini]
    E --> V[(Vector store<br/>memory or Atlas)]
    V --> G[Agent loop<br/>Groq / any OpenAI-compatible]
    G <--> T[Tools: deployments,<br/>metrics, logs]
    G --> C{Validator}
    C -->|ok| O[Answer + sources]
    C -->|invalid / LLM down| F[Offline analyst fallback]
    F --> O
```

## What is in it

| Area | Implementation |
|---|---|
| RAG | `EmbeddingService` + `VectorStore` interfaces. Implementations: local hashing / any OpenAI-compatible embeddings API; in-memory cosine / MongoDB Atlas `$vectorSearch` |
| Agent | Tool-calling loop against any OpenAI-compatible chat API (`OpenAiCompatLlmClient`), max iterations, JSON-only final answer |
| Tools | `ToolRegistry` with schema, argument clamping, service allow-list, redacted outputs. `TelemetryProvider` is an interface; the bundled one is **simulated** |
| Guardrails | `Redactor` before every LLM/embedding call and on tool output; `AnswerValidator` blocks invented citations |
| Resilience | Resilience4j circuit breaker around the LLM, HTTP timeouts, automatic fallback to the offline analyst |
| Observability | Micrometer metrics (`copilot.analysis.latency`, `copilot.llm.tokens`, `copilot.cache.hits`, `copilot.fallbacks`) at `/actuator/prometheus` |
| Caching | Caffeine cache for repeated alerts (30 min) |
| Streaming | SSE endpoint that streams pipeline stages, used by the built-in UI |
| Evaluation | `GET /api/eval` reports hit@1, hit@3 and MRR on a labelled alert set |
| Tests | Redactor/validator unit tests and a full end-to-end `@SpringBootTest` (offline mode) |

## Run locally

Requirements: JDK 21 and Maven 3.9+ (or just Docker).

```bash
# 1) offline, no keys
mvn spring-boot:run
# open http://localhost:8081

# 2) or with Docker
docker compose up --build
```

Run the tests: `mvn test`.

### API

```bash
curl -s localhost:8081/api/status

curl -s -X POST localhost:8081/api/analyze -H 'Content-Type: application/json' -d '{
  "service": "payment-service",
  "alert": "Checkout 500s. HikariPool-1 Connection is not available, request timed out after 30000ms"
}'

# add a resolved incident to the knowledge base
curl -s -X POST localhost:8081/api/incidents -H 'Content-Type: application/json' -d '{
  "title": "Thumbnail worker stuck on corrupt PNG", "service": "media-service", "severity": "SEV3",
  "symptoms": "Thumbnail queue stalls, worker loops forever decoding a file",
  "rootCause": "Decoder had no timeout", "resolution": "Added decode timeout. Quarantined the file."}'

curl -s localhost:8081/api/eval          # retrieval quality
curl -s localhost:8081/actuator/prometheus | grep copilot
```

Swagger UI is available at `http://localhost:8081/swagger-ui/index.html`; the OpenAPI specification is at
`http://localhost:8081/v3/api-docs`. After deployment, use the backend service URL (for example,
`https://<your-service>.onrender.com/swagger-ui/index.html`). The frontend domain `incidentpilot.in` serves
the static UI and does not proxy Swagger requests to the backend.

## React UI

The React UI is maintained separately in the `incident-copilot-frontend` project. Run this backend, then start the UI from that project's `frontend` directory with `npm ci` and `npm run dev`. The Vite development server proxies API requests to `http://localhost:8081`.

When hosting the UI separately, set `VITE_API_BASE_URL` in the UI project to this backend's public URL, then set `CORS_ALLOWED_ORIGINS` on the backend to the UI's exact origin (for example, `https://incident-copilot-ui.onrender.com`).

## Deploy for free

Free tiers change, so check each provider's current limits before relying on them.

You need three free accounts: **MongoDB Atlas** (database + vector search), **Groq** (LLM) and **Google AI Studio** (embeddings), plus **GitHub** and **Render** for hosting. All of them have free plans.

### 1. MongoDB Atlas (free M0 cluster)

1. Create an account at mongodb.com/atlas and deploy a **free M0** cluster.
2. *Database Access*: create a user with a strong password.
3. *Network Access*: add `0.0.0.0/0`. Render's free tier has no fixed outbound IP, so this is required. Keep the password strong.
4. *Connect > Drivers*: copy the connection string, e.g. `mongodb+srv://USER:PASSWORD@cluster0.xxxxx.mongodb.net/incident_copilot?retryWrites=true&w=majority`.

### 2. Free API keys

- **Groq**: console.groq.com, create an API key. Free tier is rate limited. Model names change, so pick a current tool-calling model from their model list and put it in `LLM_MODEL` (default: `llama-3.3-70b-versatile`).
- **Gemini embeddings**: aistudio.google.com, create an API key. The app uses Gemini's OpenAI-compatible endpoint with `gemini-embedding-001` and 768 dimensions.

Both are optional. Without them the app still works with the offline components, but the Gen AI part is then only simulated, so use real keys for your demo.

### 3. Push the code to GitHub

```bash
cd incident-copilot
git init && git add . && git commit -m "AI Incident Copilot"
git branch -M main
git remote add origin https://github.com/<you>/incident-copilot.git
git push -u origin main
```

### 4. Deploy on Render

1. render.com > **New > Blueprint** > select the repo. It reads `render.yaml` (Docker, free plan).
2. When prompted, fill in the secret variables: `MONGODB_URI`, `LLM_API_KEY`, `EMBEDDING_API_KEY`.
3. Wait for the first build (several minutes; the Docker build compiles the project).
4. Open `https://<your-service>.onrender.com`. Check `/api/status`: it should show your Groq model, the Gemini embedding model and `mongodb-atlas-vector-search`.

On first start the app embeds the 12 sample incidents and writes them to Atlas (this only happens when the collection is empty).

### 5. Create the Atlas Vector Search index (one time)

Atlas UI > your cluster > **Atlas Search** > **Create Search Index** > **Atlas Vector Search** > JSON editor:

- Database: `incident_copilot`, collection: `incidents`
- Index name: `incident_vector_index`
- Definition:

```json
{
  "fields": [
    { "type": "vector", "path": "embedding", "numDimensions": 768, "similarity": "cosine" }
  ]
}
```

Wait until the index status is **Active** (about a minute). `numDimensions` must equal `EMBEDDING_DIMENSIONS`. If you change the embedding model or dimensions, drop the collection, recreate the index and call `POST /api/seed`.

### 6. Verify

Open the UI, click a sample alert, then **Analyze**. Then click **Run retrieval evaluation** and note the numbers for your README and resume.

### 7. Free-tier behaviour to know about

- Render free web services **spin down after 15 minutes without traffic** and take about a minute to wake up. That is fine for a demo; open the link once before showing it.
- Render gives 750 free instance hours per month per workspace; free services have no persistent disk, which is why the incidents live in Atlas.
- To keep it warm you can ping `/actuator/health` every 10 minutes with a free uptime monitor, but that uses hours (one always-on service is about 744 hours in a 31-day month).
- Outbound SMTP ports are blocked on free Render services (not used here).

### Run the whole stack locally with a vector DB

```bash
cp .env.example .env     # fill in keys if you have them
STORE_TYPE=mongo docker compose --profile mongo up --build
```

`mongodb/mongodb-atlas-local` supports vector search locally. Create the same index in it with `mongosh` (`db.incidents.createSearchIndex("incident_vector_index", "vectorSearch", {fields:[...]})`).

## Keeping secrets safe

Secrets are never committed: `render.yaml` marks them `sync: false`, so you enter them once in the Render
dashboard (stored encrypted, injected as environment variables). `.env` is git-ignored. Actuator does not expose
the `env` endpoint and the API never returns keys.

To make the app read them from a secret store at startup instead, see [docs/SECRETS.md](docs/SECRETS.md): it
uses **AWS SSM Parameter Store** (standard tier, free) through an opt-in `-Paws` build and the `aws-ssm` Spring
profile. AWS Secrets Manager is deliberately not used because it is billed per secret.

## Configuration

| Variable | Default | Meaning |
|---|---|---|
| `LLM_API_KEY` | empty | Empty means the rule-based offline analyst |
| `LLM_BASE_URL` / `LLM_MODEL` | Groq / `llama-3.3-70b-versatile` | Any OpenAI-compatible chat API with tool calling |
| `EMBEDDING_API_KEY` | empty | Empty means the local hashing embedder |
| `EMBEDDING_BASE_URL` / `EMBEDDING_MODEL` / `EMBEDDING_DIMENSIONS` | Gemini / `gemini-embedding-001` / `768` | Any OpenAI-compatible embeddings API |
| `STORE_TYPE` | `memory` | `memory` or `mongo`; Mongo is used only when persistence is enabled |
| `MONGO_PERSISTENCE_ENABLED` | `true` | Set to `false` to keep incident data in temporary in-memory storage |
| `MONGODB_URI` | localhost | Atlas connection string |
| `PORT` | `8081` | Set by Render automatically |
| `CORS_ALLOWED_ORIGINS` | empty | Comma-separated exact UI origins allowed to call the API |

`MONGO_PERSISTENCE_ENABLED` is currently `true` for testing. Set it to `false` in the Render environment to keep incident data in temporary backend memory and avoid MongoDB storage usage; in-memory incidents are lost when the service restarts. Analysis responses are cached in memory and are not stored in MongoDB.

## Make it real (and stronger on a resume)

- Implement `TelemetryProvider` against Prometheus, Loki/Elasticsearch and your CI/CD API instead of `SimulatedTelemetryProvider`.
- Replace the sample incidents with real (sanitised) post-mortems and extend `eval/cases.json` with real alerts. Re-run `/api/eval` after every change to the embedding model or `top-k`.
- Add an ingestion path: an Alertmanager webhook that calls `POST /api/analyze`, or a Kafka consumer in front of `CopilotService`.

## Honest limitations

- The local embedder is **lexical** (hashed unigrams and bigrams), not semantic. The bundled evaluation set is small and written in the same vocabulary as the sample incidents, so its high score shows the pipeline works, not that retrieval is production-grade. Use the Gemini embeddings and your own eval cases for real numbers.
- Telemetry (deployments, metrics, logs) is **simulated** from `seed/telemetry.json`.
- LLM output quality depends on the model you configure. The validator guarantees structure and honest citations, not correctness.

## Resume bullets (fill in the real numbers from your own `/api/eval` run)

- Built an AI incident-triage service in Java 21 / Spring Boot that redacts PII, retrieves similar past incidents via MongoDB Atlas Vector Search and generates root-cause analysis with cited sources.
- Implemented a tool-calling LLM agent (OpenAI-compatible API) that queries deployments, metrics and logs through validated Java tools, with schema validation of model output and rejection of hallucinated citations.
- Added Resilience4j circuit breaking with an offline fallback analyst, Caffeine caching, SSE streaming, Micrometer metrics for latency/token usage, and a retrieval evaluation harness (hit@k, MRR).
- Deployed on free tiers (Render, MongoDB Atlas M0, Groq, Gemini) with Docker and a one-file Render blueprint.
