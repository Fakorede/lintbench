"""
lintgen.rag.build_corpus — build the three-tier Lint API FAISS index.

Tier 1 (lint_interfaces.json): hand-curated scanner interface methods.
Tier 2 (lint_fullapi.json):    hand-curated Context/JavaContext/LintFix/etc. API entries.
Tier 3 (lint_guides.json):     Official Lint API guide docs, chunked with
                                RecursiveCharacterTextSplitter (~1600 chars,
                                200-char overlap, heading-aware separators).

Run:
  lintgen build-index
  lintgen build-index --out-dir src/lintgen/rag/index/
"""

from __future__ import annotations

import argparse
import json
import re
from pathlib import Path

CORPUS_DIR = Path(__file__).parent / "corpus"
INDEX_DIR  = Path(__file__).parent / "index"


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

    out_dir = Path(args.out_dir)
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

    print("\nIndex build complete. Run `lintgen generate` to use it.")
