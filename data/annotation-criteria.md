# Annotation Criteria

An instance is flagged as **problematic** if it meets either of the following
criteria. The two are independent axes; an instance may satisfy one or both.

## 1. Spec under-specified (`spec_barrier`)

The specification omits information the model cannot infer, so that even a fully
capable model would fail. Sub-types:

- **bundled_issue** — one issue id encodes multiple behaviors/sub-rules; the
  spec describes only a subset.
- **hidden_contract** — the fixed test references a constant/`Option` the spec
  never mentions, so the test itself will not compile.
- **output_contract** — the tests require an undocumented output (e.g. a
  quick-fix diff or an exact message) the spec does not describe.
- **underspecified_rules** — the spec defers to external documentation instead
  of stating the concrete detectable rule.

## 2. Sibling test burden (`sibling_burden`)

An instance fails unless the model also implements one or more **other** issues
from the same detector.

### Concrete example — `IconDetector:IconDuplicatesConfig`

The one targeted test, `testNoDpi`, has an expected output containing three tags:
`[IconDuplicatesConfig]`, `[IconNoDpi]`, `[IconMissingDensityFolder]`.

1. This requires **all three** tags to be present to pass.
2. The instance only asked for `IconDuplicatesConfig`.
3. Gemini correctly emitted `[IconDuplicatesConfig]` but produced nothing for the
   siblings, so the `[IconNoDpi]` check failed first resulting in a instance failure.

## Counts (base+apis+docs, 148 pre-validated instances)

| Criterion | Count |
| --- | --- |
| `spec_barrier = Y` | 22 |
| `sibling_burden = Y` | 24 |
| Intersection (both) | 11 |
| **Union (distinct problematic)** | **35** |
