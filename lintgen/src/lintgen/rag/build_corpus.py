"""
lintgen.rag.build_corpus — build the five-tier Lint API FAISS index.

Tier 1 (lint_interfaces.json): hand-curated scanner interface methods.
Tier 2 (lint_fullapi.json):    hand-curated Context/JavaContext/LintFix/etc. API entries.
Tier 3 (lint_guides.json):     Official Lint API guide docs, chunked with
                                RecursiveCharacterTextSplitter (~1600 chars,
                                200-char overlap, heading-aware separators).
Tier 4 (auto-extracted):       UAST (org.jetbrains.uast) and PSI (com.intellij.psi)
                                method signatures extracted from intellij-community source.
Tier 5 (sdk_constants.json):   SdkConstants used in ground-truth detectors, with values
                                extracted from common-31.7.0.jar.

Run:
  lintgen build-index
  lintgen build-index --intellij-dir ../intellij-community --out-dir src/lintgen/rag/index/
"""

from __future__ import annotations

import argparse
import json
import re
from pathlib import Path

CORPUS_DIR    = Path(__file__).parent / "corpus"
INDEX_DIR     = Path(__file__).parent / "index"
DEFAULT_INTELLIJ_DIR = Path(__file__).parents[4] / "intellij-community"

# Package roots for import inference
_UAST_PKG = "org.jetbrains.uast"
_PSI_PKG  = "com.intellij.psi"


# ── Tier 4 auto-extraction helpers ────────────────────────────────────────────

# No DOTALL regexes — all patterns operate line-by-line to avoid backtracking.

# Only extract classes whose names belong to the public UAST/PSI API.
# For UAST: allow all U* and Uast* classes (small, focused package).
# For PSI: exclude obvious internal/utility classes.
_API_CLASS_PREFIX = re.compile(r'^(?:Psi|Uast|Jvm|CommonClass|TypeConversion)(?!.*(?:Impl|Null|Union|Stub|Adapter|Factory|Registry|Provider)$)')

_KT_CLASS_RE  = re.compile(
    r'^(?:(?:public|internal|private|protected|abstract|open|sealed|data|inner|value)\s+)*'
    r'(?:interface|class|object)\s+([A-Z]\w+)',
)
_KT_FUN_RE    = re.compile(
    r'(?:inline\s+|suspend\s+|operator\s+|infix\s+|tailrec\s+)*'  # optional modifiers
    r'fun\s+'
    r'(?:<[^>]+>\s+)?'                       # optional type params: <T : UElement>
    r'(?:[\w<>?,\s?.]+\.\s*)?'               # optional receiver type: UElement.
    r'(\w+)\s*\(([^)]*)\)\s*(?::\s*([\w<>?,\s?.]+))?'  # name(params): ReturnType
)
_KT_PROP_RE   = re.compile(r'(?:val|var)\s+(\w+)\s*:\s*([\w<>?,\s?.]+)')
_JAVA_CLASS_RE = re.compile(
    r'^(?:(?:public|protected|private|abstract|final|static)\s+)*'
    r'(?:@\w+\s+)*'                  # optional inline annotations (e.g. @NlsSafe)
    r'(?:interface|class|enum)\s+([A-Z]\w+)'
)
# Explicit visibility (class methods / default methods)
_JAVA_METHOD_RE = re.compile(
    r'(?:public|protected)\s+'
    r'(?:(?:abstract|default|static|final|synchronized|native)\s+)*'
    r'(?:@\w+\s+)*'               # optional annotations before return type (e.g. @NotNull)
    r'([\w<>\[\]?.]+)\s+'
    r'(\w+)\s*\(([^)]*)\)'
)
# Implicit public interface methods (no modifier, just return type + name)
_JAVA_IFACE_METHOD_RE = re.compile(
    r'^(?:@\w+\s+)*'                     # optional annotations
    r'(?:@\w+\([^)]*\)\s+)*'             # optional parameterised annotations
    r'([\w<>\[\]?.@]+)\s+'               # return type
    r'(\w+)\s*\(([^)]*)\)\s*(?:;|\{)'   # name(params); or name(params) {
)
# Interface constant fields: String FOO = "bar"; or int FOO = 0;
_JAVA_CONST_FIELD_RE = re.compile(
    r'^(?:@\w+\s+)*'
    r'(?:String|int|long|boolean|float|double|char)\s+'
    r'([A-Z][A-Z0-9_]+)\s*='             # CONSTANT_NAME =
)


