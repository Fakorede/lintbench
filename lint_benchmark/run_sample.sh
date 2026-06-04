#!/usr/bin/env bash
# run_sample.sh — all 5 prompt variants × N models via OpenRouter
#
# Usage:
#   bash run_sample.sh [--model MODEL]... [--limit N] [--out DIR] [--benchmark PATH]
#
# --model can be repeated to override the default model list, e.g.:
#   bash run_sample.sh --model anthropic/claude-sonnet-4.6 --model google/gemini-2.5-flash

set -eo pipefail

# ---------------------------------------------------------------------------
# Defaults (override via flags or env)
# ---------------------------------------------------------------------------
LIMIT=""
OUT="generated"
BENCHMARK="data/dataset.jsonl"
RUN_ID=""

DEFAULT_MODELS=(
    "anthropic/claude-sonnet-4.6"
    "openai/gpt-5.5"
    "google/gemini-3.5-flash"
)

PROMPTS=(
    "zero_shot"
    "api_hint"
    "skeleton"
    "few_shot_surface_matched"
)

# ---------------------------------------------------------------------------
# Parse flags
# ---------------------------------------------------------------------------
MODELS=()
INSTANCE_IDS=()
while [[ $# -gt 0 ]]; do
    case $1 in
        --model)         MODELS+=("$2");       shift 2 ;;
        --limit)         LIMIT="$2";           shift 2 ;;
        --out)           OUT="$2";             shift 2 ;;
        --benchmark)     BENCHMARK="$2";       shift 2 ;;
        --run-id)        RUN_ID="$2";          shift 2 ;;
        --instance-id)   INSTANCE_IDS+=("$2"); shift 2 ;;
        --instance-file)
            while IFS= read -r line; do
                [[ -z "$line" || "$line" == \#* ]] && continue
                INSTANCE_IDS+=("$line")
            done < "$2"
            shift 2 ;;
        *) echo "Unknown flag: $1" >&2; exit 1 ;;
    esac
done

# Auto-generate a run ID from timestamp if not provided
if [[ -z "$RUN_ID" ]]; then
    RUN_ID=$(date -u +"%Y%m%dT%H%M%SZ")
fi
echo "Run ID: $RUN_ID"

# Fall back to default model list if none specified
[[ ${#MODELS[@]} -eq 0 ]] && MODELS=("${DEFAULT_MODELS[@]}")

LIMIT_ARGS=()
[[ -n "$LIMIT" ]] && LIMIT_ARGS=(--limit "$LIMIT")

INSTANCE_ARGS=()
for iid in "${INSTANCE_IDS[@]+"${INSTANCE_IDS[@]}"}"; do
    INSTANCE_ARGS+=(--instance-id "$iid")
done

# ---------------------------------------------------------------------------
# Run
# ---------------------------------------------------------------------------
TOTAL=$(( ${#MODELS[@]} * ${#PROMPTS[@]} ))
i=0

for MODEL in "${MODELS[@]}"; do
    for PROMPT in "${PROMPTS[@]}"; do
        i=$(( i + 1 ))
        echo ""
        echo "======================================================================"
        echo "[$i/$TOTAL] $MODEL / $PROMPT"
        echo "======================================================================"
        python run_inference.py \
            --model           "$MODEL" \
            --provider        openrouter \
            --prompt          "$PROMPT" \
            --benchmark       "$BENCHMARK" \
            --out             "$OUT" \
            --run-id          "$RUN_ID" \
            --max-tokens      32768 \
            --thinking-budget 10000 \
            "${LIMIT_ARGS[@]}" \
            "${INSTANCE_ARGS[@]}"
    done
done

echo ""
echo "======================================================================"
echo "All $TOTAL runs complete."
echo ""
python - "$OUT" "$RUN_ID" "${MODELS[@]}" <<'EOF'
import sys, json, glob, os
from pathlib import Path

out_dir  = sys.argv[1]
run_id   = sys.argv[2]
models   = sys.argv[3:]

for model in models:
    # generation logs live at <out>/<run_id>/<model>/<prompt>/generation_log.jsonl
    logs = glob.glob(str(Path(out_dir) / run_id / model / "*" / "generation_log.jsonl"))
    if not logs:
        continue
    in_tok = out_tok = cost = 0.0
    calls = 0
    for log in logs:
        for line in open(log):
            rec = json.loads(line)
            if not rec.get("success"):
                continue
            u = rec.get("usage", {})
            in_tok  += u.get("input_tokens", 0)
            out_tok += u.get("output_tokens", 0)
            cost    += rec.get("cost_usd", 0.0)
            calls   += 1
    print(f"  {model}")
    print(f"    calls={calls}  in={int(in_tok):,}  out={int(out_tok):,}  cost=${cost:.4f}")
EOF
