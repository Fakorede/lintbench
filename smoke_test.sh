#!/usr/bin/env bash
# smoke_test.sh
#
# End-to-end smoke test for LintBench.
#
# What it does:
#   1. Uses local lintbench.jsonl if present, otherwise downloads from HuggingFace
#   2. Runs inference with --stub (writes minimal placeholder detectors, no API calls)
#   3. Runs eval with --stub (fake pass/fail, no Docker)
#   4. [Optional] Runs eval with real Docker if --docker flag is passed
#
# Usage (from repo root):
#   bash smoke_test.sh            # stub eval only (no Docker required)
#   bash smoke_test.sh --docker   # also run real Docker eval on generated files
#
# Prerequisites:
#   uv sync && source .venv/bin/activate
#   For --docker: docker pull lintbench-eval  (or build_env/run.sh)

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LB="${REPO_ROOT}/lint_benchmark"
TMP="${REPO_ROOT}/.smoke"
HF_REPO="lintbench/lintbench"   # TODO: update to real HuggingFace repo path
RUN_DOCKER=false

for arg in "$@"; do
    [[ "${arg}" == "--docker" ]] && RUN_DOCKER=true
done

GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[0;33m'
NC='\033[0m'

pass()  { echo -e "${GREEN}✓ $*${NC}"; }
fail()  { echo -e "${RED}✗ $*${NC}"; exit 1; }
warn()  { echo -e "${YELLOW}! $*${NC}"; }

TOTAL_STEPS=3
${RUN_DOCKER} && TOTAL_STEPS=4

echo "=== LintBench smoke test ==="
${RUN_DOCKER} && echo "    (Docker eval enabled)"
echo

# ---------------------------------------------------------------------------
# 1. Use local benchmark if present, otherwise download from HuggingFace
# ---------------------------------------------------------------------------
BENCHMARK="${LB}/data/lintbench.jsonl"

if [[ -f "${BENCHMARK}" ]]; then
    echo "[1/${TOTAL_STEPS}] Using local benchmark: ${BENCHMARK}"
    pass "Benchmark found locally"
else
    echo "[1/${TOTAL_STEPS}] Downloading benchmark from HuggingFace (${HF_REPO})..."
    python3 - <<PYEOF
import shutil, pathlib, sys

try:
    from huggingface_hub import hf_hub_download
    from huggingface_hub.errors import RepositoryNotFoundError, EntryNotFoundError
except ImportError as e:
    print(f"ERROR: huggingface_hub not installed: {e}", file=sys.stderr)
    print("  Run: uv sync && source .venv/bin/activate", file=sys.stderr)
    sys.exit(1)

HF_REPO = "${HF_REPO}"   # bash-substituted
dest    = pathlib.Path("${BENCHMARK}")
dest.parent.mkdir(parents=True, exist_ok=True)

try:
    path = hf_hub_download(repo_id=HF_REPO, filename="lintbench.jsonl", repo_type="dataset")
    shutil.copy(path, dest)
    print(f"  Saved to {dest}")
except RepositoryNotFoundError:
    print(f"ERROR: HuggingFace repo '{HF_REPO}' not found.", file=sys.stderr)
    print("  The dataset has not been published yet.", file=sys.stderr)
    print("  Run the curate pipeline first to generate it locally:", file=sys.stderr)
    print("    python lint_benchmark/run_curate.py", file=sys.stderr)
    sys.exit(1)
except EntryNotFoundError:
    print(f"ERROR: 'lintbench.jsonl' not found in repo '{HF_REPO}'.", file=sys.stderr)
    sys.exit(1)
except Exception as e:
    print(f"ERROR: Download failed ({type(e).__name__}): {e}", file=sys.stderr)
    sys.exit(1)
PYEOF
    [[ -f "${BENCHMARK}" ]] || fail "lintbench.jsonl not found after download"
    pass "Benchmark downloaded"
fi

# ---------------------------------------------------------------------------
# 2. Run inference (stub — no API calls)
# ---------------------------------------------------------------------------
echo
echo "[2/${TOTAL_STEPS}] Running inference (stub mode, 5 instances)..."

rm -rf "${TMP}/generated"

python "${LB}/run_inference.py" \
    --benchmark "${BENCHMARK}" \
    --model     smoke-test-model \
    --prompt    zero_shot \
    --out       "${TMP}/generated" \
    --limit     5 \
    --stub

GENERATED_DIR="${TMP}/generated/smoke-test-model/zero_shot"
[[ -d "${GENERATED_DIR}" ]] || fail "Generated directory not created"

N_FILES=$(find "${GENERATED_DIR}" -name "sample_0.*" | wc -l | tr -d ' ')
[[ "${N_FILES}" -eq 5 ]] || fail "Expected 5 generated files, got ${N_FILES}"
pass "Inference produced ${N_FILES} detector files"

# ---------------------------------------------------------------------------
# 3. Run eval (stub — no Docker)
# ---------------------------------------------------------------------------
echo
echo "[3/${TOTAL_STEPS}] Running eval (stub mode, mixed pass/fail)..."

rm -rf "${TMP}/results"
mkdir -p "${TMP}/results"

python "${LB}/run_eval.py" \
    --benchmark "${BENCHMARK}" \
    --generated "${GENERATED_DIR}" \
    --model     smoke-test-model \
    --prompt    zero_shot \
    --out       "${TMP}/results/smoke_stub.json" \
    --limit     5 \
    --stub \
    --stub-mode mixed

RESULTS="${TMP}/results/smoke_stub.json"
[[ -f "${RESULTS}" ]] || fail "Results file not created"

python3 - <<PYEOF
import json, sys

with open("${RESULTS}") as f:
    r = json.load(f)

required = ["pass_at_1", "compilation_rate", "by_difficulty", "instances"]
missing  = [k for k in required if k not in r]
if missing:
    print(f"Missing keys in results: {missing}", file=sys.stderr)
    sys.exit(1)

n = len(r["instances"])
print(f"  {n} instances evaluated")
print(f"  pass@1 = {r['pass_at_1']:.1%}")
print(f"  compile_rate = {r['compilation_rate']:.1%}")
PYEOF

pass "Stub results JSON is valid"

# ---------------------------------------------------------------------------
# 4. [Optional] Run eval with real Docker
# ---------------------------------------------------------------------------
if ${RUN_DOCKER}; then
    echo
    echo "[4/${TOTAL_STEPS}] Running eval with Docker (real compilation + tests)..."

    BUILD_ENV="${LB}/build_env/run.sh"
    [[ -f "${BUILD_ENV}" ]] || fail "Build env script not found: ${BUILD_ENV}"

    if ! docker image inspect lintbench-eval &>/dev/null; then
        warn "Docker image 'lintbench-eval' not found — building now..."
        docker build -t lintbench-eval "${LB}/build_env/" \
            || fail "Docker build failed. See output above."
    fi

    python "${LB}/run_eval.py" \
        --benchmark "${BENCHMARK}" \
        --generated "${GENERATED_DIR}" \
        --model     smoke-test-model \
        --prompt    zero_shot \
        --out       "${TMP}/results/smoke_docker.json" \
        --build-env "${BUILD_ENV}" \
        --limit     5

    DOCKER_RESULTS="${TMP}/results/smoke_docker.json"
    [[ -f "${DOCKER_RESULTS}" ]] || fail "Docker results file not created"

    python3 - <<PYEOF
import json, sys

with open("${DOCKER_RESULTS}") as f:
    r = json.load(f)

required = ["pass_at_1", "compilation_rate", "by_difficulty", "instances"]
missing  = [k for k in required if k not in r]
if missing:
    print(f"Missing keys in results: {missing}", file=sys.stderr)
    sys.exit(1)

n = len(r["instances"])
print(f"  {n} instances evaluated via Docker")
print(f"  pass@1 = {r['pass_at_1']:.1%}")
print(f"  compile_rate = {r['compilation_rate']:.1%}")

failures = r.get("failure_distribution", {})
if failures:
    print(f"  failure breakdown: { {k: v for k, v in sorted(failures.items(), key=lambda x: -x[1])} }")
PYEOF

    pass "Docker results JSON is valid"
fi

# ---------------------------------------------------------------------------
# Done
# ---------------------------------------------------------------------------
echo
echo "=== Smoke test passed ==="
echo "  Stub results:   ${TMP}/results/smoke_stub.json"
${RUN_DOCKER} && echo "  Docker results: ${TMP}/results/smoke_docker.json"
echo "  Generated files: ${GENERATED_DIR}"
