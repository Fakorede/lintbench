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
sys_p, usr_p = build_prompt(inst, 'few_shot_surface_matched')
print(usr_p)
"
```

**Prompt variants:** `zero_shot` · `api_hint` · `skeleton` · `few_shot_surface_matched` · `compile_repair_1`


---

COMMANDS


```sh
bash run_sample.sh --run-id run_001 --limit 1

OR

bash run_sample.sh --run-id run_001 --instance-id "RestrictionsDetector:ValidRestrictions"
bash run_sample.sh --run-id run_002 --instance-id "ScrollViewChildDetector:ScrollViewSize"
bash run_sample.sh --run-id run_003 --instance-id "DataBindingDetector:XmlEscapeNeeded"
bash run_sample.sh --run-id run_004 --instance-id "WrongConstructorDetector:NotConstructor"
bash run_sample.sh --run-id run_005 --instance-id "InvalidImeActionIdDetector:InvalidImeActionId"
bash run_sample.sh --run-id run_006 --instance-id "WebViewDetector:WebViewLayout"

OR

python run_inference.py \
    --model           anthropic/claude-sonnet-4.6 \
    --provider        openrouter \
    --prompt          zero_shot \
    --benchmark       data/dataset.jsonl \
    --out             generated/ \
    --max-tokens      32768 \
    --thinking-budget 10000 \
    --instance-id     "RestrictionsDetector:ValidRestrictions" \
    --run-id run_001
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
    --generated generated/run_001/claude-sonnet-4-5/skeleton/ \
    --model     claude-sonnet-4-5 \
    --prompt    skeleton \
    --out       results/sonnet_skeleton.json

# Step 3 — repair compile failures in-place
python -m inference \
    --model        claude-sonnet-4-5 \
    --repair-from  results/sonnet_skeleton.json \
    --generated    generated/run_001/claude-sonnet-4-5/skeleton/ \
    --out          generated/

# Step 4 — re-eval the repaired files
python run_eval.py \
    --generated generated/run_001/claude-sonnet-4-5/skeleton/ \
    --model     claude-sonnet-4-5 \
    --prompt    skeleton_repair1 \
    --out       results/sonnet_skeleton_repair1.json
```

The repair step overwrites the compile-failed `sample_N.{kt,java}` files in-place, so step 4 runs against the same directory but writes a new results file. Comparing `sonnet_skeleton.json` vs `sonnet_skeleton_repair1.json` gives you the delta from compile repair.

---


You need to run eval first — compile_repair_1 reads the eval results JSON to find which instances have `compilation_failed` status, then feeds the compiler errors back to the model.

**Step 2 — run eval:**
```bash
python run_eval.py \
    --generated generated/run_001/anthropic/claude-sonnet-4.6/few_shot_surface_matched_cot/ \
    --model     anthropic/claude-sonnet-4.6 \
    --prompt    few_shot_surface_matched_cot \
    --out       results/claude-sonnet-4.6_few_shot_surface_matched_cot.json \
    --build-env build_env/run.sh \
    --benchmark data/dataset.jsonl \
    --samples   1

OR

bash run_eval_all.sh --generated generated/run_001 --run-id run_001_eval

OR

bash run_eval_all.sh --generated generated/run_000 --run-id run_000_eval --instance-id "IconDetector:ConvertToWebp"

bash run_eval_all.sh --generated generated/run_001 --run-id run_001_eval --instance-id "NamespaceDetector:UnusedNamespace"

bash run_eval_all.sh --generated generated/run_002 --run-id run_002_eval --instance-id "ScrollViewChildDetector:ScrollViewSize"

bash run_eval_all.sh --generated generated/run_002-strict --run-id run_002_eval-loose --instance-id "ScrollViewChildDetector:ScrollViewSize"

bash run_eval_all.sh --generated generated/run_002-strict --run-id run_002_eval-strict --instance-id "ScrollViewChildDetector:ScrollViewSize" --strict

bash run_eval_all.sh --generated generated/run_003 --run-id run_003_eval --instance-id "DataBindingDetector:XmlEscapeNeeded"

bash run_eval_all.sh --generated generated/run_003 --run-id run_003_eval-v2 --instance-id "DataBindingDetector:XmlEscapeNeeded"

bash run_eval_all.sh --generated generated/run_004 --run-id run_004_eval --instance-id "WrongConstructorDetector:NotConstructor"

bash run_eval_all.sh --generated generated/run_005 --run-id run_005_eval --instance-id "InvalidImeActionIdDetector:InvalidImeActionId"

bash run_eval_all.sh --generated generated/run_006 --run-id run_006_eval --instance-id "WebViewDetector:WebViewLayout"
```

**Step 3 — run compile_repair_1:**
```bash
python run_inference.py \
    --model           anthropic/claude-sonnet-4.6 \
    --provider        openrouter \
    --repair-from     results/claude-sonnet-4.6_few_shot_surface_matched_cot.json \
    --generated       generated/run_001/anthropic/claude-sonnet-4.6/few_shot_surface_matched_cot/ \
    --instance        "IconDetector:ConvertToWebp" \
    --max-tokens      32768 \
    --thinking-budget 10000
```

**Step 4 - rerun eval:**
```bash
python run_eval.py \
    --generated generated/run_001/anthropic/claude-sonnet-4.6/few_shot_surface_matched_cot/ \
    --model     anthropic/claude-sonnet-4.6 \
    --prompt    few_shot_surface_matched_cot \
    --out       results/claude-sonnet-4.6_few_shot_surface_matched_cot_repair1.json \
    --build-env build_env/run.sh \
    --benchmark data/dataset.jsonl \
    --instance  "IconDetector:ConvertToWebp" \
    --samples   1
```

This overwrites the failing `sample_0.java` files in-place, so you can re-run the same eval command afterwards to measure the repaired pass rate.


---

Pilot Run:

```sh
# 1. Generate — all 3 models × 5 prompts × 30 instances
bash run_sample.sh \
    --run-id pilot_001 \
    --instance-file data/pilot_instances.txt

# 2. Evaluate
bash run_eval_all.sh \
    --generated generated/pilot_001 \
    --run-id pilot_001_eval \
    --instance-file data/pilot_instances.txt
```sh

