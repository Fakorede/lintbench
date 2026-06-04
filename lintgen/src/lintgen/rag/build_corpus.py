"""
lintgen.rag.build_corpus — build the two-tier Lint API FAISS index.

Tier 1 (lint_interfaces.json): hand-curated scanner interface methods.
  Written manually in corpus/lint_interfaces.json — not auto-generated.

Tier 2 (lint_fullapi.json): auto-constructed from android-custom-lint-rules source.
  Parses Kotlin/Java source files for public method signatures on key Lint classes.

Run:
  lintgen build-index --source ../android-custom-lint-rules/ --out-dir src/lintgen/rag/index/
"""

from __future__ import annotations

import argparse
import json
import re
from pathlib import Path

CORPUS_DIR = Path(__file__).parent / "corpus"
INDEX_DIR  = Path(__file__).parent / "index"

# Lint API classes to extract from android-custom-lint-rules source
TARGET_CLASSES = {
    "JavaContext", "XmlContext", "GradleContext", "Context",
    "UastScanner", "XmlScanner", "GradleScanner", "BinaryResourceScanner",
    "SourceCodeScanner", "ClassScanner",
    "LintFix", "Location", "Incident", "Scope", "Issue", "Implementation",
}


def _extract_methods_from_source(src_dir: Path) -> list[dict]:
    """
    Walk Kotlin/Java source files and extract public method signatures
    for TARGET_CLASSES. Returns a list of API entry dicts.
    """
    entries: list[dict] = []
    kt_files = list(src_dir.rglob("*.kt")) + list(src_dir.rglob("*.java"))

    # Simple heuristic: find files whose name matches a target class
    for path in kt_files:
        stem = path.stem
        if stem not in TARGET_CLASSES:
            continue
        source = path.read_text(encoding="utf-8", errors="ignore")

        # Extract fun/override fun signatures (Kotlin) or public ... method (Java)
        # Kotlin
        for m in re.finditer(
            r'(?:override\s+)?fun\s+(\w+)\s*\(([^)]*)\)\s*(?::\s*([\w<>?,\s]+))?',
            source,
        ):
            method_name, params, return_type = m.group(1), m.group(2), m.group(3) or "Unit"
            # Skip private/internal/test helpers
            if method_name.startswith("_") or method_name[0].isupper():
                continue
            sig = f"fun {method_name}({params.strip()}): {return_type.strip()}"
            entries.append({
                "class":       stem,
                "method":      method_name,
                "signature":   sig,
                "description": _humanise(method_name),
                "imports":     [_infer_import(stem)],
                "source":      "auto",
            })

    return entries


def _humanise(method_name: str) -> str:
    """Convert camelCase method name to a short human-readable description."""
    words = re.sub(r"([A-Z])", r" \1", method_name).lower().split()
    return " ".join(words).strip()


def _infer_import(class_name: str) -> str:
    _PKG = {
        "JavaContext":   "com.android.tools.lint.client.api.JavaContext",
        "XmlContext":    "com.android.tools.lint.detector.api.XmlContext",
        "GradleContext": "com.android.tools.lint.detector.api.GradleContext",
        "Context":       "com.android.tools.lint.detector.api.Context",
        "LintFix":       "com.android.tools.lint.detector.api.LintFix",
        "Location":      "com.android.tools.lint.detector.api.Location",
        "Incident":      "com.android.tools.lint.detector.api.Incident",
        "Issue":         "com.android.tools.lint.detector.api.Issue",
        "Scope":         "com.android.tools.lint.detector.api.Scope",
        "Implementation": "com.android.tools.lint.detector.api.Implementation",
    }
    return _PKG.get(class_name, f"com.android.tools.lint.detector.api.{class_name}")


def build_index(args: argparse.Namespace) -> None:
    """Entry point called by `lintgen build-index`."""
    try:
        from langchain_community.embeddings import HuggingFaceEmbeddings
        from langchain_community.vectorstores import FAISS
        from langchain_core.documents import Document
    except ImportError:
        raise SystemExit("RAG dependencies not installed. Run: uv sync")

    from .knowledge_base import EMBEDDING_MODEL

    out_dir    = Path(args.out_dir)
    source_dir = Path(args.source)
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
                # Child doc: short text for embedding quality
                page_content=f"{e['interface']} {e['method']} {e.get('description', '')}",
                metadata=e,
            )
            for e in tier1_entries
        ]
        tier1_store = FAISS.from_documents(tier1_docs, embeddings)
        tier1_store.save_local(str(out_dir / "tier1"))
        print(f"Tier 1 index saved → {out_dir / 'tier1'}")

    # ── Tier 2 ────────────────────────────────────────────────────────────────
    # Load hand-written full API entries if available, then merge auto-extracted.
    tier2_path = CORPUS_DIR / "lint_fullapi.json"
    tier2_entries: list[dict] = []
    if tier2_path.exists():
        tier2_entries = json.loads(tier2_path.read_text())
        print(f"Tier 2 (corpus): {len(tier2_entries)} entries loaded from lint_fullapi.json")

    if source_dir.exists():
        auto_entries = _extract_methods_from_source(source_dir)
        # Deduplicate by (class, method)
        existing = {(e["class"], e["method"]) for e in tier2_entries}
        new = [e for e in auto_entries if (e["class"], e["method"]) not in existing]
        tier2_entries.extend(new)
        print(f"Tier 2 (auto): +{len(new)} entries from {source_dir}")
    else:
        print(f"WARNING: source dir {source_dir} not found — Tier 2 uses corpus only.")

    if tier2_entries:
        tier2_docs = [
            Document(
                page_content=f"{e['class']} {e['method']} {e.get('description', '')}",
                metadata=e,
            )
            for e in tier2_entries
        ]
        tier2_store = FAISS.from_documents(tier2_docs, embeddings)
        tier2_store.save_local(str(out_dir / "tier2"))
        print(f"Tier 2 index saved → {out_dir / 'tier2'}  ({len(tier2_entries)} entries)")

    print("\nIndex build complete. Run `lintgen generate` to use it.")