def _is_deprecated(lines: list[str], idx: int) -> bool:
    """Return True if the member at line idx has a @Deprecated annotation above it."""
    i = idx - 1
    while i >= 0:
        stripped = lines[i].strip()
        if stripped.startswith("@Deprecated") or stripped.startswith("@java.lang.Deprecated"):
            return True
        # Stop scanning upward once we leave the annotation/comment block
        if stripped and not stripped.startswith("@") and not stripped.startswith("*") and not stripped.startswith("/*") and not stripped.startswith("//"):
            break
        i -= 1
    return False


def _extract_doc_lines(lines: list[str], idx: int) -> str:
    """
    Walk backward from line idx to collect the nearest /** ... */ block.
    Returns the cleaned first sentence, or "".
    """
    # Skip blank lines and annotations immediately above
    i = idx - 1
    while i >= 0 and (lines[i].strip() == "" or lines[i].strip().startswith("@")):
        i -= 1

    if i < 0 or not lines[i].strip().endswith("*/"):
        return ""

    # Collect the comment block upward
    comment_lines = []
    while i >= 0:
        comment_lines.append(lines[i])
        if lines[i].strip().startswith("/**"):
            break
        i -= 1

    comment_lines.reverse()
    raw = "\n".join(comment_lines)
    # Strip markers and leading * per line
    text = re.sub(r'/\*\*|\*/', "", raw)
    text = re.sub(r'^\s*\*\s?', "", text, flags=re.MULTILINE)
    # Strip {@code ...} inline tags
    text = re.sub(r'\{@\w+\s*([^}]*)}', r'\1', text)
    # Drop @param / @return / @see tags and everything after
    text = re.sub(r'\n\s*@\w+.*', "", text, flags=re.DOTALL)
    text = text.strip()
    if not text:
        return ""
    return re.split(r'(?<=[.!?])\s', text)[0].strip()


def _parse_kt_file(source: str, pkg: str) -> list[dict]:
    """Extract interface/class members from a Kotlin file, line-by-line."""
    lines   = source.splitlines()
    entries = []
    pkg_match = re.search(r'^package ([\w.]+)', source, re.MULTILINE)
    file_pkg  = pkg_match.group(1) if pkg_match else pkg

    # Stack of (outer_class, pop_below_depth) — pop when brace_depth < pop_below_depth
    class_stack: list[tuple[str | None, int]] = []
    current_class: str | None = None
    brace_depth = 0
    # Pending class: deferred push for multi-line constructors (class Foo(\n  ...\n) {)
    pending_class: tuple[str | None, bool] | None = None  # (name, is_api)

    for i, line in enumerate(lines):
        stripped = line.strip()
        if stripped.startswith("//"):
            continue

        # Count braces on this line (net change)
        net = stripped.count("{") - stripped.count("}")

        # Flush pending class when its opening { is found
        if pending_class is not None and "{" in stripped:
            brace_depth += net
            pname, pis_api = pending_class
            pending_class = None
            class_stack.append((current_class, brace_depth))
            current_class = pname if pis_api else None
            continue

        # Detect class/interface/object declaration
        cm = _KT_CLASS_RE.match(stripped)
        if cm:
            name = cm.group(1)
            is_api = bool(_API_CLASS_PREFIX.match(name) or re.match(r'^U[A-Z]', name) or re.match(r'^Abstract[A-Z]', name))
            brace_depth += net
            if "{" in stripped:
                # Body starts on this line
                class_stack.append((current_class, brace_depth))
                current_class = name if is_api else None
            elif stripped.endswith("(") or (stripped.count("(") > stripped.count(")")):
                # Multi-line constructor — defer until we see {
                pending_class = (name, is_api)
            # else: body-less class (e.g. class Foo : Base) — treat as one-liner, keep context
            continue

        brace_depth += net

        # Pop stack when brace depth drops below the depth recorded at class entry
        while class_stack and brace_depth < class_stack[-1][1]:
            current_class, _ = class_stack.pop()

        if current_class is None:
            continue

        # fun declaration
        fm = _KT_FUN_RE.search(stripped)
        if fm:
            name, params, ret = fm.group(1), fm.group(2), (fm.group(3) or "Unit").strip()
            if not name[0].isupper() and not name.startswith("_") and not _is_deprecated(lines, i):
                sig  = f"fun {name}({params.strip()}): {ret}"
                desc = _extract_doc_lines(lines, i)
                entries.append(_make_entry(current_class, name, sig, file_pkg, desc))
            continue

        # val/var property (instance) or ALL_CAPS companion constant
        pm = _KT_PROP_RE.search(stripped)
        if pm:
            name, typ = pm.group(1), pm.group(2).strip()
            is_const = bool(re.match(r'^[A-Z][A-Z0-9_]+$', name))  # companion object constant
            if (not name[0].isupper() or is_const) and not name.startswith("_") and not _is_deprecated(lines, i):
                sig  = f"val {name}: {typ}"
                desc = _extract_doc_lines(lines, i)
                entries.append(_make_entry(current_class, name, sig, file_pkg, desc))

    return entries


