#!/usr/bin/env bash
# slurm_vllm_deepseek_r1_32b.sh
# SLURM job: launch a vLLM OpenAI-compatible server for DeepSeek-R1-Distill-Qwen-32B
#
# Submit with:
#   sbatch slurm_vllm_deepseek_r1_32b.sh
#
# Once READY:
#   bash hpc/run_hpc_inference.sh \
#     --model  deepseek-ai/DeepSeek-R1-Distill-Qwen-32B \
#     --port   8002 \
#     --prompt zero_shot

#SBATCH --job-name=vllm-deepseek-r1-32b
#SBATCH --nodes=1
#SBATCH --ntasks=1
#SBATCH --gpus-per-node=2          # 32B fits in 2×A100-80GB at fp16
#SBATCH --cpus-per-task=16
#SBATCH --mem=120G
#SBATCH --time=04:00:00
#SBATCH --output=logs/vllm_deepseek_r1_32b_%j.log
#SBATCH --error=logs/vllm_deepseek_r1_32b_%j.log
#SBATCH --partition=gpu

set -eo pipefail

MODEL_ID="deepseek-ai/DeepSeek-R1-Distill-Qwen-32B"
SERVED_MODEL_NAME="deepseek-r1-32b"
VLLM_PORT="${VLLM_PORT:-8002}"
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

echo "$(hostname):${VLLM_PORT}" > logs/vllm_deepseek_r1_32b_endpoint.txt

# conda activate lintbench

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
# --enable-reasoning strips <think>...</think> from the text output and
# returns it separately as reasoning_content (vLLM >= 0.8.5).
