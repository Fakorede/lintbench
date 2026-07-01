# Prompts

Verbatim prompt templates used in this work. `{...}` are runtime placeholders
(e.g. `{detector}`, `{lang}`, `{nl_spec}`). Kept in sync with the code below.

## Inference baselines (`lintbench/inference/prompts.py`)

One-shot generation. `build_prompt(instance, variant)` returns `(system, user)`.

| File | Source constant | Role |
| --- | --- | --- |
| `zero_shot_system.txt` | `ZERO_SHOT_SYSTEM_PROMPT` | System prompt for the `zero_shot` variant |
| `zero_shot_user.txt` | `ZERO_SHOT_TEMPLATE` | User prompt for `zero_shot` (spec only) |
| `skeleton_user.txt` | `SKELETON_TEMPLATE` | User prompt for `skeleton` (pre-filled class skeleton) |
| `few_shot_user.txt` | `FEW_SHOT_TEMPLATE` | User prompt for `few_shot_surface_matched` (worked example + task) |
| `system_shared.txt` | `SYSTEM_PROMPT` | System prompt used by `skeleton` and `few_shot_surface_matched` |
| `compile_repair_system.txt` | `COMPILE_REPAIR_SYSTEM` | System prompt for the single compile-repair pass |
| `compile_repair_user.txt` | `COMPILE_REPAIR_TEMPLATE` | User prompt for the single compile-repair pass |

Prompt variants: `zero_shot`, `skeleton`, `few_shot_surface_matched`.

## LintGen (`lintgen/src/lintgen/agent/runner.py`, `.../rag/knowledge_base.py`)

Iterative generation. Iteration 0 reuses the inference `zero_shot` templates,
prepended with retrieved RAG knowledge context and the scanner overview;
subsequent iterations use the repair template with harness feedback.

| File | Source constant | Role |
| --- | --- | --- |
| `agent_scanner_overview.txt` | `SCANNER_OVERVIEW` | Injected at iteration 0 so the model picks scanner interface(s) |
| `agent_repair.txt` | `_REPAIR_HEADER` | Repair prompt for iterations 1..N (prev code + harness feedback) |

## Placeholders

| Placeholder | Meaning |
| --- | --- |
| `{lang}` / `{ext}` | `Kotlin`/`Java` and file extension `kt`/`java` |
| `{detector}` / `{issue_id}` | Detector class name / Lint issue id |
| `{nl_spec}` | Natural-language specification of the issue |
| `{category}` / `{severity}` / `{base_class}` | Issue metadata |
| `{scanner_interfaces}` | Comma-separated scanner interfaces |
| `{more_info}` | Optional reference-documentation block |
| `{skeleton}` | Pre-filled detector skeleton (skeleton variant) |
| `{example}` / `{example_header}` | Worked few-shot example and its header |
| `{compile_errors}` / `{original_code}` | Compile-repair inputs |
| `{prev_code}` / `{feedback}` | Agent repair inputs (previous code, harness feedback) |