def _parse_java_file(source: str, pkg: str) -> list[dict]:
    """Extract public methods and interface constants from a Java file, line-by-line."""
    lines   = source.splitlines()
    entries = []
    current_class: str | None = None

    # Read actual package from the file (handles sub-packages like psi/util)
    pkg_match = re.search(r'^package ([\w.]+);', source, re.MULTILINE)
    file_pkg  = pkg_match.group(1) if pkg_match else pkg

    for i, line in enumerate(lines):
        stripped = line.strip()

        # Detect class/interface declaration — only keep Psi*/Uast*/Jvm* API classes
        cm = _JAVA_CLASS_RE.match(stripped)
        if cm and not stripped.startswith("//") and not stripped.startswith("*"):
            name = cm.group(1)
            current_class = name if (_API_CLASS_PREFIX.match(name) or re.match(r'^Abstract[A-Z]', name)) else None
            continue

        if current_class is None:
            continue

        # Try explicit visibility first, then implicit interface method
        mm = _JAVA_METHOD_RE.search(stripped) or _JAVA_IFACE_METHOD_RE.match(stripped)
        if mm:
            ret, name, params = mm.group(1), mm.group(2), mm.group(3)
            if name[0].isupper() or name.startswith("_") or ret in ("class", "interface", "enum", "return", "import", "new"):
                continue
            if _is_deprecated(lines, i):
                continue
            sig  = f"{ret} {name}({params.strip()})"
            desc = _extract_doc_lines(lines, i)
            entries.append(_make_entry(current_class, name, sig, file_pkg, desc))
            continue

        # Interface constant fields (e.g. PsiModifier, PsiKeyword, CommonClassNames)
        fm = _JAVA_CONST_FIELD_RE.match(stripped)
        if fm and not _is_deprecated(lines, i):
            const_name = fm.group(1)
            desc = _extract_doc_lines(lines, i)
            entries.append({
                "class":             f"{file_pkg}.{current_class}",
                "method":            const_name,
                "signature":         f"{current_class}.{const_name}",
                "method_description": desc,
                "imports":           [f"{file_pkg}.{current_class}"],
            })

    return entries


def _extract_uast_psi_methods(intellij_dir: Path) -> list[dict]:
    """Extract all methods, properties, and top-level extension functions from UAST and PSI source."""
    sources = [
        # UAST — root + all subdirs (visitor/, util/, expressions/, kinds/, etc.)
        (intellij_dir / "uast/uast-common/src/org/jetbrains/uast",          _UAST_PKG),
        # PSI — core-api, java-psi-api, java-frontback-psi-api
        (intellij_dir / "platform/core-api/src/com/intellij/psi",           _PSI_PKG),
        (intellij_dir / "java/java-psi-api/src/com/intellij/psi",           _PSI_PKG),
        (intellij_dir / "java/java-frontback-psi-api/src/com/intellij/psi", _PSI_PKG),
        # psi/util
        (intellij_dir / "platform/core-api/src/com/intellij/psi/util",      _PSI_PKG),
        (intellij_dir / "java/java-psi-api/src/com/intellij/psi/util",      _PSI_PKG),
    ]

    # Extra individual files that don't match the class prefix filter but are used in detectors
    _EXTRA_FILES = [
        (intellij_dir / "platform/core-api/src/com/intellij/psi/CommonClassNames.java", _PSI_PKG),
        (intellij_dir / "java/java-psi-api/src/com/intellij/psi/util/TypeConversionUtil.java", _PSI_PKG),
    ]

    entries: list[dict] = []
    seen: set[tuple[str, str]] = set()

    def _ingest(path: Path, pkg: str) -> None:
        source = path.read_text(encoding="utf-8", errors="ignore")
        if path.suffix == ".kt":
            parsed = _parse_kt_file(source, pkg) + _parse_kt_toplevel(source, pkg)
        else:
            parsed = _parse_java_file(source, pkg)
        for e in parsed:
            key = (e["class"], e["method"])
            if key not in seen:
                seen.add(key)
                entries.append(e)

    for src_dir, pkg in sources:
        if not src_dir.exists():
            continue
        for path in list(src_dir.rglob("*.kt")) + list(src_dir.rglob("*.java")):
            _ingest(path, pkg)

    for path, pkg in _EXTRA_FILES:
        if path.exists():
            _ingest(path, pkg)

    return entries


