#!/usr/bin/env python3
"""
patch_test_loose.py — rewrite a Lint test file for loose evaluation.

For each test method in the given method list:
  • Extracts the issue IDs expected by .expect(...) calls.
  • Replaces each .expect(exactString) with .expectContains("[IssueId]") chains.
  • Leaves .expectClean() untouched (negative tests still enforce no false positives).
  • Variable-form: val/String expected = ...; .expect(expected) is handled too.

Usage:
    python3 patch_test_loose.py <test_file> <comma_separated_methods>

Exits 0 always; warns to stderr if a method isn't found.
"""

import re
import sys
from pathlib import Path

# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

ISSUE_ID_RE = re.compile(r'\[([A-Z][A-Za-z0-9_]+)\]')
MSG_LINE_RE  = re.compile(r':\s*(?:Warning|Error|Information|Fatal):\s*.+?\s*\[[A-Z][A-Za-z0-9_]+\]')


def _method_body_span(src: str, method_name: str) -> tuple[int, int] | None:
    """Return (start, end) char indices of the entire method (declaration + body)."""
    m = re.search(r'(?:fun|void|public\s+void)\s+' + re.escape(method_name) + r'\s*\(', src)
    if not m:
        return None
    brace_pos = src.find('{', m.end())
    if brace_pos == -1:
        return None
    depth = 1
    i = brace_pos + 1
    while i < len(src) and depth > 0:
        if src[i] == '{':
            depth += 1
        elif src[i] == '}':
            depth -= 1
        i += 1
    return m.start(), i


def _extract_issue_ids_from_block(block: str) -> list[str]:
    """Return unique issue IDs that appear on diagnostic lines in the lint output block."""
    seen: set[str] = set()
    ids: list[str] = []
    for line in block.splitlines():
        if MSG_LINE_RE.search(line):
            for m in ISSUE_ID_RE.finditer(line):
                iid = m.group(1)
                if iid not in seen:
                    seen.add(iid)
                    ids.append(iid)
    return ids


def _collect_issue_ids(method_body: str) -> list[str]:
    """Pull all expected issue IDs from all .expect(…) blocks in a method body."""
    seen: set[str] = set()
    ids: list[str] = []

    def _record(block: str) -> None:
        for iid in _extract_issue_ids_from_block(block):
            if iid not in seen:
                seen.add(iid)
                ids.append(iid)

    # 1. Triple-quoted Kotlin: .expect("""…""")
    for m in re.finditer(r'\.expect\s*\(\s*"""(.*?)"""', method_body, re.DOTALL):
        _record(m.group(1))

    # 2. Single-quoted direct: .expect("…")
    for m in re.finditer(r'\.expect\s*\(\s*"((?:[^"\\]|\\.)*?)"\s*\)', method_body):
        _record(m.group(1).replace("\\n", "\n"))

    # 3. val/var/String expected = "" + "…\n" + …  (concat form)
    for m in re.finditer(
        r'(?:val|var|String)\s+expected\s*=\s*((?:\s*(?:""|"(?:[^"\\]|\\.)*")\s*\+?\s*)+)',
        method_body,
    ):
        parts = re.findall(r'"((?:[^"\\]|\\.)*?)"', m.group(1))
        joined = "".join(p.replace("\\n", "\n") for p in parts)
        _record(joined)

    # 4. val/var expected = """…"""  (triple-quoted assigned to variable)
    for m in re.finditer(r'(?:val|var)\s+expected\s*=\s*"""(.*?)"""', method_body, re.DOTALL):
        _record(m.group(1))

    # 5a. String expected = String.format("" + "…\n" + …, …)  (format-call wrapper)
    for m in re.finditer(
        r'String\s+expected\s*=\s*String\.format\(\s*((?:\s*(?:""|"(?:[^"\\]|\\.)*")\s*\+?\s*)+)',
        method_body,
    ):
        parts = re.findall(r'"((?:[^"\\]|\\.)*?)"', m.group(1))
        joined = "".join(p.replace("\\n", "\n") for p in parts)
        _record(joined)

    # 5b. .expect("" + "…\n" + …)  (inline concat)
    for m in re.finditer(r'\.expect\s*\(\s*(?:""|[A-Z_][A-Z_0-9]*)\s*\+', method_body):
        start = method_body.index('(', m.start()) + 1
        depth = 1
        i = start
        while i < len(method_body) and depth > 0:
            if method_body[i] == '(':
                depth += 1
            elif method_body[i] == ')':
                depth -= 1
            i += 1
        block_src = method_body[start:i - 1]
        parts = re.findall(r'"((?:[^"\\]|\\.)*?)"', block_src)
        joined = "".join(p.replace("\\n", "\n") for p in parts)
        _record(joined)

    return ids


