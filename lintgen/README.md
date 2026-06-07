# LintGen

RAG-augmented Android Lint detector generation — the generation component of the LintBench benchmark.

LintGen wraps the [LintBench](../lintbench/) inference pipeline with a **Retrieval-Augmented Generation (RAG)** layer that retrieves relevant Lint API methods and import paths for each benchmark instance, and an **iterative repair loop** (`lintgen agent`) that compiles and tests each generated detector, feeding harness feedback back to the model for up to N repair rounds.

---

## How it works

```
Benchmark instance (nl_spec + scanner_interfaces)
        │
        ▼
  Embedding model          ← BAAI/bge-large-en-v1.5 (sentence-transformers)
  encodes the query
        │
        ▼
  Five-tier FAISS index    ← local, file-based index built from the Lint API corpus
  retrieves top-k docs
        │
        ▼
  RAG context injected     ← interface declarations + API signatures + imports
  into the prompt           (+ guide excerpts for base+apis+docs)
        │
        ▼
  LLM generates detector   ← any model via lintbench providers
        │
        ▼ (lintgen agent only)
  Build harness            ← Docker: compile + run original test suite
  returns pass/fail + errors
        │
        ├── all pass → done
        └── fail → error-augmented re-retrieval → repair prompt → LLM → repeat
```

### Vector store and embedding model

**FAISS** runs entirely in-process — the index is a set of files on disk (`rag/index/`) loaded at startup. No external database needed.

**`BAAI/bge-large-en-v1.5`** (via `sentence-transformers`) embeds the corpus at index-build time and each instance's `nl_spec + scanner_interfaces` at query time.

### Five-tier retrieval

| Tier | Corpus | Match strategy |
|---|---|---|
| **Tier 1** — Scanner interface index | Hand-curated interface method signatures (`lint_interfaces.json`, ~43 entries) | Exact-match on `scanner_interfaces`; semantic fallback |
| **Tier 2** — Full API index | Context utilities, `LintFix`, etc. (`lint_fullapi.json`, ~153 entries) | Dense FAISS retrieval, top-5, score ≥ 0.4 |
| **Tier 3** — API guide docs | 223 section chunks from the official Lint API guide | Dense FAISS retrieval |
| **Tier 4** — UAST/PSI method index | Method signatures auto-extracted from IntelliJ Community source | Dense FAISS retrieval |
| **Tier 5** — SdkConstants index | String constants used in ground-truth detectors | Dense FAISS retrieval |

Tiers 1–2 and 4–5 are included in all RAG prompt variants. Tier 3 (guide docs) is only added by `base+apis+docs`.

### Error-augmented re-retrieval (agent only)

Before each repair generation, the agent re-queries the RAG index with an **error-augmented query** rather than reusing the initial retrieval:

- **Compile failure** — unresolved symbol names (e.g. `cannot find symbol: ResourceFolderDetector`) are extracted from the compiler log and appended to the query, biasing retrieval toward the correct API signatures.
- **Test failure** (compiled but no warnings) — the scanner interface and scope constants used in the previous attempt are extracted from the generated code and appended, retrieving docs on the correct callback lifecycle and scope registration.

Each repair iteration gets only the new error-targeted context, not a stack of prior retrievals.

---

## Setup

```bash
# From the repo root
uv sync                        # install shared dependencies
uv pip install -e lintgen/     # install lintgen + its deps into the venv (editable)
source .venv/bin/activate      # activate so `lintgen` is on PATH

# Build the FAISS index (run once; re-run if corpus changes)
lintgen build-index
```

> **Note:** `uv sync` alone does not install the `lintgen` entry point — run
> `uv pip install -e lintgen/` once after cloning. Editable mode means code
> changes are picked up immediately without reinstalling.

**API keys** — put them in `/Users/researchlab/dev/research/lintbench/.env`
(loaded automatically at startup):

```bash
OPENROUTER_API_KEY=...   # covers all models with one key
# or provider-specific:
ANTHROPIC_API_KEY=...
OPENAI_API_KEY=...
GOOGLE_API_KEY=...
```

> **HPC note:** build the index on a login node (CPU-only, ~2 min), then copy `rag/index/` to your working directory before submitting jobs.

---

## Usage

All commands are run from the **repo root** (`/Users/researchlab/dev/research/lintbench/`).

### `lintgen generate` — single-shot generation

```bash
# All tiers — interface signatures + API methods + guide docs (default)
lintgen generate \
    --model  anthropic/claude-sonnet-4.6 \
    --prompt base+apis+docs

# Interface signatures + API methods only (no guide docs)
lintgen generate \
    --model  anthropic/claude-sonnet-4.6 \
    --prompt base+apis

# No RAG
lintgen generate \
    --model  anthropic/claude-sonnet-4.6 \
    --prompt zero_shot

# Specific instance
lintgen generate \
    --model       anthropic/claude-sonnet-4.6 \
    --prompt      base+apis+docs \
    --instance-id "LogDetector:LongLogTag"

# Pass@5
lintgen generate \
    --model       openai/gpt-4o \
    --samples     5 \
    --temperature 0.8 \
    --prompt      base+apis+docs

# Reasoning model with thinking budget
lintgen generate \
    --model           deepseek/deepseek-r1 \
    --thinking-budget 8000 \
    --prompt          base+apis+docs

# Ablation — disable RAG retrieval
lintgen generate \
    --model  anthropic/claude-sonnet-4.6 \
    --prompt base+apis+docs \
    --no-rag
```

