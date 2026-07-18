# Multi-Agent Workflow Orchestrator

A Gen AI multi-agent system built to go from React/JS + basic Java OOP to a full-stack
Gen AI Java developer — Spring Boot, gRPC microservices, Docker, Postgres, Redis,
security, and an LLM gateway (Portkey).

**The idea:** a user submits a goal (e.g. *"research X and draft a summary report"*).
A **planner** breaks it into a DAG of steps. A **worker** executes each step (LLM call
or tool call) through Portkey, with schema validation, retries, caching, and
response re-ranking. The client watches progress stream in.


---

## Architecture

```
┌─────────────┐        REST + SSE        ┌──────────────────────┐
│   React     │ ───────────────────────▶ │  Orchestrator Service │
│  Frontend   │ ◀─────────────────────── │  (Spring Boot)        │
└─────────────┘                          │  - Planning            │
                                          │  - Workflow/DAG state  │
                                          │  - Auth (JWT)          │
                                          └──────────┬─────────────┘
                                                      │ gRPC
                                                      ▼
                                          ┌──────────────────────┐
                                          │ Agent Execution Svc   │
                                          │ (Spring Boot)         │
                                          │  - LLM calls (Portkey)│
                                          │  - Tool calls          │
                                          │  - Eval / re-ranking   │
                                          └──────┬─────────┬──────┘
                                                 │         │
                                          ┌──────▼───┐ ┌───▼──────┐
                                          │ Postgres │ │  Redis    │
                                          │ (state)  │ │ (cache)   │
                                          └──────────┘ └───────────┘
```

Only two services, on purpose. Microservices earn their complexity when two things
scale or fail independently — that's true here for planning/state (Orchestrator) vs.
step execution (Agent Execution Service), which is CPU/IO-bound and benefits from
scaling separately. Resist adding a separate "Tool Registry" or "Eval" service until
there's real pain that justifies the split.

---

## Tech Stack

| Layer | Choice | Why |
|---|---|---|
| Language / runtime | Java 21, Spring Boot 3 | Modern Java, virtual threads for concurrent step execution |
| Inter-service comms | gRPC | Contract-first API between Orchestrator ↔ Agent Execution |
| Client-facing API | REST + SSE (Spring Web) | Simple for the React frontend, streams step-by-step progress |
| LLM gateway | Portkey | Multi-model routing, one client wraps `RestClient`/`WebClient` — no need for a heavier framework on top |
| Database | PostgreSQL | Durable, queryable workflow/step state — source of truth |
| Cache | Redis | Prompt-response cache + idempotency keys (not a queue — no Kafka needed at this scale) |
| Resilience | Resilience4j | Retries, circuit breakers, rate limiting |
| Security | Spring Security + JWT | Auth on the Orchestrator's REST API |
| Containers | Docker + Docker Compose | Postgres, Redis, both services — no Kubernetes needed here |
| Frontend | React | Plays to existing strength; renders SSE progress view |

---

## Low-Level Design

### Core data structures
```
Workflow { id, goal, status, planJson, createdAt }

Step {
  id, workflowId, type[PLAN|LLM_CALL|TOOL_CALL|AGGREGATE],
  dependsOn: List<StepId>,
  status[PENDING|RUNNING|SUCCEEDED|FAILED|SKIPPED],
  input, output, attempt, evalScore
}
```
Steps form a DAG. Execution order comes from a topological sort; independent steps
run concurrently via Java 21 virtual threads.

### API contracts
- **REST**
  - `POST /workflows` — submit a goal
  - `GET /workflows/{id}` — fetch workflow + step state
  - `GET /workflows/{id}/events` — SSE stream of step-by-step progress
- **gRPC** (`.proto`, contract-first)
  - `ExecuteStep(StepRequest) returns (StepResult)` — Orchestrator → Agent Execution Service

### Input schema validation
Every LLM output that feeds the next step (tool-call args, generated plan) must
validate against a JSON Schema before it's trusted. A schema failure is a distinct,
retryable-with-repair-prompt error — not a crash.