def _build_replacement(issue_ids: list[str]) -> str:
    """Return a chained .expectContains(…) expression for the given issue IDs."""
    if not issue_ids:
        return ".run()"  # nothing to check — just run without assertion
    return "".join(f'.expectContains("[{iid}]")' for iid in issue_ids)


def _strip_fix_diffs(body: str) -> str:
    """
    Remove .expectFixDiffs(…) calls entirely — fix-suggestion text is not
    evaluated in loose mode; the benchmark tests detection, not fix generation.
    Handles triple-quoted, single-quoted, variable, and concat forms.
    """
    # 1. Triple-quoted .expectFixDiffs("""…""")
    body = re.sub(
        r'\.expectFixDiffs\s*\(\s*""".*?"""\s*\)',
        '',
        body,
        flags=re.DOTALL,
    )
    # 2. Single-quoted .expectFixDiffs("…")
    body = re.sub(
        r'\.expectFixDiffs\s*\(\s*"(?:[^"\\]|\\.)*?"\s*\)',
        '',
        body,
    )
    # 3. Variable / concat form — walk balanced parens
    out = []
    pos = 0
    for m in re.finditer(r'\.expectFixDiffs\s*\(', body):
        paren_start = body.index('(', m.start())
        depth = 1
        i = paren_start + 1
        while i < len(body) and depth > 0:
            if body[i] == '(':
                depth += 1
            elif body[i] == ')':
                depth -= 1
            i += 1
        out.append(body[pos:m.start()])
        pos = i
    out.append(body[pos:])
    return "".join(out)


def _rewrite_method(src: str, start: int, end: int) -> str:
    """
    Rewrite the method body in src[start:end]:
      - replace .expect(exactString) with .expectContains("[IssueId]") chains
      - strip .expectFixDiffs(…) — fix text is not evaluated in loose mode
      - leave .expectClean() untouched
    """
    method_body = src[start:end]
    issue_ids = _collect_issue_ids(method_body)

    if not issue_ids:
        # No positive assertions found — nothing to patch
        return src

    contains_chain = _build_replacement(issue_ids)

    def _replace_expect(body: str) -> str:
        # 1. Triple-quoted .expect("""…""")  — must consume the closing ) too
        body = re.sub(
            r'\.expect\s*\(\s*""".*?"""\s*\)',
            contains_chain,
            body,
            flags=re.DOTALL,
        )
        # 2. Single-quoted .expect("…")
        body = re.sub(
            r'\.expect\s*\(\s*"(?:[^"\\]|\\.)*?"\s*\)',
            contains_chain,
            body,
        )
        # 3. Variable form .expect(expected) — must come after removing the declaration
        body = re.sub(
            r'\.expect\s*\(\s*expected\s*\)',
            contains_chain,
            body,
        )
        # 4. Inline concat .expect("" + … ) — walk balanced parens and replace whole call
        def _replace_inline_concat(b: str) -> str:
            out = []
            pos = 0
            for m in re.finditer(r'\.expect\s*\(\s*(?:""|[A-Z_][A-Z_0-9]*)\s*\+', b):
                paren_start = b.index('(', m.start())
                depth = 1
                i = paren_start + 1
                while i < len(b) and depth > 0:
                    if b[i] == '(':
                        depth += 1
                    elif b[i] == ')':
                        depth -= 1
                    i += 1
                out.append(b[pos:m.start()])
                out.append(contains_chain)
                pos = i
            out.append(b[pos:])
            return "".join(out)

        body = _replace_inline_concat(body)
        return body

    patched_body = _replace_expect(method_body)
    patched_body = _strip_fix_diffs(patched_body)
    return src[:start] + patched_body + src[end:]


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

def patch_file(test_file: Path, methods: list[str]) -> None:
    src = test_file.read_text(encoding="utf-8")
    original = src

    for method_name in methods:
        span = _method_body_span(src, method_name)
        if span is None:
            print(
                f"patch_test_loose: WARNING — method {method_name!r} not found in {test_file.name}",
                file=sys.stderr,
            )
            continue
        src = _rewrite_method(src, span[0], span[1])

    if src != original:
        test_file.write_text(src, encoding="utf-8")
        print(
            f"patch_test_loose: patched {test_file.name} "
            f"(methods: {', '.join(methods)})",
            file=sys.stderr,
        )
    else:
        print(
            f"patch_test_loose: no changes needed in {test_file.name}",
            file=sys.stderr,
        )


if __name__ == "__main__":
    if len(sys.argv) != 3:
        print(f"Usage: {sys.argv[0]} <test_file> <comma_separated_methods>", file=sys.stderr)
        sys.exit(1)

    test_file = Path(sys.argv[1])
    if not test_file.exists():
        print(f"patch_test_loose: ERROR — file not found: {test_file}", file=sys.stderr)
        sys.exit(1)

    methods = [m.strip() for m in sys.argv[2].split(",") if m.strip()]
    patch_file(test_file, methods)
