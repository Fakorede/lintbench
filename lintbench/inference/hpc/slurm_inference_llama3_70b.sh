#!/usr/bin/env bash
# slurm_inference_llama3_70b.sh
# SLURM job: run LintBench inference against the llama3-70b-instruct vLLM server.
#
# Requires the vLLM server job to already be running:
#   sbatch inference/hpc/slurm_vllm_llama3_70b.sh
#
# Submit:
#   sbatch inference/hpc/slurm_inference_llama3_70b.sh

#SBATCH --job-name=lintbench-llama3-70b
#SBATCH --account=loni_codelm2026
#SBATCH --partition=single
#SBATCH --nodes=1
#SBATCH --ntasks=1
#SBATCH --cpus-per-task=4
#SBATCH --time=72:00:00
#SBATCH --output=inference/hpc/logs/inference_llama3_70b_%j.log
#SBATCH --error=inference/hpc/logs/inference_llama3_70b_%j.log

set -eo pipefail

mkdir -p inference/hpc/logs

echo "========================================"
echo "Job ID   : $SLURM_JOB_ID"
echo "Host     : $(hostname)"
echo "========================================"

source /usr/local/packages/conda/24.3.0/etc/profile.d/conda.sh
conda activate /work/mfakor1/.conda/envs/lintbench

cd /work/mfakor1/lintbench/lintbench

ENDPOINT=$(cat inference/hpc/logs/vllm_llama3_70b_endpoint.txt)
echo "vLLM endpoint: $ENDPOINT"

bash inference/hpc/run_hpc_inference.sh \
    --model       llama3-70b-instruct \
    --endpoint    "$ENDPOINT" \
    --prompt      zero_shot \
    --prompt      skeleton \
    --prompt      few_shot_surface_matched \
    --samples     5 \
    --temperature 0.6 \
    --run-id      llama3_pass5_hpc_v1 \
    --out         generated/
