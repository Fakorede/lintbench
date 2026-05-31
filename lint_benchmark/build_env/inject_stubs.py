#!/usr/bin/env python3
"""
inject_stubs.py — patch a generated detector file so BuiltinIssueRegistry
                  can initialize without NoSuchFieldError.

Usage:
    python3 inject_stubs.py <generated_file> <detector_class>

Problem:
    BuiltinIssueRegistry (pre-compiled in lint-checks.jar) has direct bytecode
    references to every Issue field of every detector class, e.g.:

        NamespaceDetector.TYPO
        NamespaceDetector.REDUNDANT
        NamespaceDetector.RES_AUTO
        ...

    When a generated NamespaceDetector only defines UNUSED, the JVM throws
    NoSuchFieldError during BuiltinIssueRegistry static init ->
    ExceptionInInitializerError -> NoClassDefFoundError on every test.

Fix:
    For each field the registry expects that's missing from the generated file,
    append a stub companion-object field that aliases the one real Issue the
    model defined. The registry can initialize; tests still exercise the
    real generated logic.
"""

import re
import sys
import json
from pathlib import Path

REGISTRY_FIELDS_JSON = Path(__file__).parent / "registry_fields.json"


def find_defined_issue_fields(source: str) -> list[str]:
    """Return @JvmField val names whose value is an Issue in the source.

    Handles both explicit and inferred types:
      @JvmField val FOO: Issue = ...      (explicit)
      @JvmField val FOO = Issue.create(   (inferred)
    """
    # Explicit type annotation: val FOO: Issue =
    explicit = re.findall(
        r'@JvmField\s+val\s+([A-Z_][A-Z_0-9]*)[\s\n]*:[\s\n]*Issue\s*=',
        source,
    )
    # Inferred type: val FOO = Issue.create(
    inferred = re.findall(
        r'@JvmField\s+val\s+([A-Z_][A-Z_0-9]*)\s*=\s*Issue\s*\.',
        source,
    )
    seen = set()
    result = []
    for f in explicit + inferred:
        if f not in seen:
            seen.add(f)
            result.append(f)
    return result


def find_companion_object_end(source: str) -> int:
    """
    Return the index of the closing brace of the companion object block,
    or -1 if no companion object is found.
    """
    m = re.search(r'\bcompanion\s+object\b', source)
    if not m:
        return -1

    # Walk forward from the companion object keyword, counting braces.
    depth = 0
    i = m.start()
    while i < len(source):
        if source[i] == '{':
            depth += 1
        elif source[i] == '}':
            depth -= 1
            if depth == 0:
                return i
        i += 1
    return -1


def build_stub(field_name: str, alias: str, detector_class: str, lang: str) -> str:
    if lang == "kt":
        return (
            f"\n        // Stub: required by BuiltinIssueRegistry; aliases the generated issue\n"
            f"        @JvmField val {field_name}: Issue = {alias}\n"
        )
    else:  # java
        return (
            f"\n        // Stub: required by BuiltinIssueRegistry; aliases the generated issue\n"
            f"        @com.android.annotations.NonNull\n"
            f"        public static final Issue {field_name} = {alias};\n"
        )


def find_java_issue_fields(source: str) -> list[str]:
    return re.findall(
        r'public\s+static\s+final\s+Issue\s+([A-Z_][A-Z_0-9]*)\s*=',
        source,
    )


def find_java_companion_end(source: str) -> int:
    """Find the last closing brace of the class (Java doesn't have companion objects)."""
    # Find the last } in the file — that closes the class
    return source.rfind('}')


def inject(generated_file: Path, detector_class: str) -> bool:
    """
    Patch generated_file in-place to add missing Issue field stubs.
    Returns True if any stubs were injected.
    """
    registry = json.loads(REGISTRY_FIELDS_JSON.read_text())
    expected_fields: list[str] = registry.get(detector_class, [])
    if not expected_fields:
        return False  # single-issue detector — nothing to do

    source = generated_file.read_text(encoding="utf-8")
    lang = generated_file.suffix.lstrip(".")

    if lang == "kt":
        defined = find_defined_issue_fields(source)
    else:
        defined = find_java_issue_fields(source)

    missing = [f for f in expected_fields if f not in defined]
    if not missing:
        return False  # already complete

    if not defined:
        print(
            f"inject_stubs: WARNING — no @JvmField Issue fields found in {generated_file.name}; "
            f"cannot inject stubs (no alias target)",
            file=sys.stderr,
        )
        return False

    alias = defined[0]  # alias stubs to the first (real) issue field the model defined

    if lang == "kt":
        companion_end = find_companion_object_end(source)
        if companion_end == -1:
            # No companion object — wrap the issue declaration in one if present at top level
            # Fallback: append a new companion object at end of class
            class_end = source.rfind("}")
            if class_end == -1:
                print("inject_stubs: ERROR — cannot find class body end", file=sys.stderr)
                return False
            stubs_body = "".join(
                build_stub(f, alias, detector_class, lang) for f in missing
            )
            injection = f"\n    companion object {{\n{stubs_body}    }}\n"
            source = source[:class_end] + injection + source[class_end:]
        else:
            stubs = "".join(build_stub(f, alias, detector_class, lang) for f in missing)
            source = source[:companion_end] + stubs + "    " + source[companion_end:]
    else:
        # Java: insert before the final closing brace of the class
        insert_pos = find_java_companion_end(source)
        if insert_pos == -1:
            print("inject_stubs: ERROR — cannot find class body end", file=sys.stderr)
            return False
        stubs = "".join(build_stub(f, alias, detector_class, lang) for f in missing)
        source = source[:insert_pos] + stubs + source[insert_pos:]

    generated_file.write_text(source, encoding="utf-8")
    print(
        f"inject_stubs: injected {len(missing)} stub(s) into {generated_file.name} "
        f"[{', '.join(missing)}] -> aliased to {alias!r}",
        file=sys.stderr,
    )
    return True


if __name__ == "__main__":
    if len(sys.argv) != 3:
        print(f"Usage: {sys.argv[0]} <generated_file> <detector_class>", file=sys.stderr)
        sys.exit(1)

    generated_file = Path(sys.argv[1])
    detector_class = sys.argv[2]

    if not generated_file.exists():
        print(f"inject_stubs: ERROR — file not found: {generated_file}", file=sys.stderr)
        sys.exit(1)

    inject(generated_file, detector_class)
