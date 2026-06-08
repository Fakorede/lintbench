"""
inference/prompts.py
--------------------
Prompt templates and output extraction for LintBench inference.

Prompt variants
---------------
  zero_shot                  NL spec only (base)
  skeleton                   NL spec + pre-filled class skeleton with Issue/Implementation
                             boilerplate and method stubs (base + structure)
  few_shot_surface_matched   NL spec + worked Detector examples matched by scanner
                             interface type, drawn from checks outside the 148-instance
                             dataset (base + examples)

Few-shot examples
-----------------
12 examples across 6 scanner interfaces (SourceCodeScanner, XmlScanner, GradleScanner,
OtherFileScanner, BinaryResourceScanner, ResourceFolderScanner), all excluded from the
benchmark to avoid contamination.
"""

import re
from textwrap import dedent

# ---------------------------------------------------------------------------
# Few-shot examples — excluded from the benchmark to avoid contamination
# ---------------------------------------------------------------------------
from .examples import (
    EXAMPLE_SOURCE_SCANNER_KT,
    EXAMPLE_SOURCE_SCANNER_JAVA,
    EXAMPLE_SOURCE_SCANNER_ANNOTATION_KT,
    EXAMPLE_XML_SCANNER_KT,
    EXAMPLE_XML_SCANNER_JAVA,
    EXAMPLE_XML_SCANNER_JAVA_ATTR,
    EXAMPLE_GRADLE_SCANNER_KT,
    EXAMPLE_GRADLE_SCANNER_KT2,
    EXAMPLE_OTHER_FILE_SCANNER_JAVA,
    EXAMPLE_OTHER_FILE_SCANNER_KT,
    EXAMPLE_BINARY_RESOURCE_SCANNER_KT,
    EXAMPLE_RESOURCE_FOLDER_SCANNER_KT,
)
from .stubs import (
    UAST_HANDLER_METHODS,
    KT_STUBS,
    KT_HANDLER_STUBS,
    KT_TYPE_IMPORTS,
    BASE_KT_IMPORTS,
    JAVA_STUBS,
    JAVA_HANDLER_STUBS,
    JAVA_TYPE_IMPORTS,
    BASE_JAVA_IMPORTS,
    STUB_KT,
    STUB_JAVA,
    APIDETECTOR_EXTRA_KT,
)

# ---------------------------------------------------------------------------
# Scanner-interface → example mapping (surface matching)
# ---------------------------------------------------------------------------

# Each entry: (scanner_interface_set, kt_target_example, java_target_example)
# kt_target / java_target refer to the TARGET language being generated,
# not the example language (some examples are always Kotlin).
# _pick_examples deduplicates, so a mixed instance gets one example per
# *distinct* example string, not one per interface.
_SURFACE_EXAMPLES: list[tuple[set, str, str]] = [
    # Each entry: (scanner_interface_set, kt_target_example, java_target_example)
    # kt_target / java_target refer to the TARGET language being generated,
    # not the example language (some examples are always Kotlin).
    ({"GradleScanner"},                   EXAMPLE_GRADLE_SCANNER_KT,         EXAMPLE_GRADLE_SCANNER_KT2),
    ({"OtherFileScanner"},                EXAMPLE_OTHER_FILE_SCANNER_KT,      EXAMPLE_OTHER_FILE_SCANNER_JAVA),
    ({"BinaryResourceScanner"},           EXAMPLE_BINARY_RESOURCE_SCANNER_KT, EXAMPLE_BINARY_RESOURCE_SCANNER_KT),
    ({"ResourceFolderScanner"},           EXAMPLE_RESOURCE_FOLDER_SCANNER_KT, EXAMPLE_RESOURCE_FOLDER_SCANNER_KT),
    ({"XmlScanner"},                      EXAMPLE_XML_SCANNER_KT,             EXAMPLE_XML_SCANNER_JAVA),
    ({"SourceCodeScanner", "ClassScanner"}, EXAMPLE_SOURCE_SCANNER_KT,        EXAMPLE_SOURCE_SCANNER_JAVA),
]

_DEFAULT_EXAMPLES = (EXAMPLE_SOURCE_SCANNER_KT, EXAMPLE_SOURCE_SCANNER_JAVA)


