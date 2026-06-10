## clone repo

```sh
git clone https://github.com/Fakorede/lintbench.git
cd lintbench
```

## create conda environment

```sh
conda create -n lintbench python=3.12 -y
conda activate lintbench
conda install -c conda-forge pyzmq -y
conda install -c conda-forge libstdcxx-ng -y
conda install ipykernel -y        # makes it a Jupyter kernel
python -m ipykernel install --user --name lintbench --display-name "LintBench"
```

## install dependencies

```sh
pip install uv
uv sync
pip install vllm                  # for serving open-source models
```

## setup hugging face token

```sh
echo "HF_TOKEN=hf_your_token_here" >> .env
export HF_TOKEN=hf_your_token_here
hf auth login             # or just set the env var
```

## Submit the vLLM server job

```sh
cd lintbench
sbatch inference/hpc/slurm_vllm_gemma4_26b.sh
sbatch inference/hpc/slurm_vllm_deepseek_r1_32b.sh
sbatch inference/hpc/slurm_vllm_llama3.1_8b.sh
sbatch inference/hpc/slurm_vllm_qwen3_coder_30b.sh

# Watch it start:
squeue -u $USER
ls -lt inference/hpc/logs/vllm_deepseek_r1_32b_*.log | head -1
tail -f inference/hpc/logs/vllm_deepseek_r1_32b_<jobid>.log
# wait for: "Application startup complete"
```

## run inference

```sh
# run inference (from the login node qbd1):

sbatch inference/hpc/slurm_inference_gemma4_26b.sh
sbatch inference/hpc/slurm_inference_deepseek_r1_32b.sh
sbatch inference/hpc/slurm_inference_llama3.1_8b.sh
sbatch inference/hpc/slurm_inference_qwen25_32b.sh

# see logs
# tail live
tail -f inference/hpc/logs/inference_deepseek_r1_32b_<jobid>.log

# or find the latest
ls -lt inference/hpc/logs/inference_deepseek_r1_32b_*.log | head -1

OR

# specific prompt
# The vLLM server is on qbd498, and run_hpc_inference.sh connects to it from the login node over the internal network — no SSH needed.
bash inference/hpc/run_hpc_inference.sh \
    --model       gemma4-26b \
    --host        qbd498 \
    --port        8003 \
    --prompt      zero_shot \
    --samples     5 \
    --temperature 1.0 \
    --run-id      gemma4_26b_pass5_zero_shot \
    --out         generated/
```

## download locally

```sh
# logs
cd /Users/researchlab/dev/research/lintbench/lintbench/inference/hpc/logs
rsync -avz mfakor1@qbd.loni.org:/work/mfakor1/lintbench/lintbench/inference/hpc/logs/ .

# inference
cd /Users/researchlab/dev/research/lintbench/lintbench/inference/hpc/generated
rsync -avz mfakor1@qbd.loni.org:/work/mfakor1/lintbench/lintbench/generated/ .
```

