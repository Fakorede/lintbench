#!/usr/bin/env bash
# slurm_inference_qwen3_coder_30b.sh
# SLURM job: run LintBench inference against the qwen3-coder-30b vLLM server.
#
# Requires the vLLM server job to already be running:
#   sbatch inference/hpc/slurm_vllm_qwen3_coder_30b.sh
#
# Submit:
#   sbatch inference/hpc/slurm_inference_qwen3_coder_30b.sh

#SBATCH --job-name=lintbench-qwen3-coder-30b
#SBATCH --account=loni_codelm2026
#SBATCH --partition=single
#SBATCH --nodes=1
#SBATCH --ntasks=1
#SBATCH --cpus-per-task=4
#SBATCH --time=72:00:00
#SBATCH --output=inference/hpc/logs/inference_qwen3_coder_30b_%j.log
#SBATCH --error=inference/hpc/logs/inference_qwen3_coder_30b_%j.log

set -eo pipefail

mkdir -p inference/hpc/logs

echo "========================================"
echo "Job ID   : $SLURM_JOB_ID"
echo "Host     : $(hostname)"
echo "========================================"

source /usr/local/packages/conda/24.3.0/etc/profile.d/conda.sh
conda activate /work/mfakor1/.conda/envs/lintbench

cd /work/mfakor1/lintbench/lintbench

ENDPOINT=$(cat inference/hpc/logs/vllm_qwen3_coder_30b_endpoint.txt)
echo "vLLM endpoint: $ENDPOINT"

bash inference/hpc/run_hpc_inference.sh \
    --model       qwen3-coder-30b \
    --endpoint    "$ENDPOINT" \
    --prompt      zero_shot \
    --prompt      skeleton \
    --prompt      few_shot_surface_matched \
    --samples     5 \
    --temperature 0.6 \
    --run-id      qwen3_pass5_hpc_v1 \
    --out         generated/