### Prompt templates
Versioned `PromptTemplate` records (id, version, template string, required
variables), rendered with simple string substitution and validated for missing
placeholders. No templating engine — unnecessary weight for this.

### Token limits
Simple chars/4 heuristic per-step budget, enforced before each call, with
truncation of long tool outputs feeding back into context. Only move to a real
tokenizer if the heuristic proves insufficient.

### Failure taxonomy

| Failure | Retryable? | Handling |
|---|---|---|
| Timeout / 5xx from Portkey | Yes, backoff + jitter | Resilience4j retry |
| Rate limited (429) | Yes, longer backoff | Circuit breaker per model |
| Malformed JSON output | Yes, once, with repair prompt | Re-prompt showing the error to the model |
| Schema-valid but wrong tool call | No | Fail step, surface to planner for re-plan |
| Budget/token limit exceeded | No | Fail step — no silent truncation-retry loop |
| Circular dependency in generated plan | No | Reject plan at validation time, before execution |

### Caching & retries
- Cache key = `hash(prompt + model + params)`
- Idempotency key per step execution, so a retried step never duplicates a
  side-effecting tool call (e.g. sending an email twice)

### Re-ranking / evals
For steps marked "critical" (e.g. final synthesis): generate 2–3 candidates, score
each with a cheap model as judge (a different model via Portkey, for an independent
check) plus a cheap heuristic (schema compliance, length sanity), and pick the
top-scored candidate. All scores are logged — this becomes the eval dataset later.

### Model tiering
Cheap/fast model for planning drafts and tool-arg generation; a stronger model
reserved for final synthesis and the eval/re-ranking judge. Keeps cost and latency
sane without sacrificing quality where it matters.

---

## Getting Started

> Prerequisites: Docker, Docker Compose, a Portkey API key with access to multiple models.

```bash
git clone <repo-url>
cd multi-agent-orchestrator
cp .env.example .env        # add your PORTKEY_API_KEY
docker compose up --build
```

Services:
- Orchestrator API: `http://localhost:8080`
- Agent Execution Service (gRPC): `localhost:9090`
- Postgres: `localhost:5432`
- Redis: `localhost:6379`

Submit a workflow:
```bash
curl -X POST http://localhost:8080/workflows \
  -H "Content-Type: application/json" \
  -d '{"goal": "Research X and draft a summary report"}'
```

---

## Build Roadmap (6–8 weeks)

1. **Skeleton** — Spring Boot boots, Postgres + Redis in Docker Compose, Workflow/Step CRUD
2. **Portkey integration** — client, prompt templates, token budgeting; one working single LLM call end-to-end
3. **Planner** — LLM generates a DAG (structured JSON, schema-validated), sequential topological executor
4. **gRPC split** — Agent Execution Service extracted, `.proto` defined, 2–3 real tools added
5. **Resilience** — Resilience4j retries/circuit breakers, Redis caching, idempotency, full failure taxonomy
6. **Evals** — multi-candidate generation + re-ranking for critical steps, scores logged
7. **Security** — Spring Security + JWT, rate limiting, secrets handling for the Portkey key
8. **Frontend + polish** — React SSE progress view, Docker Compose cleanup, architecture write-up

---

## Project Structure (planned)
```
.
├── orchestrator-service/       # Spring Boot — planning, workflow state, REST + SSE API
├── agent-execution-service/    # Spring Boot — LLM calls, tool calls, evals (gRPC server)
├── proto/                      # Shared .proto contracts
├── frontend/                   # React client
├── docker-compose.yml
└── README.md
```

---

## Non-goals

Deliberately left out to keep this a *learning-sized, solvable* system rather than
an over-engineered one:
- Kubernetes (Docker Compose is enough at this scale)
- Kafka / message queues (Redis covers caching + idempotency needs here)
- A separate Tool Registry or Eval microservice (modules inside Agent Execution
  Service until real pain justifies the split)
- A full tokenizer library (heuristic estimate first)
