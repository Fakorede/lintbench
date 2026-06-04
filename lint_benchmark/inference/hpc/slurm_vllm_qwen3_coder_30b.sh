#!/usr/bin/env bash
# slurm_vllm_qwen3_coder_30b.sh
# SLURM job: launch a vLLM OpenAI-compatible server for Qwen3-Coder-30B
#
# Submit with:
#   sbatch slurm_vllm_qwen3_coder_30b.sh
#
# The server listens on $VLLM_HOST:$VLLM_PORT (default 8000).
# Logs are written to logs/vllm_qwen3_coder_30b_<job_id>.log
#
# Once the server is READY, point the inference script at it:
#   bash hpc/run_hpc_inference.sh \
#     --model  Qwen/Qwen3-Coder-30B-A3B \
#     --port   8000 \
#     --prompt zero_shot

#SBATCH --job-name=vllm-qwen3-coder-30b
#SBATCH --nodes=1
#SBATCH --ntasks=1
#SBATCH --gpus-per-node=2          # Qwen3-Coder-30B fits in 2×A100-80GB (fp16)
#SBATCH --cpus-per-task=16
#SBATCH --mem=120G
#SBATCH --time=04:00:00
#SBATCH --output=logs/vllm_qwen3_coder_30b_%j.log
#SBATCH --error=logs/vllm_qwen3_coder_30b_%j.log
#SBATCH --partition=gpu            # adjust to your cluster's partition name

set -eo pipefail

# ---------------------------------------------------------------------------
# Configuration
# ---------------------------------------------------------------------------
MODEL_ID="Qwen/Qwen3-Coder-30B-A3B"
SERVED_MODEL_NAME="qwen3-coder-30b"
VLLM_PORT="${VLLM_PORT:-8000}"
TENSOR_PARALLEL="${TENSOR_PARALLEL:-2}"
MAX_MODEL_LEN="${MAX_MODEL_LEN:-32768}"
GPU_UTIL="${GPU_UTIL:-0.92}"

mkdir -p logs

echo "========================================"
echo "Job ID   : $SLURM_JOB_ID"
echo "Host     : $(hostname)"
echo "Model    : $MODEL_ID"
echo "Port     : $VLLM_PORT"
echo "GPUs     : $TENSOR_PARALLEL"
echo "========================================"

# Write the host/port so the inference script can read it
echo "$(hostname):${VLLM_PORT}" > logs/vllm_qwen3_coder_30b_endpoint.txt

# Activate your environment — adjust path as needed
# conda activate lintbench
# source /path/to/venv/bin/activate

python -m vllm.entrypoints.openai.api_server \
    --model                 "$MODEL_ID" \
    --served-model-name     "$SERVED_MODEL_NAME" \
    --port                  "$VLLM_PORT" \
    --tensor-parallel-size  "$TENSOR_PARALLEL" \
    --max-model-len         "$MAX_MODEL_LEN" \
    --gpu-memory-utilization "$GPU_UTIL" \
    --trust-remote-code \
    --enable-reasoning \
    --reasoning-parser      deepseek_r1 \
    --disable-log-requests
# --enable-reasoning + --reasoning-parser expose <think> content as
# reasoning_content in the response (vLLM >= 0.8.5).
# deepseek_r1 parser handles <think>...</think> and works for Qwen3 as well.