def _parse_kt_toplevel(source: str, pkg: str) -> list[dict]:
    """
    Extract top-level Kotlin extension functions (e.g. fun UElement.getParentOfType(...))
    that are not inside any class/interface/object block.
    Stored under a synthetic class name derived from the file's @file:JvmName or package.
    """
    entries: list[dict] = []
    lines   = source.splitlines()

    # Use actual package from file, not the root pkg passed in
    pkg_match = re.search(r'^package ([\w.]+)', source, re.MULTILINE)
    file_pkg  = pkg_match.group(1) if pkg_match else pkg

    # Determine synthetic class name from @file:JvmName("Foo") or package
    jvm_name_match = re.search(r'@file:JvmName\("(\w+)"\)', source)
    if jvm_name_match:
        synthetic_class = jvm_name_match.group(1)
    elif pkg_match:
        synthetic_class = file_pkg.split(".")[-1].capitalize() + "Kt"
    else:
        return entries

    # Track whether we're inside a class block to skip member functions
    depth = 0
    in_class = False

    for i, line in enumerate(lines):
        stripped = line.strip()

        # Track brace depth to know when we're inside a class
        if _KT_CLASS_RE.match(stripped) and not stripped.startswith("//"):
            in_class = True
        if in_class:
            depth += stripped.count("{") - stripped.count("}")
            if depth <= 0:
                in_class = False
                depth = 0
            continue

        # Top-level fun (extension or plain)
        fm = _KT_FUN_RE.search(stripped)
        if fm and not stripped.startswith("//") and not stripped.startswith("*"):
            name, params, ret = fm.group(1), fm.group(2), (fm.group(3) or "Unit").strip()
            if not name[0].isupper() and not name.startswith("_") and not _is_deprecated(lines, i):
                sig  = f"fun {name}({params.strip()}): {ret}"
                desc = _extract_doc_lines(lines, i)
                entries.append({
                    "class":             f"{file_pkg}.{synthetic_class}",
                    "method":            name,
                    "signature":         sig,
                    "method_description": desc,
                    "imports":           [f"{file_pkg}.{synthetic_class}"],
                })

    return entries


# Java/Kotlin primitives and stdlib types that don't need an import
_NO_IMPORT = {
    "Boolean", "Int", "Long", "Float", "Double", "Short", "Byte", "Char",
    "Unit", "Any", "Nothing", "String", "List", "Map", "Set", "Collection",
    "Iterable", "Sequence", "Array", "Pair", "Triple", "Result",
    "void", "boolean", "int", "long", "float", "double", "short", "byte", "char",
    "Object", "Number", "Comparable", "Enum",
}


def _resolve_imports(sig: str, uast_pkg: str, psi_pkg: str) -> list[str]:
    """Extract type names from a signature and resolve them to import paths."""
    # Pull out all \w+ tokens that look like type names (start with uppercase)
    tokens = re.findall(r'\b([A-Z]\w+)\b', sig)
    imports = []
    seen: set[str] = set()
    for t in tokens:
        if t in _NO_IMPORT or t in seen:
            continue
        seen.add(t)
        if t.startswith("U") and len(t) > 1:
            imports.append(f"{uast_pkg}.{t}")
        elif t.startswith("Psi") or t.startswith("Jvm"):
            imports.append(f"{psi_pkg}.{t}")
        # other types (e.g. from other packages) — skip; not resolvable without full classpath
    return imports


def _make_entry(class_name: str, method_name: str, sig: str, pkg: str, desc: str) -> dict:
    fqn     = f"{pkg}.{class_name}"
    imports = _resolve_imports(sig, _UAST_PKG, _PSI_PKG)
    return {
        "class":              fqn,
        "method":             method_name,
        "signature":          sig,
        "method_description": desc,
        "imports":            imports,
    }


