#!/usr/bin/env python3
"""
02_extract_specs.py
-------------------
Extracts natural language specifications from Android Lint detector source files.

For each paired check, parses the Issue.create(...) call(s) to extract:
  - id                (e.g. "ShortAlarm")
  - briefDescription  (e.g. "Short or Frequent Alarm")
  - explanation       (the long-form NL description)
  - category          (e.g. CORRECTNESS, SECURITY, PERFORMANCE)
  - severity          (ERROR, WARNING, INFORMATIONAL, FATAL)
  - priority          (1-10 integer)
  - moreInfo URLs     (optional reference links)

Handles both Kotlin (named parameters) and Java (positional parameters) styles.

Input:  lint_pairs.json  (produced by 01_pair_checks.py)
Output: lint_specs.json  (one record per Issue, with spec fields + source metadata)

Usage:
    python3 02_extract_specs.py [--pairs PATH] [--checks-dir PATH] [--out PATH]
"""

import argparse
import json
import re
import textwrap
from pathlib import Path

REPO_ROOT  = Path(__file__).resolve().parent.parent.parent / "lint_codebase" / "base"
CHECKS_DIR = REPO_ROOT / "lint/libs/lint-checks/src/main/java/com/android/tools/lint/checks"
PAIRS_FILE = Path("data/lint_pairs.json")
OUT_FILE   = Path("data/lint_specs.json")


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def clean_text(raw: str) -> str:
    """
    Clean up multiline string content extracted from source:
      - Remove line-continuation backslashes (Kotlin triple-quoted strings)
      - Strip leading/trailing whitespace from each line
      - Collapse runs of blank lines to a single blank line
      - Strip surrounding whitespace
    """
    # Remove Kotlin string continuation backslashes at end of lines
    raw = re.sub(r"\\\s*\n\s*", " ", raw)
    # Strip per-line leading whitespace (indentation from source)
    lines = [line.strip() for line in raw.splitlines()]
    # Collapse multiple blank lines
    collapsed = re.sub(r"\n{3,}", "\n\n", "\n".join(lines))
    return collapsed.strip()


def extract_string_value(text: str, start: int) -> tuple[str, int]:
    """
    Extract a string value beginning at `start` in `text`.
    Handles:
      - Triple-quoted Kotlin strings:  \"\"\" ... \"\"\"
      - Double-quoted strings:          \"...\"  (with \" escapes)
      - Java string concatenation:      "foo" + "bar" + ...
    Returns (extracted_string, end_index).
    """
    i = start
    # Skip whitespace
    while i < len(text) and text[i] in " \t\n\r":
        i += 1

    # Triple-quoted Kotlin string
    if text[i:i+3] == '"""':
        end = text.find('"""', i + 3)
        if end == -1:
            return "", i
        content = text[i+3:end]
        return clean_text(content), end + 3

    # Double-quoted string (possibly concatenated with +)
    result_parts = []
    while i < len(text) and text[i] == '"':
        i += 1  # skip opening quote
        part = []
        while i < len(text):
            ch = text[i]
            if ch == '\\' and i + 1 < len(text):
                part.append(text[i+1])
                i += 2
            elif ch == '"':
                i += 1
                break
            else:
                part.append(ch)
                i += 1
        result_parts.append("".join(part))
        # Skip whitespace and + for Java string concatenation
        j = i
        while j < len(text) and text[j] in " \t\n\r":
            j += 1
        if j < len(text) and text[j] == '+':
            j += 1
            while j < len(text) and text[j] in " \t\n\r":
                j += 1
            if j < len(text) and text[j] == '"':
                i = j
                continue
        i = j
        break

    return clean_text(" ".join(result_parts)), i


def extract_kotlin_issue(block: str) -> dict | None:
    """
    Parse a Kotlin-style Issue.create(id = ..., briefDescription = ..., explanation = ...) block.
    Returns a dict with the extracted fields, or None if parsing fails.
    """
    result = {}

    for field in ("id", "briefDescription", "explanation", "category", "severity", "priority", "moreInfo"):
        # Match:  fieldName = <value>
        pattern = re.compile(rf'\b{field}\s*=\s*', re.DOTALL)
        m = pattern.search(block)
        if not m:
            continue
        val_start = m.end()

        if field in ("id", "briefDescription", "explanation", "moreInfo"):
            val, _ = extract_string_value(block, val_start)
            result[field] = val
        elif field in ("category", "severity"):
            # e.g.  Category.SECURITY  or  Severity.WARNING
            m2 = re.match(r'[\w.]+', block[val_start:])
            result[field] = m2.group().split(".")[-1] if m2 else ""
        elif field == "priority":
            m2 = re.match(r'\d+', block[val_start:])
            result[field] = int(m2.group()) if m2 else None

    # Collect all moreInfo / addMoreInfo URLs
    urls = re.findall(r'(?:moreInfo|addMoreInfo)\s*[=(]\s*"([^"]+)"', block)
    result["moreInfo_urls"] = urls

    if "id" not in result or "explanation" not in result:
        return None
    return result


