#!/usr/bin/env bash
# run_kimi_baselines_eval.sh — evaluate moonshotai/kimi-k2.7-code:nitro generated detectors
#
# Usage (from lintbench/):
#   bash run_kimi_baselines_eval.sh

set -eo pipefail

MODEL="moonshotai/kimi-k2.7-code:nitro"
RUN_ID="kimi_pass5_v1"
BENCHMARK="data/dataset.jsonl"
BUILD_ENV="build_env/run.sh"
GENERATED="generated"
RESULTS="results/kimi_baselines"
SAMPLES=5
PROMPTS=(zero_shot skeleton few_shot_surface_matched)

mkdir -p "${RESULTS}"

total=${#PROMPTS[@]}
i=0
failed=()

for PROMPT in "${PROMPTS[@]}"; do
    i=$(( i + 1 ))
    GEN_DIR="${GENERATED}/${RUN_ID}/${MODEL}/${PROMPT}"
    OUT="${RESULTS}/${MODEL}_${PROMPT}.json"
    LOG_DIR="${RESULTS}/logs/${MODEL}_${PROMPT}"

    echo ""
    echo "======================================================================"
    echo "[$i/$total] ${MODEL} / ${PROMPT}"
    echo "  generated : ${GEN_DIR}"
    echo "  out       : ${OUT}"
    echo "======================================================================"

    python run_eval.py \
        --generated "${GEN_DIR}" \
        --model     "${MODEL}" \
        --prompt    "${PROMPT}" \
        --benchmark "${BENCHMARK}" \
        --out       "${OUT}" \
        --build-env "${BUILD_ENV}" \
        --samples   "${SAMPLES}" \
        --log-dir   "${LOG_DIR}" || {
            echo "ERROR: eval failed for ${MODEL} / ${PROMPT}" >&2
            failed+=("${PROMPT}")
        }
done

echo ""
echo "======================================================================"
echo "Done. ${total} evals complete."
if [[ ${#failed[@]} -gt 0 ]]; then
    echo "Failed:"
    for f in "${failed[@]}"; do echo "  $f"; done
fi
