#!/usr/bin/env bash
# requires bash 3.2+ (macOS compatible)
# run_eval_all.sh — eval all generated provider/model/prompt directories
#
# Discovers every <generated>/<provider>/<model>/<prompt>/ directory and runs
# run_eval.py for each. Skips _repair* directories.
#
# Usage:
#   bash run_eval_all.sh [--generated DIR] [--benchmark PATH] [--results DIR]
#
# Defaults:
#   --generated  generated/
#   --benchmark  data/dataset.jsonl
#   --results    results/

set -eo pipefail

GENERATED="generated"
BENCHMARK="data/dataset.jsonl"
RESULTS="results"
RUN_ID=""
INSTANCE_ARGS=()
STRICT_FLAG=""

while [[ $# -gt 0 ]]; do
    case $1 in
        --generated)  GENERATED="$2";                        shift 2 ;;
        --benchmark)  BENCHMARK="$2";                        shift 2 ;;
        --results)    RESULTS="$2";                          shift 2 ;;
        --run-id)     RUN_ID="$2";                           shift 2 ;;
        --instance|--instance-id)   INSTANCE_ARGS+=(--instance "$2");      shift 2 ;;
        --instance-file)
            while IFS= read -r line; do
                [[ -z "$line" || "$line" == \#* ]] && continue
                INSTANCE_ARGS+=(--instance "$line")
            done < "$2"
            shift 2 ;;
        --strict)     STRICT_FLAG="--strict";                shift ;;
        *) echo "Unknown flag: $1" >&2; exit 1 ;;
    esac
done

# Auto-generate a run ID from timestamp if not provided
if [[ -z "$RUN_ID" ]]; then
    RUN_ID=$(date -u +"%Y%m%dT%H%M%SZ")
fi

OUT_DIR="${RESULTS}/${RUN_ID}"
LOG_DIR="${OUT_DIR}/logs"
mkdir -p "$OUT_DIR" "$LOG_DIR"
echo "Run ID : $RUN_ID"
echo "Results: $OUT_DIR"
echo "Logs   : $LOG_DIR"

# Collect all provider/model/prompt dirs, skip _repair*
DIRS=()
while IFS= read -r dir; do
    DIRS+=("$dir")
done < <(find "$GENERATED" -mindepth 3 -maxdepth 3 -type d ! -name '*_repair*' | sort)

TOTAL=${#DIRS[@]}
i=0
failed=()

for DIR in "${DIRS[@]}"; do
    i=$(( i + 1 ))

    # Extract provider/model/prompt from path: generated/<provider>/<model>/<prompt>
    RELATIVE="${DIR#${GENERATED}/}"   # e.g. anthropic/claude-sonnet-4.6/zero_shot
    PROVIDER=$(echo "$RELATIVE" | cut -d/ -f1)
    MODEL_SLUG=$(echo "$RELATIVE" | cut -d/ -f2)
    PROMPT=$(echo "$RELATIVE" | cut -d/ -f3)
    MODEL="${PROVIDER}/${MODEL_SLUG}"  # e.g. anthropic/claude-sonnet-4.6

    # Results filename: replace / with _ and strip special chars
    SAFE_MODEL=$(echo "$MODEL" | tr '/' '_')
    OUT="${OUT_DIR}/${SAFE_MODEL}_${PROMPT}.json"

    echo ""
    echo "======================================================================"
    echo "[$i/$TOTAL] $MODEL / $PROMPT"
    echo "  generated : $DIR"
    echo "  results   : $OUT"
    echo "  logs      : ${LOG_DIR}/${SAFE_MODEL}_${PROMPT}/"
    echo "======================================================================"

    python run_eval.py \
        --generated "$DIR" \
        --model     "$MODEL" \
        --prompt    "$PROMPT" \
        --benchmark "$BENCHMARK" \
        --out       "$OUT" \
        --build-env build_env/run.sh \
        --samples   1 \
        --log-dir   "${LOG_DIR}/${SAFE_MODEL}_${PROMPT}" \
        ${STRICT_FLAG} \
        "${INSTANCE_ARGS[@]}" || {
            echo "ERROR: eval failed for $MODEL / $PROMPT" >&2
            failed+=("$MODEL/$PROMPT")
        }
done

echo ""
echo "======================================================================"
echo "All $TOTAL evals complete."
if [[ ${#failed[@]} -gt 0 ]]; then
    echo "Failed:"
    for f in "${failed[@]}"; do echo "  $f"; done
fi