def _pick_examples(instance: dict, lang_ext: str) -> str:
    """
    Return one few-shot example per matched scanner interface, separated by
    a divider. For multi-interface detectors (e.g. SourceCodeScanner +
    XmlScanner) this provides one concrete example per interface pattern so
    the model sees how each scanner callback is implemented.

    Matches on scanner_interfaces only (not api_surfaces) to avoid false
    positives.
    """
    signals: set[str] = set(instance.get("scanner_interfaces", []))
    seen_examples: list[str] = []
    seen_texts: set[str] = set()

    for marker_set, kt_ex, java_ex in _SURFACE_EXAMPLES:
        if signals & marker_set:
            ex = kt_ex if lang_ext == "kt" else java_ex
            if ex not in seen_texts:
                seen_examples.append(ex)
                seen_texts.add(ex)

    if not seen_examples:
        kt_ex, java_ex = _DEFAULT_EXAMPLES
        seen_examples.append(kt_ex if lang_ext == "kt" else java_ex)

    return "\n\n---\n\n".join(seen_examples)


# ---------------------------------------------------------------------------
# Skeleton generator
# ---------------------------------------------------------------------------


def _infer_scope(interfaces: list[str]) -> str:
    """Return a Scope expression for the Implementation constructor."""
    _SINGLE: dict[str, str] = {
        "SourceCodeScanner":     "Scope.JAVA_FILE_SCOPE",
        "GradleScanner":         "Scope.GRADLE_SCOPE",
        "XmlScanner":            "Scope.RESOURCE_FILE_SCOPE",
        "OtherFileScanner":      "Scope.OTHER_SCOPE",
        "BinaryResourceScanner": "Scope.BINARY_RESOURCE_FILE_SCOPE",
        "ResourceFolderScanner": "Scope.RESOURCE_FOLDER_SCOPE",
    }
    _ENUM_ITEM: dict[str, str] = {
        "SourceCodeScanner":     "Scope.JAVA_FILE",
        "GradleScanner":         "Scope.GRADLE_FILE",
        "XmlScanner":            "Scope.RESOURCE_FILE",
        "BinaryResourceScanner": "Scope.BINARY_RESOURCE_FILE",
        "ResourceFolderScanner": "Scope.RESOURCE_FOLDER",
    }
    if len(interfaces) == 1:
        return _SINGLE.get(interfaces[0], "Scope.JAVA_FILE_SCOPE")
    items = [_ENUM_ITEM[i] for i in interfaces if i in _ENUM_ITEM]
    return f"EnumSet.of({', '.join(items)})" if items else "Scope.JAVA_FILE_SCOPE"


def _collect_imports(stub_text: str, type_map: dict[str, str]) -> list[str]:
    """Return import lines for type names that appear as whole words in stub_text."""
    return [
        imp for name, imp in type_map.items()
        if re.search(r'\b' + re.escape(name) + r'\b', stub_text)
    ]


