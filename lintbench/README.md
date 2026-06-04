# LintBench

A benchmark for evaluating LLMs on Android Lint detector generation. Each
instance pairs a natural language specification (extracted from
`Issue.create()` in the AOSP source) with the corresponding detector
implementation and its JUnit test suite for automated pass/fail evaluation.

## Dataset

**156 validated instances** in `data/dataset.jsonl`, drawn from the AOSP `tools/base`
repository (`mirror-goog-studio-main`, May 2026) and filtered through a two-stage
validation pipeline (see [Dataset validation](#dataset-validation) below).

| Split  | Raw | Validated | Criteria |
|--------|-----|-----------|----------|
| easy   | 124 | 65        | Score 0–2: simple method-call matching, single scope |
| medium | 174 | 56        | Score 3–5: type-aware, multi-scope, light data-flow |
| hard   | 141 | 35        | Score 6+: interprocedural, deep data-flow, CFG-level |

- **Languages:** Kotlin · Java
- **Categories:** CORRECTNESS, SECURITY, PERFORMANCE, ICONS, others
- **Evaluation:** pass@k — all `tests_to_run` methods must pass in the real Lint test harness

`data/lintbench.jsonl` contains all 439 raw instances including those filtered out
during validation.

## Setup

```bash
# 1. Clone the AOSP lint source (sparse checkout, ~200MB)
git clone --no-checkout https://android.googlesource.com/platform/tools/base lint_codebase/base
cd lint_codebase/base
git sparse-checkout init --cone
git sparse-checkout set lint
git checkout mirror-goog-studio-main
cd ../..

# 2. Install Python dependencies and activate the virtualenv
uv sync
source .venv/bin/activate

# 3. Build the Docker evaluation image (one-time, ~5 min)
docker build -t lintbench-eval lintbench/build_env/

# 4. Build the stub generator (one-time, ~2 min)
cd lintbench/stub_generator && ./gradlew shadowJar && cd ../..
```

## Project layout

```
lintbench/
  curate/               Dataset construction pipeline (01–05)
  generate/             Model inferencing package
  eval/                 Evaluation package
  build_env/            Docker + Gradle compilation environment
    oracle_eval.py      Run real AOSP detectors as oracle to validate instances
    stub_eval.py        Run stubbed detectors to confirm tests have discriminating power
    src/oracle/         Staging dir for oracle detector files (per-instance)
    src/stub/           Staging dir for stub detector files (per-instance)
  stub_generator/       JVM tool: generates minimal stubs from real detector sources
    src/main/kotlin/
      StubGenerator.kt  Kotlin compiler PSI (.kt) + JavaParser (.java)
  data/
    lintbench.jsonl     All 439 raw instances
    dataset.jsonl       162 validated instances (oracle pass + stub fail)
  results/
    oracle/             oracle_eval results and per-instance logs
    stub/               stub_eval results and per-instance logs
  run_inference.py      Entry point: generate detector files from a model
  run_eval.py           Entry point: compile and test generated detectors
pyproject.toml          uv/pip package config (repo root)
```

## Pipeline

### Step 1 — Curate (outputs are saved in `data/`)

Re-run only if updating to a newer AOSP branch.

```bash
# Run the full pipeline
python lintbench/run_curate.py

# Skip steps whose output already exists
python lintbench/run_curate.py --skip-existing

# Run only specific steps (1–5)
python lintbench/run_curate.py --only 2 3
```

Each step reads from `data/` and writes back to `data/`. Final outputs:
- `data/lintbench.jsonl` — one instance per line; used by generate and eval
- `data/lintbench.json` — full dataset including excluded instances and metadata

### Dataset validation

Two validation passes filter `lintbench.jsonl` down to `dataset.jsonl`.
Run these if re-curating from a newer AOSP branch; the outputs are already
committed for the current corpus.

**Oracle eval** — runs the real AOSP detector source through the harness.
Instances where the real implementation fails to compile or pass its own tests
are not valid benchmark entries.

```bash
python3 lintbench/build_env/oracle_eval.py --workers 4 --timeout 180
# Results → lintbench/results/oracle/oracle_results.json
# Passing instances → lintbench/data/dataset.jsonl
```

| Status        | Count | Meaning |
|---------------|-------|---------|
| `pass`        | 160   | Real detector compiles and all targeted tests pass → carried forward |
| `test_fail`   | 45    | Tests fail even with the real implementation (stub/API gap) |
| `compile_fail`| 233   | Real detector uses AOSP-internal APIs not in the Maven artifact |

**Stub eval** — replaces each detector with a minimal stub (correct class
structure, `Issue` declarations intact, all method bodies emptied) and re-runs
the tests. Tests must fail with a stub; a passing test has no discriminating
power and the instance is dropped from `dataset.jsonl`.

```bash
python3 lintbench/build_env/stub_eval.py --workers 4 --timeout 180
# Results → lintbench/results/stub/stub_results.json
# dataset.jsonl updated in-place (passing-stub instances removed)
```

The stub generator (called automatically by `stub_eval.py`) uses the Kotlin
compiler's own PSI for `.kt` files and JavaParser for `.java` files, so stubs
are syntactically faithful to the original — companion objects and `Issue`
declarations are preserved verbatim while all method bodies are replaced with
typed no-op stubs.

### Step 2 — Rn Inference

Send benchmark instances to an LLM and save generated detector files.

```bash
# dry run
python run_inference.py \
    --model  claude-sonnet-4-5 \
    --prompt zero_shot \
    --stub \
    --limit  5 \
    --out    generated/

# generate sample prompts
python3 -c "
from inference.prompts import build_prompt
import json

inst = json.loads(open('data/dataset.jsonl').readline())
sys_p, usr_p = build_prompt(inst, 'api_hint')
print(usr_p)
"

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

**Prompt variants:** `zero_shot` · `api_hint` · `skeleton` · `few_shot_surface_matched` · `few_shot_surface_matched_cot` · `compile_repair_1`

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

## Smoke test

```bash
bash smoke_test.sh            # no API keys or Docker required
bash smoke_test.sh --docker   # also run real compilation via Docker
```

Steps run automatically:

1. **Benchmark** — uses `data/lintbench.jsonl` if present locally, otherwise downloads from HuggingFace
2. **Inference stub** — writes minimal placeholder detectors for 5 instances, no API calls
3. **Eval stub** — fake pass/fail without Docker, validates results JSON
4. **Docker eval** (`--docker` only) — compiles and tests the 5 generated detectors in the real build harness; builds the image automatically if not found

Results land in `.smoke/results/`:
- `smoke_stub.json` — stub eval output
- `smoke_docker.json` — Docker eval output (with `--docker`)

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
3. Rebuild: `docker build -t lintbench-eval lintbench/build_env/`

## License

This project is licensed under the **Apache License 2.0** — see [LICENSE](LICENSE) for details.

Detector source files and test files are derived from the Android Open Source Project,
also licensed under Apache 2.0. 

Copyright 2026 LintBench Authors.