def _collect_uast_psi_imports(checks_dir: Path, dataset_path: Path) -> tuple[set[str], set[str]]:
    """
    Scan ground-truth detector files and return:
      (used_classes, used_methods)

    used_classes — UAST/PSI class FQNs that are imported (including class part of static
                   member imports, e.g. CommonClassNames.JAVA_LANG_OBJECT → CommonClassNames).
    used_methods — bare method/function names imported at the top-level function level
                   (e.g. `import org.jetbrains.uast.toUElementOfType` → 'toUElementOfType').
    """
    if not dataset_path.exists() or not checks_dir.exists():
        return set(), set()
    used_classes: set[str] = set()
    used_methods: set[str] = set()
    _IMPORT_RE = re.compile(
        r'import (?:static )?(org\.jetbrains\.uast\.[^\s;]+|com\.intellij\.psi\.[^\s;]+)'
    )
    for line in dataset_path.read_text().splitlines():
        inst = json.loads(line)
        path = checks_dir / inst["check_file"]
        if path.exists():
            src = path.read_text(errors="ignore")
            for m in _IMPORT_RE.findall(src):
                fqn = m.rstrip(";)")
                used_classes.add(fqn)
                parts = fqn.split(".")
                # Detect class-level boundary (first UpperCase segment)
                for i, part in enumerate(parts):
                    if part and part[0].isupper():
                        cls_fqn = ".".join(parts[:i+1])
                        used_classes.add(cls_fqn)
                        # If there are more segments after the class, they're members
                        if i + 1 < len(parts):
                            # static member import — last segment is member name
                            pass  # class already added
                        else:
                            # bare function import e.g. org.jetbrains.uast.toUElementOfType
                            # last segment is the function name (lowercase)
                            if parts[-1][0].islower():
                                used_methods.add(parts[-1])
                        break
                else:
                    # All lowercase segments — top-level function import
                    used_methods.add(parts[-1])
    return used_classes, used_methods


# ── Tier 5 SdkConstants extraction ───────────────────────────────────────────

DEFAULT_COMMON_JAR = (
    Path.home() / ".gradle/caches/modules-2/files-2.1"
    / "com.android.tools/common/31.7.0"
    / "1c142e0e9068bcd35ad06d1d7832f87b3e0d47fd/common-31.7.0.jar"
)
DEFAULT_CHECKS_DIR = Path(__file__).parents[4] / "base/lint/libs/lint-checks/src/main/java/com/android/tools/lint/checks"
DEFAULT_DATASET    = Path(__file__).parents[4] / "lintbench/data/dataset.jsonl"

_SDK_CONST_RE = re.compile(r'SdkConstants\.([A-Z][A-Z0-9_]+)')
_SDK_IMPORT_RE = re.compile(r'import (?:static )?com\.android\.SdkConstants\.([A-Z][A-Z0-9_]+)')


def _extract_sdk_constants(
    common_jar: Path,
    checks_dir: Path,
    dataset_path: Path,
) -> list[dict]:
    """
    1. Collect SdkConstants names used in ground-truth detector files.
    2. Decompile their values from common-31.7.0.jar via javap -constants.
    3. Return one entry per constant with name, value, and description.
    """
    import subprocess

    # Step 1 — collect used constant names from ground-truth files
    used: set[str] = set()
    if dataset_path.exists() and checks_dir.exists():
        for line in dataset_path.read_text().splitlines():
            inst = json.loads(line)
            path = checks_dir / inst["check_file"]
            if path.exists():
                src = path.read_text(errors="ignore")
                used.update(_SDK_IMPORT_RE.findall(src))
                used.update(_SDK_CONST_RE.findall(src))
    else:
        print("  WARNING: dataset or checks_dir not found — Tier 5 will index all SdkConstants.")

    # Step 2 — decompile constant values from jar
    if not common_jar.exists():
        print(f"  WARNING: {common_jar} not found — skipping Tier 5.")
        return []

    import tempfile, shutil
    tmpdir = Path(tempfile.mkdtemp())
    try:
        subprocess.run(
            ["jar", "xf", str(common_jar), "com/android/SdkConstants.class"],
            cwd=tmpdir, check=True, capture_output=True,
        )
        result = subprocess.run(
            ["javap", "-p", "-constants", str(tmpdir / "com/android/SdkConstants.class")],
            capture_output=True, text=True,
        )
    finally:
        shutil.rmtree(tmpdir, ignore_errors=True)

    all_constants: dict[str, str] = {}
    for line in result.stdout.splitlines():
        m = re.match(r'\s*public static final (?:java\.lang\.)?String (\w+) = "(.*?)";', line)
        if m:
            all_constants[m.group(1)] = m.group(2)

    # Step 3 — filter to used constants (or all if no dataset)
    target = used if used else set(all_constants.keys())
    entries = []
    for name, value in sorted(all_constants.items()):
        if name not in target:
            continue
        # Human-readable description: split on underscores and lowercase
        desc = name.replace("_", " ").lower()
        entries.append({
            "constant":    name,
            "value":       value,
            "description": desc,
            "imports":     ["com.android.SdkConstants"],
        })

    return entries