def _build_skeleton(instance: dict) -> str:
    """
    Return a detector skeleton populated from the instance's methods_to_generate.
    Stubs are generated for every method in the list using exact Lint API signatures.
    UElementHandler methods are embedded inside createUastHandler() rather than
    placed directly on the Detector class.
    """
    lang_ext   = instance["check_lang"]
    detector   = instance["detector"]
    issue_id   = instance["issue_id"]
    brief      = instance.get("brief_description", "").replace('"', '\\"')
    category   = instance.get("category", "CORRECTNESS")
    severity   = instance.get("severity", "WARNING")
    priority   = instance.get("priority") or 5
    interfaces = instance.get("scanner_interfaces", ["SourceCodeScanner"])
    methods    = instance.get("methods_to_generate", [])
    base_class = instance.get("base_class", "Detector")

    scope      = _infer_scope(interfaces)
    iface_str  = ", ".join(interfaces)

    use_handler    = "createUastHandler" in methods
    handler_meths  = [m for m in methods if m in UAST_HANDLER_METHODS] if use_handler else []
    detector_meths = [
        m for m in methods
        if m != "createUastHandler" and m not in handler_meths
    ]

    if lang_ext == "kt":
        # Direct detector-level stubs
        method_blocks: list[str] = []
        for m in detector_meths:
            method_blocks.append(
                KT_STUBS.get(m, f"override fun {m}(/*TODO*/) {{ TODO() }}")
            )

        # createUastHandler with embedded handler stubs
        if use_handler:
            inner_lines: list[str] = []
            for m in handler_meths:
                stub = KT_HANDLER_STUBS.get(m, f"override fun {m}(node: UElement) {{ TODO() }}")
                inner_lines.append(
                    "\n".join("        " + line for line in stub.splitlines())
                )
            inner = (
                "\n\n".join(inner_lines)
                if inner_lines
                else "        // TODO: override visitor methods matching getApplicableUastTypes()"
            )
            method_blocks.append(
                f"override fun createUastHandler(context: JavaContext): UElementHandler =\n"
                f"    object : UElementHandler() {{\n"
                f"{inner}\n"
                f"    }}"
            )

        body = "\n\n".join(
            "\n".join("    " + line for line in block.splitlines())
            for block in method_blocks
        )

        all_stubs = "\n".join(method_blocks) + f" {iface_str} {scope}"
        extra = _collect_imports(all_stubs, KT_TYPE_IMPORTS)
        imports = "\n".join(sorted(set(BASE_KT_IMPORTS + extra)))

        companion = "\n".join([
            "    companion object {",
            "        private val IMPLEMENTATION = Implementation(",
            f"            {detector}::class.java,",
            f"            {scope},",
            "        )",
            "",
            "        @JvmField",
            "        val ISSUE = Issue.create(",
            f'            id = "{issue_id}",',
            f'            briefDescription = "{brief}",',
            '            explanation = "TODO",',
            f"            category = Category.{category},",
            f"            priority = {priority},",
            f"            severity = Severity.{severity},",
            "            implementation = IMPLEMENTATION,",
            "        )",
            "    }",
        ])

        # If the base class already provides the scanner interface (e.g. LayoutDetector
        # implements XmlScanner), omit the redundant interface list.
        _BASE_PROVIDES_IFACE = {"LayoutDetector", "ResourceXmlDetector"}
        if base_class in _BASE_PROVIDES_IFACE:
            class_header = f"class {detector} : {base_class}()"
        else:
            class_header = f"class {detector} : {base_class}(), {iface_str}"

        sections = [
            f"package com.android.tools.lint.checks",
            "",
            imports,
            "",
            f"{class_header} {{",
            "",
            companion,
        ]
        if body:
            sections += ["", body]
        sections.append("}")
        return "\n".join(sections)

    else:  # java
        # Direct detector-level stubs
        method_blocks = []
        for m in detector_meths:
            method_blocks.append(
                JAVA_STUBS.get(m, f"public void {m}(/*TODO*/) {{ // TODO }}")
            )

        # createUastHandler with embedded handler stubs
        if use_handler:
            inner_lines = []
            for m in handler_meths:
                stub = JAVA_HANDLER_STUBS.get(m, f"public void {m}(UElement node) {{ // TODO }}")
                inner_lines.append(
                    "\n".join("            " + line for line in stub.splitlines())
                )
            inner = (
                "\n\n".join(inner_lines)
                if inner_lines
                else "            // TODO: override visitor methods matching getApplicableUastTypes()"
            )
            method_blocks.append(dedent(f"""\
                @Override
                public UElementHandler createUastHandler(@NonNull JavaContext context) {{
                    return new UElementHandler() {{
                {inner}
                    }};
                }}"""))

        indent = "    "
        body = "\n\n".join(
            "\n".join(indent + line for line in block.splitlines())
            for block in method_blocks
        )

        # Infer imports — also inject Collection/List/Nullable tokens as needed
        collection_hint = " Collection" if any(
            m in ("getApplicableElements", "getApplicableAttributes") for m in methods
        ) else ""
        list_hint = " List" if any(
            m in ("getApplicableMethodNames", "getApplicableReferenceNames",
                  "getApplicableConstructorTypes", "getApplicableUastTypes",
                  "applicableSuperClasses", "applicableAnnotations") for m in methods
        ) else ""
        nullable_hint = " Nullable" if "checkDslPropertyAssignment" in methods else ""
        all_stubs = "\n".join(method_blocks) + f" {iface_str} {scope}{collection_hint}{list_hint}{nullable_hint}"
        extra = _collect_imports(all_stubs, JAVA_TYPE_IMPORTS)
        imports = "\n".join(sorted(set(BASE_JAVA_IMPORTS + extra)))

        _BASE_PROVIDES_IFACE = {"LayoutDetector", "ResourceXmlDetector"}
        if base_class in _BASE_PROVIDES_IFACE:
            java_class_header = f"public class {detector} extends {base_class} {{"
        else:
            java_class_header = f"public class {detector} extends {base_class} implements {iface_str} {{"

        header = [
            f"package com.android.tools.lint.checks;",
            "",
            imports,
            "",
            java_class_header,
            "",
            f"    private static final Implementation IMPLEMENTATION =",
            f"            new Implementation({detector}.class, {scope});",
            "",
            f"    public static final Issue ISSUE =",
            f"            Issue.create(",
            f'                    "{issue_id}",',
            f'                    "{brief}",',
            f'                    "TODO",',
            f"                    Category.{category},",
            f"                    {priority},",
            f"                    Severity.{severity},",
            f"                    IMPLEMENTATION);",
        ]
        sections = header
        if body:
            sections += ["", body]
        sections.append("}")
        return "\n".join(sections)


