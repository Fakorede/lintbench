## Regenerate manifest data

# 1. Re-run only the affected curate steps (fast, no Docker)
```sh
python lintbench/run_curate.py --only 4 5
```

# 2. Rebuild dataset.jsonl from updated lintbench.jsonl (no Docker)
```sh
python3 lintbench/build_env/oracle_eval.py --workers 4 --rebuild-dataset
```