# ── Tier 3 guide chunking ──────────────────────────────────────────────────────

CHUNK_SIZE    = 1600   # chars (~400 tokens for bge-large-en-v1.5)
CHUNK_OVERLAP = 200    # chars


def _extract_text(html: str) -> str:
    """Strip HTML tags and normalise whitespace."""
    text = re.sub(r"<script[^>]*>.*?</script>", "", html, flags=re.DOTALL)
    text = re.sub(r"<style[^>]*>.*?</style>",  "", text, flags=re.DOTALL)
    text = re.sub(r"<[^>]+>", " ", text)
    text = re.sub(r"[ \t]+", " ", text)
    text = re.sub(r"\n{3,}", "\n\n", text)
    text = re.sub(r"body\{visibility:hidden.*", "", text, flags=re.DOTALL)
    return text.strip()


DOCS_DIR = Path(__file__).parents[4] / "android-custom-lint-rules/docs"

# API guide files to index
API_GUIDE_FILES = [
    "api-guide/annotations.md.html",
    "api-guide/ast-analysis.md.html",
    "api-guide/basics.md.html",
    "api-guide/changes.md.html",
    "api-guide/dataflow-analyzer.md.html",
    "api-guide/example.md.html",
    "api-guide/faq.md.html",
    "api-guide/messages.md.html",
    "api-guide/options.md.html",
    "api-guide/partial-analysis.md.html",
    "api-guide/publishing.md.html",
    "api-guide/quickfixes.md.html",
    "api-guide/terminology.md.html",
    "api-guide/test-modes.md.html",
    "api-guide/unit-testing.md.html",
    "usage/suppressing.md.html",
    "usage/lintxml.md.html",
]


def _extract_text(html: str) -> str:
    """Strip HTML tags and normalise whitespace."""
    text = re.sub(r"<script[^>]*>.*?</script>", "", html, flags=re.DOTALL)
    text = re.sub(r"<style[^>]*>.*?</style>",   "", text, flags=re.DOTALL)
    text = re.sub(r"<[^>]+>", " ", text)
    text = re.sub(r"[ \t]+", " ", text)
    text = re.sub(r"\n{3,}", "\n\n", text)
    text = re.sub(r"body\{visibility:hidden.*", "", text, flags=re.DOTALL)
    return text.strip()