# ---------------------------------------------------------------------------
# Prompt templates
# ---------------------------------------------------------------------------
# - Implement every method listed in the required methods.

SYSTEM_PROMPT = """\
You are an expert Android developer specialising in Android Lint custom checks.
You write Lint Detector implementations in {lang} that are correct, idiomatic,
and compile cleanly against the Android Lint API.

Rules:
- Output ONLY the detector source file. No explanation, no markdown fences.
- Use the exact package: com.android.tools.lint.checks
- The class name must be: {detector}
- Import only from: com.android.tools.lint.*, com.intellij.psi.*, org.jetbrains.uast.*
- Do NOT include a main() method or any test code.
"""

# Minimal system prompt for zero_shot (C0): no structural hints, no method lists.
ZERO_SHOT_SYSTEM_PROMPT = """\
You are an expert Android developer specialising in Android Lint custom checks.
You write Lint Detector implementations in {lang} that are correct, idiomatic,
and compile cleanly against the Android Lint API.

- Output ONLY the detector source file. No explanation, no markdown fences.
- Use the exact package: com.android.tools.lint.checks
- The class name must be: {detector}
"""

# ── zero_shot ────────────────────────────────────────────────────────────────

ZERO_SHOT_TEMPLATE = """\
Implement an Android Lint Detector named {detector} in {lang} for the following issue.

Specification:
{nl_spec}
{more_info}
Generate the complete {detector}.{ext} source file now.\
"""

# ── skeleton ─────────────────────────────────────────────────────────────────

SKELETON_TEMPLATE = """\
Complete the following Android Lint Detector skeleton in {lang}.

Issue ID: {issue_id}
Detector class name: {detector}
Language: {lang}
Category: {category}
Severity: {severity}
Base class to extend: {base_class}

Specification:
{nl_spec}

Starter skeleton — fill in all TODO() stubs and add any helper methods needed:
```{ext}
{skeleton}
```
{more_info}
Output the complete {detector}.{ext} source file with all methods fully implemented.\
"""

# ── few_shot ─────────────────────────────────────────────────────────────────

FEW_SHOT_TEMPLATE = """\
{example_header}

{example}

---

Now implement a NEW Android Lint Detector in {lang} for the following issue.

Issue ID: {issue_id}
Detector class name: {detector}
Language: {lang}
Category: {category}
Severity: {severity}
Base class to extend: {base_class}
Scanner interfaces to implement: {scanner_interfaces}

Specification:
{nl_spec}

Lint API methods to override:
{methods_list}
{more_info}
Generate the complete {detector}.{ext} source file now.\
"""

# ── few_shot_surface_matched — reuses FEW_SHOT_TEMPLATE with surface-picked example ──

# ── compile_repair ───────────────────────────────────────────────────────────

COMPILE_REPAIR_SYSTEM = """\
You are an expert Android developer specialising in Android Lint custom checks.
Fix compilation errors in {lang} Android Lint Detector code.

Rules:
- Output ONLY the corrected source file. No explanation, no markdown fences.
- Preserve the exact package: com.android.tools.lint.checks
- Preserve the class name: {detector}
- Fix every compilation error listed. Do not introduce new errors.
- Do NOT remove or change the detector logic — only fix compilation issues.
"""

COMPILE_REPAIR_TEMPLATE = """\
The following {lang} Android Lint Detector failed to compile.
Fix all compilation errors and return the corrected source file.

Compilation errors:
{compile_errors}

Original code:
```{ext}
{original_code}
```

Output ONLY the corrected {detector}.{ext} source file. \
Do not include any explanation or markdown fences.\
"""

# ---------------------------------------------------------------------------
# All supported prompt variants (for CLI validation)
# ---------------------------------------------------------------------------

ALL_VARIANTS = [
    "zero_shot",
    "skeleton",
    "few_shot_surface_matched",
]


# ---------------------------------------------------------------------------
# Public API
# ---------------------------------------------------------------------------

