#!/usr/bin/env bash
# slurm_vllm_qwen25_32b.sh
# SLURM job: launch a vLLM OpenAI-compatible server for Qwen2.5-32B-Instruct
#
# Submit with:
#   sbatch slurm_vllm_qwen25_32b.sh
#
# Once READY:
#   bash hpc/run_hpc_inference.sh \
#     --model  qwen25-32b \
#     --port   8000 \
#     --prompt zero_shot

#SBATCH --job-name=vllm-qwen25-32b
#SBATCH --account=loni_codelm2026
#SBATCH --partition=gpu2
#SBATCH --nodes=1
#SBATCH --ntasks=1
#SBATCH --gpus-per-node=2          # Qwen2.5-32B fits in 2×A100-80GB (fp16)
#SBATCH --cpus-per-task=16
#SBATCH --time=72:00:00
#SBATCH --output=inference/hpc/logs/vllm_qwen25_32b_%j.log
#SBATCH --error=inference/hpc/logs/vllm_qwen25_32b_%j.log

set -eo pipefail

MODEL_ID="Qwen/Qwen2.5-32B-Instruct"
SERVED_MODEL_NAME="qwen25-32b"
VLLM_PORT="${VLLM_PORT:-8000}"
TENSOR_PARALLEL="${TENSOR_PARALLEL:-2}"
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

echo "$(hostname):${VLLM_PORT}" > inference/hpc/logs/vllm_qwen25_32b_endpoint.txt
echo "$(hostname):${VLLM_PORT}" > inference/hpc/logs/vllm_qwen25_32b_${SLURM_JOB_ID}_endpoint.txt

module load cuda/12.2.1

source /usr/local/packages/conda/24.3.0/etc/profile.d/conda.sh
conda activate /work/mfakor1/.conda/envs/lintbench

# Use conda env's libstdc++ instead of the system one (too old on this cluster)
CONDA_LIB="/work/mfakor1/.conda/envs/lintbench/lib"
export LD_PRELOAD="${CONDA_LIB}/libstdc++.so.6"
export LD_LIBRARY_PATH="${CONDA_LIB}:${LD_LIBRARY_PATH:-}"

# Set CUDA_HOME for FlashInfer JIT compilation (module load sets CUDA_HOME automatically)
export CUDA_HOME="${CUDA_HOME:-/usr/local/packages/cuda/12.2.1/jd2fi7s}"
echo "CUDA_HOME : $CUDA_HOME"
echo "nvcc      : $(which nvcc)"

# Load HF_TOKEN from .env if not already set
if [[ -z "$HF_TOKEN" && -f "/work/mfakor1/lintbench/.env" ]]; then
    export $(grep -E '^HF_TOKEN=' /work/mfakor1/lintbench/.env | xargs)
fi

python -m vllm.entrypoints.openai.api_server \
    --model                  "$MODEL_ID" \
    --served-model-name      "$SERVED_MODEL_NAME" \
    --port                   "$VLLM_PORT" \
    --tensor-parallel-size   "$TENSOR_PARALLEL" \
    --max-model-len          "$MAX_MODEL_LEN" \
    --gpu-memory-utilization "$GPU_UTIL" \
    --trust-remote-code
# Qwen2.5-32B-Instruct is a standard instruct model — no --reasoning-parser needed.