def build_guide_chunks(docs_dir: Path = DOCS_DIR) -> list[dict]:
    """
    Extract plain text from the 17 specific HTML files listed in API_GUIDE_FILES
    (15 api-guide/*.md.html + usage/suppressing.md.html + usage/lintxml.md.html)
    and chunk with RecursiveCharacterTextSplitter (~1600 chars, 200-char overlap).

    Each chunk carries: source, file, title, chunk_index, content.
    content is prefixed with "[file > section title]" as a breadcrumb so the
    embedding captures context even for mid-section chunks.

    The result is written to lint_guides.json for inspection and indexed into
    the Tier 3 FAISS store.
    """
    try:
        from langchain_text_splitters import RecursiveCharacterTextSplitter
    except ImportError:
        from langchain.text_splitter import RecursiveCharacterTextSplitter  # type: ignore

    splitter = RecursiveCharacterTextSplitter(
        separators=["\n## ", "\n### ", "\n\n", "\n", " "],
        chunk_size=CHUNK_SIZE,
        chunk_overlap=CHUNK_OVERLAP,
        length_function=len,
    )

    chunks: list[dict] = []

    for rel_path in API_GUIDE_FILES:
        path = docs_dir / rel_path
        if not path.exists():
            print(f"  WARNING: guide not found — {path}")
            continue

        text = _extract_text(path.read_text(encoding="utf-8", errors="ignore"))
        source = rel_path.split("/")[0]   # "api-guide" or "usage"
        file_  = path.name

        # Split the full document into heading-delimited sections first,
        # then apply the token-budget splitter within each section.
        sections = re.split(r"\n(#{1,2} [^\n]+)", text)
        current_title = file_.replace(".md.html", "").replace("-", " ").title()
        current_body  = []

        def _flush(title: str, body_parts: list[str]) -> None:
            body = "\n".join(body_parts).strip()
            if not body or len(body) < 80:
                return
            section_text = f"# {title}\n\n{body}"
            if len(section_text) <= CHUNK_SIZE:
                chunks.append({
                    "source":      source,
                    "file":        file_,
                    "title":       title,
                    "chunk_index": 0,
                    "content":     section_text,
                })
            else:
                for i, sub in enumerate(splitter.split_text(section_text)):
                    sub = sub.strip()
                    if not sub:
                        continue
                    chunks.append({
                        "source":      source,
                        "file":        file_,
                        "title":       title,
                        "chunk_index": i,
                        "content":     f"[{file_} > {title}]\n{sub}",
                    })

        for part in sections:
            part = part.strip()
            if not part:
                continue
            if re.match(r"^#{1,2} ", part):
                _flush(current_title, current_body)
                current_title = part.lstrip("# ").strip()
                current_body  = []
            else:
                current_body.append(part)
        _flush(current_title, current_body)

    return chunks


# ── Index builder ──────────────────────────────────────────────────────────────