def build_prompt(instance: dict, variant: str) -> tuple[str, str]:
    """Return (system_prompt, user_prompt) for the given instance and variant."""
    lang     = "Kotlin" if instance["check_lang"] == "kt" else "Java"
    ext      = instance["check_lang"]
    issue_id = instance["issue_id"]
    detector = instance["detector"]

    methods_list = "\n".join(
        f"  {i+1}. {m}" for i, m in enumerate(instance["methods_to_generate"])
    )

    more_info = ""
    if instance.get("more_info_urls"):
        urls = "\n".join(f"  - {u}" for u in instance["more_info_urls"])
        more_info = f"\nReference documentation:\n{urls}\n"

    base_class = instance.get("base_class", "Detector")

    common = dict(
        lang=lang,
        ext=ext,
        issue_id=issue_id,
        detector=detector,
        category=instance["category"],
        severity=instance["severity"],
        base_class=base_class,
        scanner_interfaces=", ".join(instance["scanner_interfaces"]) or "SourceCodeScanner",
        nl_spec=instance["nl_spec"],
        methods_list=methods_list,
        more_info=more_info,
    )

    system = SYSTEM_PROMPT.format(lang=lang, detector=detector)

    if variant == "zero_shot":
        system = ZERO_SHOT_SYSTEM_PROMPT.format(lang=lang, detector=detector)
        user = ZERO_SHOT_TEMPLATE.format(**common)

    elif variant == "skeleton":
        skeleton = _build_skeleton(instance)
        user = SKELETON_TEMPLATE.format(skeleton=skeleton, **common)

    elif variant == "few_shot_surface_matched":
        examples = _pick_examples(instance, ext)
        n = examples.count("\n\n---\n\n") + 1
        header = (
            f"Here {'are' if n > 1 else 'is'} {n} example{'s' if n > 1 else ''} "
            f"of complete Android Lint Detector{'s' if n > 1 else ''} in {lang} "
            f"using the same scanner interface{'s' if n > 1 else ''} "
            f"({common['scanner_interfaces']}):"
        )
        user = FEW_SHOT_TEMPLATE.format(example=examples, example_header=header, **common)

    else:
        raise ValueError(
            f"Unknown prompt variant: {variant!r}. "
            f"Choose from: {', '.join(ALL_VARIANTS)}"
        )

    return system, user


def build_repair_prompt(
    instance: dict,
    original_code: str,
    compile_errors: list[str],
) -> tuple[str, str]:
    """
    Return (system_prompt, user_prompt) for a compile-repair pass.

    Shows the model the failed code and compiler errors so it can
    produce a corrected file (compile_repair_1 mode).
    """
    lang     = "Kotlin" if instance["check_lang"] == "kt" else "Java"
    ext      = instance["check_lang"]
    detector = instance["detector"]

    errors_str = "\n".join(compile_errors) if compile_errors else "(no error detail available)"

    system = COMPILE_REPAIR_SYSTEM.format(lang=lang, detector=detector)
    user   = COMPILE_REPAIR_TEMPLATE.format(
        lang=lang,
        ext=ext,
        detector=detector,
        compile_errors=errors_str,
        original_code=original_code,
    )
    return system, user


# ---------------------------------------------------------------------------
# Stub / smoke-test helpers
# ---------------------------------------------------------------------------


def stub_detector(instance: dict) -> str:
    """Return a minimal syntactically valid detector for smoke testing."""
    if instance["check_lang"] == "kt":
        extra = APIDETECTOR_EXTRA_KT if instance["detector"] == "ApiDetector" else ""
        return STUB_KT.format(
            detector=instance["detector"],
            issue_id=instance["issue_id"],
            extra_constants=extra,
        )
    return STUB_JAVA.format(
        detector=instance["detector"],
        issue_id=instance["issue_id"],
    )


def extract_code(raw: str, ext: str) -> str:
    """
    Extract source code from the model's response.
    Handles:
      1. ```kotlin/java/... fenced blocks  — picks the block that starts with
         'package', falling back to the longest block (avoids grabbing a small
         snippet Claude writes during CoT reasoning)
      2. Plain ``` fenced blocks (same preference logic)
      3. Raw code (starts with package/comment)
      4. CoT prefix — reasoning text before the package declaration (strips preamble)
    """
    # 1 & 2: fenced blocks
    for lang_tag in (ext, "kotlin" if ext == "kt" else "java", ""):
        pattern = rf"```{lang_tag}\s*\n(.*?)```"
        blocks = [m.strip() for m in re.findall(pattern, raw, re.DOTALL | re.IGNORECASE)]
        if blocks:
            # Prefer the block that looks like a full source file
            for block in blocks:
                if block.startswith("package"):
                    return block
            return max(blocks, key=len)

    # 3: raw code with no preamble
    stripped = raw.strip()
    if stripped.startswith("package") or stripped.startswith("/*"):
        return stripped

    # 4: CoT preamble — find the first package declaration and take everything from there
    m = re.search(r"^(package\s+\S)", raw, re.MULTILINE)
    if m:
        return raw[m.start():].strip()

    return stripped
