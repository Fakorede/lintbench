# LintGen

RAG-augmented Android Lint detector generation — the generation component of the LintBench benchmark contribution.

LintGen wraps the [LintBench](../lintbench/) inference pipeline with a **Retrieval-Augmented Generation (RAG)** layer that dynamically retrieves relevant Lint API methods and import paths for each benchmark instance before sending the prompt to the model.

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
  LLM generates detector   ← any model via lintbench providers / HPC vLLM
```

### Vector store and embedding model

**FAISS** is the vector store. It runs entirely in-process — the index is a pair of files on disk (`rag/index/tier1/`, `rag/index/tier2/`) loaded at startup. No external database or hosted service (Pinecone, Weaviate, Chroma, etc.) is needed.

**`BAAI/bge-large-en-v1.5`** (via `sentence-transformers`) is the embedding model. It is used twice:
1. **Index build time** (`lintgen build-index`) — embeds every entry in the corpus once and writes the FAISS index to disk. Only needs to run again if the corpus changes.
2. **Query time** (each `lintgen generate` call) — embeds the `nl_spec + scanner_interfaces` string for the current instance and runs an ANN search against the stored index.

### Two-tier retrieval

| Tier | Corpus | Size | Match strategy |
|---|---|---|---|
| **Tier 1** — Interface index | Hand-curated scanner interface methods (`rag/corpus/lint_interfaces.json`) | ~50 entries | Exact-match on `scanner_interfaces` first; semantic fallback |
| **Tier 2** — Full API index | Auto-extracted from `android-custom-lint-rules/` source + hand-written entries in `rag/corpus/lint_fullapi.json` | ~500–1 000 entries | Dense FAISS retrieval, top-5, score ≥ 0.4 |

Tier 1 targets the most common failure mode — wrong or missing scanner interface method overrides. Tier 2 covers `Context` methods, `LintFix`, `Location`, AST utilities, and import paths.

---

## Setup

```bash
# From the repo root — install lintgen and its dependencies into the workspace
uv sync

# Build the FAISS index (run once; re-run if corpus changes)
# Parses android-custom-lint-rules/ to auto-construct the Tier 2 index
cd lintbench/
lintgen build-index --source ../android-custom-lint-rules/ --out-dir ../lintgen/src/lintgen/rag/index/
```

> **HPC note:** build the index on a login node (no GPU needed — embedding runs on CPU in ~2 min for the full corpus), then copy `rag/index/` to your working directory before submitting inference jobs.

---

## Usage

```bash
# RAG-augmented generation (api_hint_rag prompt)
lintgen generate \
    --model  anthropic/claude-sonnet-4.6 \
    --prompt api_hint_rag \
    --out    generated/

# Ablation — same model, no RAG
lintgen generate \
    --model  anthropic/claude-sonnet-4.6 \
    --prompt api_hint_rag \
    --no-rag \
    --out    generated_norag/

# Evaluate (delegates to lintbench/run_eval_all.sh)
lintgen eval \
    --generated generated/ \
    --out        results/

# Smoke-test on 5 instances
lintgen generate --model <model> --limit 5
```

### Available prompt variants

| Variant | Description |
|---|---|
| `api_hint_rag` | RAG-retrieved Lint API context injected before the spec *(default)* |
| `api_hint` | Static API hint (no retrieval — lintbench baseline) |
| `zero_shot` | NL spec only |
| `skeleton` | Pre-filled class skeleton |
| `few_shot_surface_matched` | Scanner-matched worked example |

---

## Project layout

```
lintgen/
├── pyproject.toml
├── src/lintgen/
│   ├── cli.py                  # lintgen generate | build-index | eval
│   ├── inference/
│   │   └── runner.py           # generation loop with RAG injection
│   ├── rag/
│   │   ├── knowledge_base.py   # LintAPIKnowledgeBase — FAISS retrieval + format_context()
│   │   ├── build_corpus.py     # index builder (parses android-custom-lint-rules/)
│   │   ├── corpus/
│   │   │   ├── lint_interfaces.json  # Tier 1: hand-curated interface methods
│   │   │   └── lint_fullapi.json     # Tier 2: additional hand-written entries (optional)
│   │   └── index/              # persisted FAISS index — built locally, gitignored
│   └── eval/
│       └── runner.py           # thin wrapper over lintbench/run_eval_all.sh
└── tests/
    └── test_rag.py
```

---

## Development

```bash
uv sync --extra dev
uv run pytest tests/ -v
uv run ruff check src/
```

---

## Dependencies

| Package | Role |
|---|---|
| `faiss-cpu` | Local vector store — ANN search over embedded corpus |
| `sentence-transformers` | Loads `BAAI/bge-large-en-v1.5` for corpus and query embedding |
| `langchain-community` | `HuggingFaceEmbeddings` + `FAISS` wrappers |
| `openai` / `anthropic` / `google-generativeai` | LLM inference providers (shared with lintbench) |
