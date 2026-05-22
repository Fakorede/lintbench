# LintBench

A benchmark for evaluating LLMs on Android Lint detector generation. Each
instance pairs a natural language specification (extracted from
`Issue.create()` in the AOSP source) with the corresponding detector
implementation and its JUnit test suite for automated pass/fail evaluation.

## Dataset

439 benchmark instances drawn from the AOSP `tools/base` repository
(`mirror-goog-studio-main`, May 2026).

| Split  | Instances | Criteria |
|--------|-----------|----------|
| EASY   | 124       | Score 0–2: simple method-call matching, single scope |
| MEDIUM | 174       | Score 3–5: type-aware, multi-scope, light data-flow |
| HARD   | 141       | Score 6+: interprocedural, deep data-flow, CFG-level |

- **Languages:** 297 Kotlin · 142 Java
- **Categories:** CORRECTNESS (257), SECURITY (53), PERFORMANCE (40), ICONS (22), others
- **Evaluation:** pass@k — all `tests_to_run` methods must pass in the real Lint test harness

## Setup

```bash
# 1. Clone the AOSP lint source (sparse checkout, ~200MB)
git clone --no-checkout https://android.googlesource.com/platform/tools/base lint-codebase/base
cd lint-codebase/base
git sparse-checkout init --cone
git sparse-checkout set lint
git checkout mirror-goog-studio-main
cd ../..

# 2. Install Python dependencies and activate the virtualenv
uv sync
source lint_benchmark/.venv/bin/activate

# 3. Build the Docker evaluation image (one-time, ~5 min)
docker build -t lintbench-eval build_env/
```

## Project layout

```
lint_benchmark/
  curate/               Dataset construction pipeline (01–05)
  generate/             Model inferencing package
  eval/                 Evaluation package
  build_env/            Docker + Gradle compilation environment
  data/                 Benchmark artifacts (lintbench.json, intermediates)
  run_inference.py      Entry point: generate detector files from a model
  run_eval.py           Entry point: compile and test generated detectors
  pyproject.toml        uv/pip package config
```

## Pipeline

### Step 1 — Curate (already done; outputs are in `data/`)

Re-run only if updating to a newer AOSP branch.

```bash
python curate/01_pair_checks.py
python curate/02_extract_specs.py
python curate/03_audit_test_quality.py
python curate/04_stratify_difficulty.py
python curate/05_build_benchmark.py
```

Each script reads from `data/` and writes back to `data/`. Run from `lint_benchmark/` root.

### Step 2 — Generate

Send benchmark instances to an LLM and save generated detector files.

```bash
# Pass@1, greedy
python run_inference.py \
    --model  claude-sonnet-4-5 \
    --prompt zero_shot \
    --out    generated/

# Pass@5, with temperature
python run_inference.py \
    --model       gpt-4o \
    --prompt      zero_shot \
    --samples     5 \
    --temperature 0.8 \
    --out         generated/

# Via OpenRouter (access any model with one key)
python run_inference.py \
    --model    meta-llama/llama-3.3-70b-instruct \
    --provider openrouter \
    --out      generated/

# Restrict to one split or cap instances for testing
python run_inference.py --model gpt-4o --split easy --limit 10 --out generated/
```

**Prompt variants:** `zero_shot` · `few_shot` · `cot`

**API keys** (set whichever provider you use):
```bash
export OPENAI_API_KEY=...
export ANTHROPIC_API_KEY=...
export GOOGLE_API_KEY=...
export OPENROUTER_API_KEY=...
```

**Output layout:**
```
generated/
  <model>/<prompt>/
    <instance_id>/
      sample_0.kt
      sample_1.kt
    generation_log.jsonl
```

### Step 3 — Evaluate

Compile and test each generated detector inside the Docker harness.

```bash
python run_eval.py \
    --generated generated/claude-sonnet-4-5/zero_shot/ \
    --model     claude-sonnet-4-5 \
    --prompt    zero_shot \
    --out       results/sonnet_zero_shot.json \
    --build-env build_env/run.sh \
    --samples   1

# Stub mode (no Docker — for testing the pipeline)
python run_eval.py \
    --generated generated/claude-sonnet-4-5/zero_shot/ \
    --model     claude-sonnet-4-5 \
    --prompt    zero_shot \
    --out       results/test.json \
    --stub --stub-mode mixed
```

**Results schema** (`results/*.json`):

```
pass_at_1, pass_at_k, compilation_rate
by_difficulty: { EASY, MEDIUM, HARD } → { pass_at_1, compile_rate, n }
by_category:   { CORRECTNESS, … }    → { pass_at_1, n }
by_api_surface: { surface }           → { pass_at_1, n }
failure_distribution: { mode: count }
instances: [ per-instance detail with per-sample results ]
```

**Failure modes:** `pass` · `compilation_failed` · `wrong_imports` · `wrong_scanner` ·
`too_narrow` · `too_broad` · `wrong_logic` · `timeout` · `no_file`

## Pinned versions

| Component   | Version | Reason |
|-------------|---------|--------|
| Lint API    | 31.7.0  | Matches AOSP `mirror-goog-studio-main` at corpus construction time |
| Kotlin      | 1.9.20  | Dependency of `lint-tests` 31.7.0 |
| JUnit       | 4.13.2  | Dependency of `lint-tests` 31.7.0 |
| Java        | 17      | Minimum required by Lint 31.x |
| Gradle      | 8.6     | Compatible with JDK 17 + Kotlin 1.9 |
| Python      | 3.12    | |

To update Lint API version after re-running curation against a newer branch:
1. Update `lintVersion` in `build_env/gradle.properties`
2. Update `LINT_VERSION` in `build_env/Dockerfile`
3. Rebuild: `docker build -t lintbench-eval build_env/`

## License

Detector source files and test files are from the Android Open Source Project
and are licensed under **Apache 2.0**. Include attribution in any published
benchmark or dataset derived from this work.
