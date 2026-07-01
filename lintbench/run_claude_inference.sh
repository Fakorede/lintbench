#!/usr/bin/env bash
# run_claude_inference.sh — generate claude-sonnet-4-6 predictions across 3 prompts
#
# Usage (from lintbench/):
#   bash run_claude_inference.sh

set -eo pipefail

MODEL="anthropic/claude-sonnet-4.6"
RUN_ID="claude_pass5_v1"
BENCHMARK="data/dataset.jsonl"
OUT="generated"
PROMPTS=(zero_shot skeleton few_shot_surface_matched)

total=${#PROMPTS[@]}
i=0
failed=()

for PROMPT in "${PROMPTS[@]}"; do
    i=$(( i + 1 ))
    echo ""
    echo "======================================================================"
    echo "[$i/$total] ${MODEL} / ${PROMPT}"
    echo "  out : ${OUT}/${RUN_ID}/${MODEL}/${PROMPT}/"
    echo "======================================================================"

    python -m inference \
        --model      "${MODEL}" \
        --prompt     "${PROMPT}" \
        --samples    5 \
        --run-id     "${RUN_ID}" \
        --benchmark  "${BENCHMARK}" \
        --out        "${OUT}" \
        --max-tokens 32768 || {
            echo "ERROR: inference failed for ${PROMPT}" >&2
            failed+=("${PROMPT}")
        }
done

echo ""
echo "======================================================================"
echo "Done. ${total} prompts attempted."
if [[ ${#failed[@]} -gt 0 ]]; then
    echo "Failed:"
    for f in "${failed[@]}"; do echo "  $f"; done
fi
echo ""
echo "Next: bash run_claude_baselines_eval.sh"
