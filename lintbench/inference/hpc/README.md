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

# Watch it start:
squeue -u $USER
ls inference/hpc/logs/
tail -f inference/hpc/logs/vllm_gemma4_26b_<jobid>.log
# wait for: "Application startup complete"
```

## run inference

```sh
# run inference (from the login node qbd1):
cd /work/mfakor1/lintbench/
conda activate lintbench
cd lintbench

# Run all prompt variants
# Get the node the job landed on
NODE=$(cat inference/hpc/logs/vllm_gemma4_26b_endpoint.txt)

bash inference/hpc/run_hpc_inference.sh \
    --model       gemma4-26b \
    --endpoint    "$NODE" \
    --prompt      skeleton \
    --prompt      few_shot_surface_matched \
    --prompt      zero_shot \
    --samples     5 \
    --temperature 1.0 \
    --run-id      gemma4_pass5_hpc_v1 \
    --out         generated/

NODE=$(cat inference/hpc/logs/vllm_deepseek_r1_32b_endpoint.txt)

bash inference/hpc/run_hpc_inference.sh \
    --model       deepseek-r1-32b \
    --endpoint    "$NODE" \
    --prompt      skeleton \
    --prompt      few_shot_surface_matched \
    --prompt      zero_shot \
    --samples     5 \
    --temperature 0.6 \
    --run-id      deepseek_pass5_hpc_v1 \
    --out         generated/

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