def split_java_args(block: str) -> list[str]:
    """
    Split a Java/Kotlin Issue.create(...) block into its top-level comma-separated
    arguments, correctly tracking string literals (including escapes) and paren depth.
    """
    paren = block.find("(")
    if paren == -1:
        return []

    args: list[str] = []
    current: list[str] = []
    depth = 0
    in_str = False
    i = paren + 1

    while i < len(block):
        ch = block[i]

        if ch == '"' and not in_str:
            if block[i:i+3] == '"""':
                end = block.find('"""', i + 3)
                if end == -1:
                    break
                current.append(block[i:end+3])
                i = end + 3
                continue
            else:
                in_str = True
                current.append(ch)
        elif ch == '"' and in_str:
            in_str = False
            current.append(ch)
        elif ch == '\\' and in_str:
            current.append(ch)
            i += 1
            if i < len(block):
                current.append(block[i])
        elif not in_str and ch in '({[':
            depth += 1
            current.append(ch)
        elif not in_str and ch in ')}]':
            if depth == 0:
                break
            depth -= 1
            current.append(ch)
        elif not in_str and ch == ',' and depth == 0:
            args.append("".join(current).strip())
            current = []
        else:
            current.append(ch)
        i += 1

    if current:
        args.append("".join(current).strip())

    return args


def resolve_java_string_arg(raw: str) -> str:
    """
    Resolve a Java string argument that may be a single literal or a concatenation.
    Handles:  "foo" + "bar"  and  "foo"\n  + " bar"
    """
    raw = raw.strip()
    if not raw:
        return ""
    val, _ = extract_string_value(raw, 0)
    return val


def extract_java_issue(block: str) -> dict | None:
    """
    Parse a Java-style Issue.create("id", "briefDescription", "explanation",
    Category.X, priority, Severity.Y, impl) positional argument block.
    Returns a dict with extracted fields, or None if parsing fails.
    """
    args = split_java_args(block)
    if len(args) < 3:
        return None

    result: dict = {}
    result["id"]               = resolve_java_string_arg(args[0])
    result["briefDescription"] = resolve_java_string_arg(args[1])
    result["explanation"]      = resolve_java_string_arg(args[2])

    if len(args) > 3:
        # e.g. "Category.CORRECTNESS" → "CORRECTNESS"
        cat_raw = args[3].strip()
        result["category"] = cat_raw.split(".")[-1].strip().rstrip(")")

    if len(args) > 4:
        m = re.search(r'\d+', args[4])
        result["priority"] = int(m.group()) if m else None

    if len(args) > 5:
        sev_raw = args[5].strip()
        result["severity"] = sev_raw.split(".")[-1].strip().rstrip(")")

    # Collect addMoreInfo / moreInfo URLs from the full block
    urls = re.findall(r'(?:moreInfo|addMoreInfo)\s*[=(]\s*"([^"]+)"', block)
    result["moreInfo_urls"] = urls

    if not result.get("id") or not result.get("explanation"):
        return None
    return result


def find_issue_blocks(source: str, lang: str) -> list[str]:
    """
    Find all Issue.create(...) blocks in the source file, including any
    chained builder calls (.addMoreInfo(), .moreInfo(), .setAndroidSpecific(), etc.)
    that follow the closing paren on the same expression.

    Returns a list of raw text blocks (from 'Issue.create' through the full
    builder chain ending at ; or assignment boundary).
    """
    blocks = []
    pattern = re.compile(r'Issue\.create\s*\(')
    for m in pattern.finditer(source):
        start = m.start()
        # Walk forward to find the matching closing paren of Issue.create(...)
        depth = 0
        i = m.end() - 1  # point at opening '('
        while i < len(source):
            ch = source[i]
            if ch == '(':
                depth += 1
            elif ch == ')':
                depth -= 1
                if depth == 0:
                    break
            elif ch == '"':
                # Skip over string literals to avoid counting parens inside strings
                if source[i:i+3] == '"""':
                    end = source.find('"""', i + 3)
                    i = end + 2 if end != -1 else i
                else:
                    i += 1
                    while i < len(source) and source[i] != '"':
                        if source[i] == '\\':
                            i += 1
                        i += 1
            i += 1

        close_paren = i  # position of ')' that closes Issue.create(...)

        # Now extend the block to capture chained .addMoreInfo(...) / .moreInfo(...)
        # calls. These appear as:  )\n  .addMoreInfo("url")\n  .addMoreInfo("url2")
        # We scan forward consuming whitespace and dot-method chains until we hit
        # a semicolon, a blank line, or a line that doesn't start with '.'
        j = close_paren + 1
        # Capture up to 800 chars of chained calls (more than enough for any real case)
        chain_end = close_paren + 1
        limit = min(len(source), close_paren + 800)
        while j < limit:
            # Skip whitespace
            while j < limit and source[j] in ' \t\n\r':
                j += 1
            # If next non-whitespace char is '.' it's a chained method call
            if j < limit and source[j] == '.':
                # Consume through the closing paren of this call
                depth2 = 0
                in_str2 = False
                while j < limit:
                    ch2 = source[j]
                    if ch2 == '"'  and not in_str2:
                        in_str2 = True
                    elif ch2 == '"'  and in_str2:
                        in_str2 = False
                    elif ch2 == '\\' and in_str2:
                        j += 1
                    elif not in_str2 and ch2 == '(':
                        depth2 += 1
                    elif not in_str2 and ch2 == ')':
                        depth2 -= 1
                        if depth2 == 0:
                            chain_end = j + 1
                            j += 1
                            break
                    j += 1
            else:
                break  # no more chained calls

        blocks.append(source[start:chain_end])
    return blocks


