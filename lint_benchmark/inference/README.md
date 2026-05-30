Use `--stub` — it writes minimal placeholder detectors without making any API calls:

```sh
python run_inference.py \
    --model  claude-sonnet-4-5 \
    --prompt zero_shot \
    --stub \
    --limit  5 \
    --out    generated/
```

To preview what prompt a specific variant generates without running anything:

```sh
python3 -c "
from inference.prompts import build_prompt
import json

inst = json.loads(open('data/dataset.jsonl').readline())
sys_p, usr_p = build_prompt(inst, 'compile_repair_1')
print(usr_p)
"
```

**Prompt variants:** `zero_shot` · `api_hint` · `skeleton` · `few_shot_surface_matched` · `few_shot_surface_matched_cot` · `compile_repair_1`


---

COMMANDS


```sh
bash run_sample.sh --limit 1

python run_eval.py \
    --generated generated/anthropic/claude-sonnet-4.6/few_shot_surface_matched \
    --model     anthropic/claude-sonnet-4.6 \
    --prompt    few_shot_surface_matched \
    --out       results/anthropic/claude-sonnet-4.6_few_shot_surface_matched.json \
    --build-env build_env/run.sh \
    --samples   1

python run_eval.py \
    --generated generated/openai/gpt-5.5/api_hint \
    --model     openai/gpt-5.5 \
    --prompt    api_hint \
    --out       results/openai/gpt-5.5_api_hint.json \
    --build-env build_env/run.sh \
    --samples   1
```


Yes — `run_sample.sh` doesn't pass `--temperature` or `--samples`, so both default: `samples=1` triggers the `temperature=0.0` path in `__main__.py`. That's pass@1 greedy, which is correct for comparing prompt variants deterministically.

If you wanted pass@k you'd add `--samples 5` (and optionally `--temperature 0.8`) to the `run_sample.sh` invocation of `run_inference.py`.


---



Three steps for compile_repair_1:

```bash
# Step 1 — generate with skeleton
python -m inference \
    --model claude-sonnet-4-5 \
    --prompt skeleton \
    --out generated/

# Step 2 — evaluate, get results JSON with compile failures flagged
python run_eval.py \
    --generated generated/claude-sonnet-4-5/skeleton/ \
    --model     claude-sonnet-4-5 \
    --prompt    skeleton \
    --out       results/sonnet_skeleton.json

# Step 3 — repair compile failures in-place
python -m inference \
    --model        claude-sonnet-4-5 \
    --repair-from  results/sonnet_skeleton.json \
    --generated    generated/claude-sonnet-4-5/skeleton/ \
    --out          generated/

# Step 4 — re-eval the repaired files
python run_eval.py \
    --generated generated/claude-sonnet-4-5/skeleton/ \
    --model     claude-sonnet-4-5 \
    --prompt    skeleton_repair1 \
    --out       results/sonnet_skeleton_repair1.json
```

The repair step overwrites the compile-failed `sample_N.{kt,java}` files in-place, so step 4 runs against the same directory but writes a new results file. Comparing `sonnet_skeleton.json` vs `sonnet_skeleton_repair1.json` gives you the delta from compile repair.

---


You need to run eval first — compile_repair_1 reads the eval results JSON to find which instances have `compilation_failed` status, then feeds the compiler errors back to the model.

**Step 1 — run eval:**
```bash
python run_eval.py \
    --generated generated/anthropic/claude-sonnet-4.6/few_shot_surface_matched_cot/ \
    --model     anthropic/claude-sonnet-4.6 \
    --prompt    few_shot_surface_matched_cot \
    --out       results/claude-sonnet-4.6_few_shot_surface_matched_cot.json \
    --build-env build_env/run.sh \
    --samples   1
```

**Step 2 — run compile_repair_1:**
```bash
python run_inference.py \
    --model       anthropic/claude-sonnet-4.6 \
    --provider    openrouter \
    --repair-from results/claude-sonnet-4.6_few_shot_surface_matched_cot.json \
    --generated   generated/anthropic/claude-sonnet-4.6/few_shot_surface_matched_cot/ \
    --max-tokens  32768 \
    --thinking-budget 10000
```

This overwrites the failing `sample_0.java` files in-place, so you can re-run the same eval command afterwards to measure the repaired pass rate.


