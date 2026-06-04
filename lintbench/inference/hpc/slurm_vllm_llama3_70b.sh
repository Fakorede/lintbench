#!/usr/bin/env bash
# slurm_vllm_llama3_70b.sh
# SLURM job: launch a vLLM OpenAI-compatible server for LLaMA 3-70B-Instruct
#
# Submit with:
#   sbatch slurm_vllm_llama3_70b.sh
#
# Once READY:
#   bash hpc/run_hpc_inference.sh \
#     --model  meta-llama/Meta-Llama-3-70B-Instruct \
#     --port   8001 \
#     --prompt zero_shot

#SBATCH --job-name=vllm-llama3-70b
#SBATCH --nodes=1
#SBATCH --ntasks=1
#SBATCH --gpus-per-node=4          # 70B needs 4×A100-80GB at fp16
#SBATCH --cpus-per-task=16
#SBATCH --mem=200G
#SBATCH --time=04:00:00
#SBATCH --output=logs/vllm_llama3_70b_%j.log
#SBATCH --error=logs/vllm_llama3_70b_%j.log
#SBATCH --partition=gpu

set -eo pipefail

MODEL_ID="meta-llama/Meta-Llama-3-70B-Instruct"
SERVED_MODEL_NAME="llama3-70b-instruct"
VLLM_PORT="${VLLM_PORT:-8001}"
TENSOR_PARALLEL="${TENSOR_PARALLEL:-4}"
MAX_MODEL_LEN="${MAX_MODEL_LEN:-8192}"
GPU_UTIL="${GPU_UTIL:-0.92}"

mkdir -p logs

echo "========================================"
echo "Job ID   : $SLURM_JOB_ID"
echo "Host     : $(hostname)"
echo "Model    : $MODEL_ID"
echo "Port     : $VLLM_PORT"
echo "GPUs     : $TENSOR_PARALLEL"
echo "========================================"

echo "$(hostname):${VLLM_PORT}" > logs/vllm_llama3_70b_endpoint.txt

# conda activate lintbench

# LLaMA 3 gated model — ensure HF_TOKEN is set in your environment
if [[ -z "$HF_TOKEN" ]]; then
    echo "WARNING: HF_TOKEN not set. LLaMA 3 is a gated model; download may fail." >&2
fi

python -m vllm.entrypoints.openai.api_server \
    --model                 "$MODEL_ID" \
    --served-model-name     "$SERVED_MODEL_NAME" \
    --port                  "$VLLM_PORT" \
    --tensor-parallel-size  "$TENSOR_PARALLEL" \
    --max-model-len         "$MAX_MODEL_LEN" \
    --gpu-memory-utilization "$GPU_UTIL" \
    --disable-log-requests
