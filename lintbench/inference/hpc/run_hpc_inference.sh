#!/usr/bin/env bash
# run_hpc_inference.sh — run LintBench inference against a local vLLM server
#
# This script is the HPC counterpart of lintbench/run_sample.sh.
# It points the existing inference pipeline at an OpenAI-compatible vLLM
# endpoint instead of a cloud API.
#
# USAGE
# ─────────────────────────────────────────────────────────────────────────────
# # Run all prompt variants for one model:
#   bash hpc/run_hpc_inference.sh \
#     --model  qwen3-coder-30b \
#     --host   gpu-node-01 \
#     --port   8000
#
# # Override prompt list or limit instances:
#   bash hpc/run_hpc_inference.sh \
#     --model  llama3-70b-instruct \
#     --host   gpu-node-02 \
#     --port   8001 \
#     --prompt zero_shot \
#     --limit  10
#
# # Read host from the endpoint file written by the SLURM script:
#   ENDPOINT=$(cat hpc/logs/vllm_qwen3_coder_30b_endpoint.txt)
#   bash hpc/run_hpc_inference.sh --endpoint "$ENDPOINT" --model qwen3-coder-30b
#
# NOTES
# ─────────────────────────────────────────────────────────────────────────────
# - The model name passed here must match --served-model-name in the SLURM script.
# - No API key is needed for local vLLM (OPENAI_API_KEY defaults to "local").
# - Run this script from the lintbench/ directory.

set -eo pipefail

# ---------------------------------------------------------------------------
# Defaults
# ---------------------------------------------------------------------------
HOST="localhost"
PORT="8000"
ENDPOINT=""        # if set, overrides HOST:PORT
MODEL=""
LIMIT=""
OUT="generated"
BENCHMARK="data/dataset.jsonl"
RUN_ID=""
TEMPERATURE=""
MAX_TOKENS="32768"
DELAY="0.2"        # local server: no rate limits, short delay is fine
SAMPLES=""

PROMPTS=(
    "zero_shot"
    "api_hint"
    "skeleton"
    "few_shot_surface_matched"
)

INSTANCE_IDS=()
EXTRA_PROMPTS=()

# ---------------------------------------------------------------------------
# Parse flags
# ---------------------------------------------------------------------------
while [[ $# -gt 0 ]]; do
    case $1 in
        --model)        MODEL="$2";            shift 2 ;;
        --host)         HOST="$2";             shift 2 ;;
        --port)         PORT="$2";             shift 2 ;;
        --endpoint)     ENDPOINT="$2";         shift 2 ;;
        --prompt)       EXTRA_PROMPTS+=("$2"); shift 2 ;;
        --limit)        LIMIT="$2";            shift 2 ;;
        --out)          OUT="$2";              shift 2 ;;
        --benchmark)    BENCHMARK="$2";        shift 2 ;;
        --run-id)       RUN_ID="$2";           shift 2 ;;
        --temperature)  TEMPERATURE="$2";      shift 2 ;;
        --samples)      SAMPLES="$2";          shift 2 ;;
        --max-tokens)   MAX_TOKENS="$2";       shift 2 ;;
        --delay)        DELAY="$2";            shift 2 ;;
        --instance-id)  INSTANCE_IDS+=("$2");  shift 2 ;;
        --instance-file)
            while IFS= read -r line; do
                [[ -z "$line" || "$line" == \#* ]] && continue
                INSTANCE_IDS+=("$line")
            done < "$2"
            shift 2 ;;
        *) echo "Unknown flag: $1" >&2; exit 1 ;;
    esac
done

if [[ -z "$MODEL" ]]; then
    echo "ERROR: --model is required" >&2
    exit 1
fi

# Resolve endpoint
if [[ -n "$ENDPOINT" ]]; then
    HOST="${ENDPOINT%%:*}"
    PORT="${ENDPOINT##*:}"
fi
API_BASE="http://${HOST}:${PORT}/v1"

# If caller passed specific prompts, use those instead of the default list
[[ ${#EXTRA_PROMPTS[@]} -gt 0 ]] && PROMPTS=("${EXTRA_PROMPTS[@]}")

# Run ID
[[ -z "$RUN_ID" ]] && RUN_ID=$(date -u +"%Y%m%dT%H%M%SZ")
echo "Run ID    : $RUN_ID"
echo "Model     : $MODEL"
echo "API Base  : $API_BASE"
echo "Prompts   : ${PROMPTS[*]}"

# ---------------------------------------------------------------------------
# Wait for server to be ready (poll /health)
# ---------------------------------------------------------------------------
echo ""
echo "Waiting for vLLM server at $API_BASE ..."
MAX_WAIT=300   # seconds
WAITED=0
until curl -sf "${API_BASE%/v1}/health" > /dev/null 2>&1; do
    if [[ $WAITED -ge $MAX_WAIT ]]; then
        echo "ERROR: vLLM server did not become ready within ${MAX_WAIT}s" >&2
        exit 1
    fi
    sleep 5
    WAITED=$(( WAITED + 5 ))
    echo "  ... ${WAITED}s elapsed"
done
echo "Server ready."

# ---------------------------------------------------------------------------
# Build shared args
# ---------------------------------------------------------------------------
LIMIT_ARGS=()
[[ -n "$LIMIT" ]] && LIMIT_ARGS=(--limit "$LIMIT")

TEMP_ARGS=()
[[ -n "$TEMPERATURE" ]] && TEMP_ARGS=(--temperature "$TEMPERATURE")

SAMPLES_ARGS=()
[[ -n "$SAMPLES" ]] && SAMPLES_ARGS=(--samples "$SAMPLES")

INSTANCE_ARGS=()
for iid in "${INSTANCE_IDS[@]+"${INSTANCE_IDS[@]}"}"; do
    INSTANCE_ARGS+=(--instance-id "$iid")
done

# Local vLLM does not require a real key
export OPENAI_API_KEY="${OPENAI_API_KEY:-local}"

# ---------------------------------------------------------------------------
# Run
# ---------------------------------------------------------------------------
TOTAL=${#PROMPTS[@]}
i=0

for PROMPT in "${PROMPTS[@]}"; do
    i=$(( i + 1 ))
    echo ""
    echo "======================================================================"
    echo "[$i/$TOTAL] $MODEL / $PROMPT"
    echo "======================================================================"
    python run_inference.py \
        --model       "$MODEL" \
        --provider    openai \
        --api-base    "$API_BASE" \
        --prompt      "$PROMPT" \
        --benchmark   "$BENCHMARK" \
        --out         "$OUT" \
        --run-id      "$RUN_ID" \
        --max-tokens  "$MAX_TOKENS" \
        --delay       "$DELAY" \
        "${LIMIT_ARGS[@]}" \
        "${TEMP_ARGS[@]}" \
        "${SAMPLES_ARGS[@]}" \
        "${INSTANCE_ARGS[@]}"
done

echo ""
echo "======================================================================"
echo "All $TOTAL runs complete for $MODEL."
echo ""
echo "Next step — eval:"
echo "  bash lintbench/run_eval_all.sh \\"
echo "    --generated ${OUT}/${RUN_ID} \\"
echo "    --run-id    $RUN_ID"