### `lintgen agent` — iterative repair loop

Generates a detector, compiles and tests it via the Docker build harness, then feeds compile errors or test failure output back to the model for repair. Repeats up to `--max-iter` times. Tests are never shown to the model — only execution output is fed back. RAG context is re-retrieved at each repair iteration using an error-augmented query.

```bash
# Full dataset, all tiers (default)
lintgen agent \
    --model     anthropic/claude-sonnet-4.6 \
    --prompt    base+apis+docs \
    --build-env lintbench/build_env/run.sh

# Interface signatures + API methods only (no guide docs)
lintgen agent \
    --model     anthropic/claude-sonnet-4.6 \
    --prompt    base+apis \
    --build-env lintbench/build_env/run.sh

# No RAG
lintgen agent \
    --model     anthropic/claude-sonnet-4.6 \
    --prompt    zero_shot \
    --build-env lintbench/build_env/run.sh

# Single instance
lintgen agent \
    --model       anthropic/claude-sonnet-4.6 \
    --prompt      base+apis+docs \
    --build-env   lintbench/build_env/run.sh \
    --instance-id "WrongConstructorDetector:NotConstructor"

# Multiple instances
lintgen agent \
    --model       anthropic/claude-sonnet-4.6 \
    --prompt      base+apis+docs \
    --build-env   lintbench/build_env/run.sh \
    --instance-id "LogDetector:LongLogTag" \
    --instance-id "IconDetector:ConvertToWebp"

# Dry-run (no Docker — stub mode for testing the loop)
lintgen agent \
    --model    anthropic/claude-sonnet-4.6 \
    --prompt   base+apis+docs \
    --max-iter 3
```

**Trajectory output** (per instance, default `--out lintgen/generated/`):

```
lintgen/generated/<run_id>/<model>/<prompt>_agent/<instance_id>/
  iter_0.kt                ← initial generation
  iter_0_prompt.txt        ← exact prompt sent (system + user, including RAG context)
  iter_0_raw.txt           ← raw model response
  iter_0_logs/             ← Docker harness logs for this iteration
  iter_1.kt                ← first repair attempt
  iter_1_prompt.txt        ← repair prompt (error-augmented RAG + prev code + errors)
  ...
  final.kt                 ← best result (last passing, else last attempt)
  trajectory.jsonl         ← per-iteration record: compiled, tests_passed/failed, cost_usd
```

### `lintgen eval` — evaluate generated detectors

```bash
lintgen eval \
    --generated generated/<run_id>/<model>/<prompt>/ \
    --out        results/run.json
```

Delegates to `lintbench/run_eval_all.sh`.

---

## Prompt variants

| Variant | RAG tiers | Knowledge added | Paper condition |
|---|---|---|---|
| `zero_shot` | none | NL description only | C0 |
| `few_shot_surface_matched` | none | k worked Detector examples matched by scanner interface | C1 |
| `skeleton` | none | Pre-filled class skeleton (base class, scanner, Issue/Implementation boilerplate) | C2 |
| `base+apis` | 1, 2, 4, 5 | Interface declaration + scanner method signatures + Lint/UAST/PSI API methods + SdkConstants + required imports | C3 |
| `base+apis+docs` | 1, 2, 3, 4, 5 | All of the above + API guide doc excerpts (Tier 3) | C4 |
| `base+docs` | 3 | API guide doc excerpts only, top-k=20 | C5 |

**Tier breakdown:**
- **Tier 1** — Scanner interface declaration hint + method signatures (exact-match on `scanner_interfaces`)
- **Tier 2** — Full Lint API methods (`JavaContext`, `LintFix`, etc.)
- **Tier 3** — API guide doc excerpts (top-k=20 for `base+docs`; top-k=5 otherwise)
- **Tier 4** — UAST/PSI method signatures
- **Tier 5** — `SdkConstants` string values used in ground-truth detectors

---

## Project layout

```
lintgen/
├── pyproject.toml
└── src/lintgen/
    ├── cli.py                  # lintgen generate | agent | build-index | eval
    ├── agent/
    │   └── runner.py           # iterative repair loop with error-augmented re-retrieval
    ├── inference/
    │   └── runner.py           # single-shot generation with RAG injection
    ├── rag/
    │   ├── knowledge_base.py   # LintAPIKnowledgeBase — five-tier FAISS retrieval
    │   ├── build_corpus.py     # index builder (Tiers 1–5)
    │   ├── corpus/
    │   │   ├── lint_interfaces.json  # Tier 1: hand-curated interface methods
    │   │   └── lint_fullapi.json     # Tier 2: additional API entries
    │   └── index/              # persisted FAISS index (gitignored)
    └── eval/
        └── runner.py           # thin wrapper over lintbench/run_eval_all.sh
```

---

## Dependencies

| Package | Role |
|---|---|
| `faiss-cpu` | Local vector store — ANN search over embedded corpus |
| `sentence-transformers` | Loads `BAAI/bge-large-en-v1.5` for corpus and query embedding |
| `langchain-community` | `HuggingFaceEmbeddings` + `FAISS` wrappers |
| `openai` / `anthropic` / `google-generativeai` | LLM inference providers |
| `python-dotenv` | Loads `.env` from repo root at startup |