def parse_file(path: Path, lang: str) -> list[dict]:
    """Parse all Issue declarations from a single detector source file."""
    try:
        source = path.read_text(encoding="utf-8", errors="replace")
    except Exception as e:
        return []

    blocks = find_issue_blocks(source, lang)
    issues = []
    for block in blocks:
        parsed = extract_kotlin_issue(block) if lang == "kt" else extract_java_issue(block)
        if parsed and parsed.get("id") and parsed.get("explanation"):
            issues.append(parsed)
    return issues


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

def main(pairs_path: Path, checks_dir: Path, out_path: Path) -> None:
    if not pairs_path.exists():
        raise SystemExit(f"ERROR: pairs file not found: {pairs_path}\n"
                         f"       Run 01_pair_checks.py first.")
    if not checks_dir.exists():
        raise SystemExit(f"ERROR: checks dir not found: {checks_dir}")

    pairs_data = json.loads(pairs_path.read_text())
    paired     = pairs_data["paired"]

    records    = []
    no_issues  = []
    parse_errs = []

    for entry in paired:
        check_path = checks_dir / entry["check_file"]
        lang       = entry["check_lang"]

        if not check_path.exists():
            parse_errs.append(entry["check_file"])
            continue

        issues = parse_file(check_path, lang)

        if not issues:
            no_issues.append(entry["check_file"])
            continue

        for issue in issues:
            records.append({
                # Source metadata
                "detector":   entry["check"],
                "check_file": entry["check_file"],
                "check_lang": entry["check_lang"],
                "test_file":  entry["test_file"],
                # Extracted spec fields
                "issue_id":          issue.get("id", ""),
                "brief_description": issue.get("briefDescription", ""),
                "explanation":       issue.get("explanation", ""),
                "category":          issue.get("category", ""),
                "severity":          issue.get("severity", ""),
                "priority":          issue.get("priority"),
                "more_info_urls":    issue.get("moreInfo_urls", []),
                # Derived: combined NL spec for use as LLM prompt
                "nl_spec": (
                    f"{issue.get('briefDescription', '').strip()}\n\n"
                    f"{issue.get('explanation', '').strip()}"
                ).strip(),
            })

    # Stats
    unique_detectors = len({r["detector"] for r in records})
    print("=" * 62)
    print(f"  Paired detectors processed:    {len(paired):>4}")
    print(f"  Detectors with 0 issues found: {len(no_issues):>4}")
    print(f"  Parse errors (file not found): {len(parse_errs):>4}")
    print(f"  Total Issue records extracted: {len(records):>4}")
    print(f"  Unique detectors with issues:  {unique_detectors:>4}")
    print("=" * 62)

    # Category breakdown
    from collections import Counter
    cats = Counter(r["category"] for r in records if r["category"])
    print("\nCategory breakdown:")
    for cat, count in cats.most_common():
        print(f"  {cat:<20s} {count}")

    # Severity breakdown
    sevs = Counter(r["severity"] for r in records if r["severity"])
    print("\nSeverity breakdown:")
    for sev, count in sevs.most_common():
        print(f"  {sev:<20s} {count}")

    # Sample
    print("\nSample extracted specs (first 3):")
    for r in records[:3]:
        print(f"\n  [{r['issue_id']}] {r['brief_description']}")
        preview = r["explanation"][:200].replace("\n", " ")
        print(f"  {preview}{'...' if len(r['explanation']) > 200 else ''}")

    if no_issues:
        print(f"\nDetectors with no parseable issues ({len(no_issues)}):")
        for name in no_issues[:20]:
            print(f"  {name}")

    # Save
    output = {
        "summary": {
            "paired_detectors":   len(paired),
            "total_issues":       len(records),
            "unique_detectors":   unique_detectors,
            "no_issues_found":    len(no_issues),
            "parse_errors":       len(parse_errs),
            "category_counts":    dict(cats.most_common()),
            "severity_counts":    dict(sevs.most_common()),
        },
        "issues": records,
    }
    out_path.write_text(json.dumps(output, indent=2))
    print(f"\nFull results saved to: {out_path}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--pairs",      type=Path, default=PAIRS_FILE,
                        help="lint_pairs.json from 01_pair_checks.py")
    parser.add_argument("--checks-dir", type=Path, default=CHECKS_DIR,
                        help="Path to lint-checks source directory")
    parser.add_argument("--out",        type=Path, default=OUT_FILE,
                        help="Output JSON file path")
    args = parser.parse_args()
    main(args.pairs, args.checks_dir, args.out)
