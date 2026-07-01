# Verified-subset experiment artifacts

Model-generated artifacts filtered to the **113 verified benchmark instances**
(`data/dataset_verified.jsonl`) — the 148-instance benchmark minus the 35
instances flagged as under-specified or subject to sibling-test burden
(see `data/annotation-criteria.md`).

Covers all 4 models (Claude Sonnet 4.6, Gemini 3.5 Flash, Kimi K2.7, Qwen 3.6-Max)
across both the LintBench baselines and the LintGen agent settings.

## Layout

```
lintbench-baselines/
  generated/<model>_pass5_v1/      Single-shot inference outputs (generated detector files)
                                   <provider>/<model>/<prompt>/<instance>/sample_*.{kt,java}
  results/<model>_baselines/       Eval results + per-sample logs
                                   <provider>/<model>_<variant>.json  (filtered + aggregates recomputed)
                                   logs/<provider>/<model>_<variant>/sample_N/<instance>/...

lintgen/
  <model>-full-run*/               RAG agent runs (base+docs, base+apis, base+apis+docs settings)
  <model>-base-norag/              No-RAG agent runs (zero_shot_agent setting)
                                   <provider>/<model>/<setting>_agent/<instance>/{trajectory.jsonl, iter_*}
                                   <setting>_agent/run_log.jsonl  (filtered to 113)
```

Prompt variants (baselines): `zero_shot`, `skeleton`, `few_shot_surface_matched`.
Agent budget: 5 iterations (final = iter 4).


