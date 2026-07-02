# Support Triage Graph

A Spring AI + Claude customer-support triage service built as an **Alibaba StateGraph**
and driven entirely over a **REST API**. A request is classified, routed to the right
support desk, and its answer is checked by an LLM judge that either accepts it, **loops**
back for another attempt, or **escalates** to a human. If the category is unknown, the
graph **pauses for a human** and resumes on a second HTTP call.

This is a learning project that applies all three Spring AI graph-workflow recipes at once:

- **Graph-based workflow** — nodes + conditional edges
- **Human-in-the-loop** — `interruptBefore` a node, resumed over REST
- **Loop** — a bounded retry cycle driven by a resolution check

```
POST /api/tickets  "I was double charged"
  START -> Classify --billing--> Billing Support --> Check Resolution
                                                        |  resolved -> END
                                                        |  needs revision (attempts<3) -> back to desk   (LOOP)
                                                        |  not resolved (attempts>=3) -> Escalate -> END
        Classify --unknown--> [interrupt] Human Classification   (PAUSE -> resume via 2nd HTTP call)
```

## Module

Single-module Maven project.

| Item     | Value                                                        |
|----------|--------------------------------------------------------------|
| Port     | 8082                                                         |
| Package  | `com.example.supporttriagegraph`                            |
| Graph    | `com.alibaba.cloud.ai.graph.StateGraph` (compiled singleton) |

### Graph nodes (`.../graph`)

Each node implements `NodeAction` (`apply(OverAllState) -> Map` of state deltas) and is a `@Component`.

| Node                          | Role                                                              |
|-------------------------------|-------------------------------------------------------------------|
| `ClassifySupportRequestNode`  | Claude classifies the request: `billing` / `technical` / `unknown`|
| `HumanClassificationNode`     | Placeholder; graph is paused just before it (`interruptBefore`)   |
| `BillingSupportNode`          | Drafts a billing answer, using prior `feedback` on retries        |
| `TechnicalSupportNode`        | Drafts a technical answer, using prior `feedback` on retries      |
| `CheckResolutionNode`         | LLM judge: sets `resolved` + `feedback`, increments `attempts`    |
| `EscalateToHumanNode`         | Builds a `finalResponse` handoff after retries are exhausted      |

### Graph state keys

Registered with `KeyStrategy` (all `ReplaceStrategy`):
`user_question`, `category`, `response`, `feedback`, `attempts`, `resolved`, `escalated`, `finalResponse`.

## Tech stack

- Java 25
- Spring Boot 4.0.5
- Spring AI 2.0.0-M5 (`spring-ai-starter-model-anthropic`)
- Spring AI Alibaba Graph 1.1.2.2 (`spring-ai-alibaba-graph-core`)
- Anthropic Claude Haiku (`claude-haiku-4-5-20251001`)

## Prerequisites

- JDK 25
- An Anthropic API key

```bash
export ANTHROPIC_API_KEY=sk-ant-...
```

## Running

```bash
mvn spring-boot:run
```

The app starts on `http://localhost:8082`.

## Usage

The whole workflow is three endpoints.

### 1. Submit a request

```bash
curl -X POST http://localhost:8082/api/tickets \
  -H "Content-Type: application/json" \
  -d '{"question":"I was double charged on my invoice"}'
```

A clear request runs to completion in one call (branching + loop happen internally):

```json
{ "ticketId": "42e6...", "status": "RESOLVED", "category": "billing", "attempts": 3,
  "response": "Thank you for bringing this to our attention ..." }
```

### 2. Human-in-the-loop (vague request)

A vague request classifies as `unknown`, so the graph **pauses**:

```bash
curl -X POST http://localhost:8082/api/tickets \
  -H "Content-Type: application/json" \
  -d '{"question":"hello, something is wrong"}'
```

```json
{ "ticketId": "3281...", "status": "NEEDS_CLASSIFICATION", "category": "unknown",
  "attempts": 0, "response": null }
```

Resume it by supplying the human category (`billing` or `technical`):

```bash
curl -X POST http://localhost:8082/api/tickets/3281.../classify \
  -H "Content-Type: application/json" \
  -d '{"category":"technical"}'
```

The graph continues from where it paused and runs to `RESOLVED` or `ESCALATED`.

### 3. Check a ticket

```bash
curl http://localhost:8082/api/tickets/3281...
```

### Statuses

| Status                 | Meaning                                                    |
|------------------------|------------------------------------------------------------|
| `RESOLVED`             | A support desk answer passed the resolution check          |
| `ESCALATED`            | Retries exhausted (`attempts >= 3`) — handed to a human     |
| `NEEDS_CLASSIFICATION` | Paused at the human interrupt; call `/classify` to resume  |

## How REST human-in-the-loop works

The interesting part: the recipes drive the interrupt from a console loop, but here it is
split across two HTTP requests. The **ticket id is the graph `threadId`**.

- **Submit** → `graph.invoke(Map.of("user_question", q), RunnableConfig.builder().threadId(id).build())`.
  If the returned `category` is `unknown`, the graph paused before the human node.
- **Resume** → `graph.getState(cfgWithThreadId).config().withResume()`, then
  `graph.updateState(resumeCfg, Map.of("category", cat), "human-classify")`, then
  `graph.invoke(Map.of(), resumeCfg)` to run to the end.

The compiled graph is a singleton bean with a built-in in-memory checkpoint store keyed by
`threadId`, so the paused state survives between the two calls. (Paused tickets are lost on
restart — acceptable for a learning project; a persistent saver would fix that.)

