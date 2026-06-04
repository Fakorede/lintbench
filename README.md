# LintBench & LintGen

This repository contains two complementary research contributions for the study of LLM-based Android Lint detector generation.

| | [LintBench](#lintbench) | [LintGen](#lintgen) |
|---|---|---|
| **What** | Benchmark + evaluation harness | RAG-augmented generation pipeline |
| **Input** | Natural language Lint issue spec | Same benchmark instances |
| **Output** | pass@k, compile rate, failure modes | Generated detectors via retrieval-augmented prompting |
| **Key artefact** | `data/dataset.jsonl` — 156 validated instances | `api_hint_rag` prompt variant with dynamic Lint API retrieval |

---

## LintBench

> `lintbench/` · [full documentation](lintbench/README.md)

A benchmark for evaluating LLMs on Android Lint detector generation. Each instance pairs a natural-language specification (extracted from `Issue.create()` in the AOSP source) with the corresponding detector implementation and its JUnit test suite for automated pass/fail evaluation.

**156 validated instances** across easy / hard splits, evaluated inside a real Lint test harness running in Docker.

```bash
# Quick start
uv sync
docker build -t lintbench-eval lintbench/build_env/

# Run inference (any model via OpenRouter)
cd lintbench/
python run_inference.py --model anthropic/claude-sonnet-4.6 --prompt zero_shot --out generated/

# Evaluate
python run_eval.py \
    --generated generated/<run-id>/<model>/zero_shot/ \
    --build-env build_env/run.sh \
    --out       results/sonnet_zero_shot.json
```

**Prompt variants:** `zero_shot` · `api_hint` · `skeleton` · `few_shot_surface_matched`

**Failure modes tracked:** `compilation_failed` · `wrong_imports` · `wrong_scanner` · `too_narrow` · `message_mismatch` · `too_broad` · `wrong_logic` · `init_error`

---

## LintGen

> `lintgen/` · [full documentation](lintgen/README.md)

A RAG-augmented generation pipeline that retrieves relevant Lint API methods and import paths for each benchmark instance before prompting the model. Designed to directly address the most common LintBench failure modes — wrong imports, missing method overrides, wrong scanner interface.

**Embedding model:** `BAAI/bge-large-en-v1.5` (sentence-transformers)  
**Vector store:** FAISS (local, file-based — no hosted service required)  
**Two-tier retrieval:** hand-curated scanner interface methods (Tier 1) + full Lint API auto-extracted from source (Tier 2)

```bash
# Build the FAISS index once
lintgen build-index --source android-custom-lint-rules/

# RAG-augmented generation
lintgen generate --model anthropic/claude-sonnet-4.6 --prompt api_hint_rag --out generated/

# Ablation — same model, no RAG
lintgen generate --model anthropic/claude-sonnet-4.6 --prompt api_hint_rag --no-rag --out generated_norag/

# Evaluate (delegates to lintbench eval harness)
lintgen eval --generated generated/ --out results/
```

---

## Repository layout

```
lintbench/                  Benchmark dataset, inference pipeline, eval harness
  data/dataset.jsonl          156 validated instances
  inference/                  Prompt variants + LLM provider clients
    hpc/                      vLLM SLURM scripts + HPC inference runner
  eval/                       Compilation + test evaluation
  build_env/                  Docker + Gradle harness (Lint API 31.7.0)
  stub_generator/             JVM tool: generates minimal stubs from detector sources

lintgen/                    RAG-augmented generation (uv workspace member)
  src/lintgen/
    rag/                      FAISS knowledge base + corpus builder
      corpus/                 Hand-curated Tier 1 corpus (lint_interfaces.json)
      index/                  Persisted FAISS index (built locally, gitignored)
    inference/                Generation runner with RAG injection
    eval/                     Thin wrapper over lintbench eval harness
  tests/

android-custom-lint-rules/  Android Lint API source (Tier 2 index source)
pyproject.toml              uv workspace root (lintbench + lintgen members)
smoke_test.sh               End-to-end smoke test (no API keys or Docker required)
```

---

## Setup

```bash
# Clone
git clone <repo-url> && cd lintbench

# Install all dependencies (both lintbench and lintgen)
uv sync

# Build Docker eval image (lintbench)
docker build -t lintbench-eval lintbench/build_env/

# Build stub generator (lintbench)
cd lintbench/stub_generator && ./gradlew shadowJar && cd ../..

# Build FAISS index (lintgen) — run once, ~2 min on CPU
# Source defaults to base/lint/libs/lint-api/ (already cloned above)
lintgen build-index
```

**API keys** — set whichever provider you use:
```bash
export OPENAI_API_KEY=...
export ANTHROPIC_API_KEY=...
export GOOGLE_API_KEY=...
export OPENROUTER_API_KEY=...   # single key for all models via openrouter.ai
```

---

## Pinned versions

| Component | Version | Reason |
|---|---|---|
| Lint API | 31.7.0 | Matches AOSP `mirror-goog-studio-main` at corpus construction time |
| Kotlin | 1.9.20 | Dependency of `lint-tests` 31.7.0 |
| Java | 17 | Minimum required by Lint 31.x |
| Gradle | 8.6 | Compatible with JDK 17 + Kotlin 1.9 |
| Python | ≥ 3.12 | |

---

## License

Apache License 2.0 — see [LICENSE](LICENSE).  
Detector source files and test files are derived from the Android Open Source Project, also licensed under Apache 2.0.  
Copyright 2026 LintBench Authors.