def build_index(args: argparse.Namespace) -> None:
    """Entry point called by `lintgen build-index`."""
    try:
        from langchain_huggingface import HuggingFaceEmbeddings
        from langchain_community.vectorstores import FAISS
        from langchain_community.vectorstores.utils import DistanceStrategy
        from langchain_core.documents import Document
    except ImportError:
        raise SystemExit("RAG dependencies not installed. Run: uv sync")

    from .knowledge_base import EMBEDDING_MODEL

    out_dir      = Path(args.out_dir)
    intellij_dir = Path(getattr(args, "intellij_dir", None) or DEFAULT_INTELLIJ_DIR)
    common_jar   = Path(getattr(args, "common_jar",   None) or DEFAULT_COMMON_JAR)
    out_dir.mkdir(parents=True, exist_ok=True)

    embeddings = HuggingFaceEmbeddings(model_name=EMBEDDING_MODEL)

    # ── Tier 1 ────────────────────────────────────────────────────────────────
    tier1_path = CORPUS_DIR / "lint_interfaces.json"
    if not tier1_path.exists():
        print(f"WARNING: {tier1_path} not found — skipping Tier 1 index.")
        tier1_entries = []
    else:
        tier1_entries = json.loads(tier1_path.read_text())
        print(f"Tier 1: {len(tier1_entries)} interface entries")

    if tier1_entries:
        tier1_docs = [
            Document(
                page_content=(
                    f"{e['interface'].split('.')[-1]} {e['method']} "
                    f"{e.get('method_description', '')[:200]}"
                ),
                metadata=e,
            )
            for e in tier1_entries
        ]
        tier1_store = FAISS.from_documents(tier1_docs, embeddings, distance_strategy=DistanceStrategy.COSINE)
        tier1_store.save_local(str(out_dir / "tier1"))
        print(f"Tier 1 index saved → {out_dir / 'tier1'}")

    # ── Tier 2 ────────────────────────────────────────────────────────────────
    tier2_path = CORPUS_DIR / "lint_fullapi.json"
    if not tier2_path.exists():
        print(f"WARNING: {tier2_path} not found — skipping Tier 2 index.")
        tier2_entries = []
    else:
        tier2_entries = json.loads(tier2_path.read_text())
        print(f"Tier 2: {len(tier2_entries)} entries from lint_fullapi.json")

    if tier2_entries:
        tier2_docs = [
            Document(
                page_content=(
                    f"{e.get('class', '').split('.')[-1]} {e['method']} "
                    f"{e.get('method_description', e.get('description', ''))[:200]}"
                ),
                metadata=e,
            )
            for e in tier2_entries
        ]
        tier2_store = FAISS.from_documents(tier2_docs, embeddings, distance_strategy=DistanceStrategy.COSINE)
        tier2_store.save_local(str(out_dir / "tier2"))
        print(f"Tier 2 index saved → {out_dir / 'tier2'}  ({len(tier2_entries)} entries)")

    # ── Tier 3 ────────────────────────────────────────────────────────────────
    docs_dir = Path(getattr(args, "docs_dir", None) or DOCS_DIR)
    tier3_out_path = CORPUS_DIR / "lint_guides.json"

    tier3_chunks = build_guide_chunks(docs_dir)
    if not tier3_chunks:
        print(f"WARNING: no guide chunks produced (docs_dir={docs_dir}) — skipping Tier 3.")
    else:
        # Write chunks for inspection
        tier3_out_path.write_text(json.dumps(tier3_chunks, indent=2) + "\n")
        print(
            f"Tier 3: {len(tier3_chunks)} chunks from {docs_dir} "
            f"(chunk_size={CHUNK_SIZE}, overlap={CHUNK_OVERLAP}) "
            f"→ lint_guides.json updated"
        )

        tier3_docs = [
            Document(
                # Child doc: breadcrumb + first 300 chars keeps embedding focused
                page_content=f"[{c['file']} > {c['title']}] {c['content'][:300]}",
                metadata=c,
            )
            for c in tier3_chunks
        ]
        tier3_store = FAISS.from_documents(tier3_docs, embeddings, distance_strategy=DistanceStrategy.COSINE)
        tier3_store.save_local(str(out_dir / "tier3"))
        print(f"Tier 3 index saved → {out_dir / 'tier3'}")

    # ── Tier 4 ────────────────────────────────────────────────────────────────
    if not intellij_dir.exists():
        print(f"WARNING: intellij-community not found at {intellij_dir} — skipping Tier 4.")
    else:
        tier4_all = _extract_uast_psi_methods(intellij_dir)
        if not tier4_all:
            print("WARNING: no Tier 4 entries extracted — skipping Tier 4 index.")
        else:
            # Filter to classes/methods actually used by the dataset checkers (lean corpus)
            used_classes, used_methods = _collect_uast_psi_imports(DEFAULT_CHECKS_DIR, DEFAULT_DATASET)
            if used_classes or used_methods:
                tier4_entries = [
                    e for e in tier4_all
                    if e["class"] in used_classes or e["method"] in used_methods
                ]
                print(
                    f"Tier 4: {len(tier4_all)} entries extracted, "
                    f"filtered to {len(tier4_entries)} (classes/methods used in dataset)"
                )
            else:
                tier4_entries = tier4_all
                print(f"Tier 4: {len(tier4_entries)} entries extracted from {intellij_dir}")

            tier4_corpus_path = CORPUS_DIR / "uast_psi_api.json"
            tier4_corpus_path.write_text(json.dumps(tier4_entries, indent=2) + "\n")
            print(f"→ uast_psi_api.json updated")
            tier4_docs = [
                Document(
                    page_content=(
                        f"{e['class'].split('.')[-1]} {e['method']} "
                        f"{e.get('method_description', '')[:200]}"
                    ),
                    metadata=e,
                )
                for e in tier4_entries
            ]
            tier4_store = FAISS.from_documents(tier4_docs, embeddings, distance_strategy=DistanceStrategy.COSINE)
            tier4_store.save_local(str(out_dir / "tier4"))
            print(f"Tier 4 index saved → {out_dir / 'tier4'}")

    # ── Tier 5 ────────────────────────────────────────────────────────────────
    tier5_entries = _extract_sdk_constants(common_jar, DEFAULT_CHECKS_DIR, DEFAULT_DATASET)
    if not tier5_entries:
        print("WARNING: no SdkConstants extracted — skipping Tier 5 index.")
    else:
        tier5_corpus_path = CORPUS_DIR / "sdk_constants.json"
        tier5_corpus_path.write_text(json.dumps(tier5_entries, indent=2) + "\n")
        print(
            f"Tier 5: {len(tier5_entries)} SdkConstants extracted "
            f"→ sdk_constants.json updated"
        )
        tier5_docs = [
            Document(
                page_content=f"SdkConstants.{e['constant']} = \"{e['value']}\" {e['description']}",
                metadata=e,
            )
            for e in tier5_entries
        ]
        tier5_store = FAISS.from_documents(tier5_docs, embeddings, distance_strategy=DistanceStrategy.COSINE)
        tier5_store.save_local(str(out_dir / "tier5"))
        print(f"Tier 5 index saved → {out_dir / 'tier5'}")

    print("\nIndex build complete. Run `lintgen generate` to use it.")
