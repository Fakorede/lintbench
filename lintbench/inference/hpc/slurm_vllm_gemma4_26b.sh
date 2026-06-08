#!/usr/bin/env bash
# slurm_vllm_gemma4_26b.sh
# SLURM job: launch a vLLM OpenAI-compatible server for Gemma 4-26B-A4B-IT
#
# Submit with:
#   sbatch slurm_vllm_gemma4_26b.sh
#
# Once READY:
#   bash hpc/run_hpc_inference.sh \
#     --model  gemma4-26b \
#     --host   <node> \
#     --port   8003 \
#     --prompt zero_shot
#
# GPU notes:
#   Gemma 4-26B-A4B is a MoE model. Total weights ~52 GB at bf16.
#   1×A100-80GB is sufficient; use 2 if memory is tight or for throughput.

#SBATCH --job-name=vllm-gemma4-26b
#SBATCH --account=loni_codelm2026
#SBATCH --partition=gpu2
#SBATCH --nodes=1
#SBATCH --ntasks=1
#SBATCH --gpus-per-node=1          # 1×A100-80GB fits the full weight matrix
#SBATCH --cpus-per-task=16
#SBATCH --time=12:00:00
#SBATCH --output=inference/hpc/logs/vllm_gemma4_26b_%j.log
#SBATCH --error=inference/hpc/logs/vllm_gemma4_26b_%j.log

set -eo pipefail

MODEL_ID="google/gemma-4-26B-A4B-it"
SERVED_MODEL_NAME="gemma4-26b"
VLLM_PORT="${VLLM_PORT:-8003}"
TENSOR_PARALLEL="${TENSOR_PARALLEL:-1}"
MAX_MODEL_LEN="${MAX_MODEL_LEN:-32768}"
GPU_UTIL="${GPU_UTIL:-0.92}"

mkdir -p inference/hpc/logs

echo "========================================"
echo "Job ID   : $SLURM_JOB_ID"
echo "Host     : $(hostname)"
echo "Model    : $MODEL_ID"
echo "Port     : $VLLM_PORT"
echo "GPUs     : $TENSOR_PARALLEL"
echo "========================================"

echo "$(hostname):${VLLM_PORT}" > inference/hpc/logs/vllm_gemma4_26b_endpoint.txt

module load cuda/12.2.1

source /usr/local/packages/conda/24.3.0/etc/profile.d/conda.sh
conda activate /work/mfakor1/.conda/envs/lintbench

# Use conda env's libstdc++ instead of the system one (too old on this cluster)
CONDA_LIB="/work/mfakor1/.conda/envs/lintbench/lib"
export LD_PRELOAD="${CONDA_LIB}/libstdc++.so.6"
export LD_LIBRARY_PATH="${CONDA_LIB}:${LD_LIBRARY_PATH:-}"

# Set CUDA_HOME for FlashInfer JIT compilation (module load sets CUDA_HOME automatically)
export CUDA_HOME="${CUDA_HOME:-/usr/local/packages/cuda/12.2.1}"
echo "CUDA_HOME : $CUDA_HOME"
echo "nvcc      : $(which nvcc)"

# Load HF_TOKEN from .env if not already set
if [[ -z "$HF_TOKEN" && -f "/work/mfakor1/lintbench/.env" ]]; then
    export $(grep -E '^HF_TOKEN=' /work/mfakor1/lintbench/.env | xargs)
fi

# Gemma 4 is a gated model — ensure HF_TOKEN is set
if [[ -z "$HF_TOKEN" ]]; then
    echo "ERROR: HF_TOKEN not set. Gemma 4 is a gated model; download will fail." >&2
    exit 1
fi

python -m vllm.entrypoints.openai.api_server \
    --model                  "$MODEL_ID" \
    --served-model-name      "$SERVED_MODEL_NAME" \
    --port                   "$VLLM_PORT" \
    --tensor-parallel-size   "$TENSOR_PARALLEL" \
    --max-model-len          "$MAX_MODEL_LEN" \
    --gpu-memory-utilization "$GPU_UTIL" \
    --trust-remote-code \
    --reasoning-parser       gemma4
# --reasoning-parser gemma4 strips <|think|>...</|think|> blocks from text
# output and exposes them as reasoning_content in the response.
