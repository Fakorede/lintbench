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
  FAISS vector store       ← local, file-based index built from the Lint API corpus
  retrieves top-k docs
        │
        ▼
  RAG context injected     ← API method signatures + required imports
  into the prompt
        │
        ▼
  LLM generates detector   ← any model via lintbench providers
        │
        ▼ (lintgen agent only)
  Build harness            ← Docker: compile + run original test suite
  returns pass/fail + errors
        │
        ├── all pass → done
        └── fail → repair prompt (last code + error feedback) → LLM → repeat
```

### Vector store and embedding model

**FAISS** runs entirely in-process — the index is a pair of files on disk (`rag/index/`) loaded at startup. No external database needed.

**`BAAI/bge-large-en-v1.5`** (via `sentence-transformers`) embeds the corpus at index-build time and each instance's `nl_spec + scanner_interfaces` at query time.

### Two-tier retrieval

| Tier | Corpus | Match strategy |
|---|---|---|
| **Tier 1** — Interface index | Hand-curated scanner interface methods (`rag/corpus/lint_interfaces.json`) | Exact-match on `scanner_interfaces`; semantic fallback |
| **Tier 2** — Full API index | Auto-extracted from AOSP + hand-written entries (`rag/corpus/lint_fullapi.json`) | Dense FAISS retrieval, top-5, score ≥ 0.4 |

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
# RAG-augmented (default)
lintgen generate \
    --model  anthropic/claude-sonnet-4.6 \
    --prompt api_hint_rag \
    --out    lintgen/generated/

# Specific instance only
lintgen generate \
    --model       anthropic/claude-sonnet-4.6 \
    --prompt      api_hint_rag \
    --instance-id "LogDetector:LongLogTag" \
    --out         generated/

# Pass@5
lintgen generate \
    --model       openai/gpt-4o \
    --samples     5 \
    --temperature 0.8 \
    --prompt      api_hint_rag \
    --out         generated/

# Reasoning model with thinking budget
lintgen generate \
    --model           deepseek/deepseek-r1 \
    --thinking-budget 8000 \
    --prompt          api_hint_rag \
    --out             generated/

# Ablation — no RAG
lintgen generate \
    --model  anthropic/claude-sonnet-4.6 \
    --prompt api_hint_rag \
    --no-rag \
    --out    lintgen/generated_norag/
```

### `lintgen agent` — iterative repair loop

Generates a detector, compiles and tests it via the Docker build harness, then feeds compile errors or test failure output back to the model for repair. Repeats up to `--max-iter` times. Tests are never shown to the model — only execution output is fed back.

```bash
# Full dataset, up to 10 repair rounds per instance
lintgen agent \
    --model     anthropic/claude-sonnet-4.6 \
    --prompt    zero_shot \
    --build-env lintbench/build_env/run.sh

# Single instance
lintgen agent \
    --model       anthropic/claude-sonnet-4.6 \
    --prompt      zero_shot \
    --build-env   lintbench/build_env/run.sh \
    --instance-id "LogDetector:LongLogTag"

# Multiple instances
lintgen agent \
    --model       anthropic/claude-sonnet-4.6 \
    --prompt      few_shot_surface_matched \
    --build-env   lintbench/build_env/run.sh \
    --instance-id "LogDetector:LongLogTag" \
    --instance-id "IconDetector:ConvertToWebp"

# Custom output dir
lintgen agent \
    --model     anthropic/claude-sonnet-4.6 \
    --prompt    zero_shot \
    --build-env lintbench/build_env/run.sh \
    --out       lintgen/generated/

# Dry-run (no Docker — stub mode for testing the loop)
lintgen agent \
    --model    anthropic/claude-sonnet-4.6 \
    --prompt   zero_shot \
    --max-iter 3
```

**Trajectory output** (per instance, default `--out lintgen/generated/`):

```
lintgen/generated/<run_id>/<model>/zero_shot_agent/<instance_id>/
  iter_0.kt              ← initial generation
  iter_0_prompt.txt      ← exact prompt sent (system + user)
  iter_0_raw.txt         ← raw model response
  iter_1.kt              ← first repair attempt
  ...
  final.kt               ← best result (last passing, else last attempt)
  trajectory.jsonl       ← per-iteration record: compiled, tests_passed/failed, cost_usd
  <test logs>            ← Docker harness logs copied here (LINTBENCH_LOG_DIR)
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

| Variant | Knowledge added | Paper condition |
|---|---|---|
| `zero_shot` | NL description only | C0 |
| `few_shot_surface_matched` | k worked Detector examples matched by scanner interface | C1 |
| `skeleton` | Pre-filled class skeleton (base class, scanner, Issue/Implementation boilerplate) | C2 |
| `api_hint_rag` | RAG-retrieved Lint/UAST/PSI API signatures + doc comments | C3 |

---

## Project layout

```
lintgen/
├── pyproject.toml
└── src/lintgen/
    ├── cli.py                  # lintgen generate | agent | build-index | eval
    ├── agent/
    │   └── runner.py           # iterative repair loop with trajectory saving
    ├── inference/
    │   └── runner.py           # single-shot generation with RAG injection
    ├── rag/
    │   ├── knowledge_base.py   # LintAPIKnowledgeBase — FAISS retrieval + format_context()
    │   ├── build_corpus.py     # index builder (Tiers 1–5)
    │   ├── corpus/
    │   │   ├── lint_interfaces.json  # Tier 1: hand-curated interface methods
    │   │   └── lint_fullapi.json     # Tier 2: additional hand-written entries
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
