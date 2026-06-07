"""
lintgen.rag.knowledge_base — LintAPIKnowledgeBase

Five-tier retrieval:
  Tier 1 — Interface index (hand-curated, ~43 entries)
            Exact-match on scanner_interfaces first; semantic search fallback.
  Tier 2 — Full Lint API index (~153 entries: Context, JavaContext, LintFix, etc.)
            FAISS cosine retrieval, top-k.
  Tier 3 — API guide docs (223 section chunks from the official Lint API guide)
            FAISS cosine retrieval, top-k.
  Tier 4 — UAST / PSI method index (auto-extracted from intellij-community)
            FAISS cosine retrieval, top-k.
  Tier 5 — SdkConstants used in ground-truth detectors, with string values
            FAISS cosine retrieval, top-k.

Index is built once by build_corpus.py and persisted under rag/index/.
"""

from __future__ import annotations

import json
from pathlib import Path
from typing import Optional

# Populated by get_knowledge_base() on first call.
_KB_SINGLETON: Optional["LintAPIKnowledgeBase"] = None

INDEX_DIR  = Path(__file__).parent / "index"
CORPUS_DIR = Path(__file__).parent / "corpus"

EMBEDDING_MODEL  = "BAAI/bge-large-en-v1.5"
DEFAULT_TOP_K    = 5
SCORE_THRESHOLD  = 0.6
_TIER3_CHAR_LIMIT = 1600


def _smart_truncate(content: str, limit: int = _TIER3_CHAR_LIMIT) -> str:
    """
    Truncate a Tier-3 guide chunk at a logical boundary rather than a hard
    character cut.

    Priority:
      1. No truncation needed          — return as-is.
      2. End of a code fence           — break after the closing fence line
                                         (Lint guide uses ~~~ fences).
      3. End of a paragraph            — break at the last blank line.
      4. End of a sentence             — break at the last ". " or ".\n".
      5. Hard cut                      — fall back to the character limit.
    """
    if len(content) <= limit:
        return content

    window = content[:limit]

    # 1. Code fence boundary (~~~~~~...kotlin or plain ~~~)
    fence_pos = window.rfind("~~~~~~~~~~~~~~~~~~")
    if fence_pos > limit // 2:
        end = fence_pos + window[fence_pos:].find("\n") + 1
        return content[:end].rstrip() + "\n... (truncated)"

    # 2. Paragraph boundary
    para_pos = window.rfind("\n\n")
    if para_pos > limit // 2:
        return content[:para_pos] + "\n... (truncated)"

    # 3. Sentence boundary
    sent_pos = max(window.rfind(". "), window.rfind(".\n"))
    if sent_pos > limit // 2:
        return content[:sent_pos + 1] + "\n... (truncated)"

    # 4. Hard cut
    return window + "\n... (truncated)"


class LintAPIKnowledgeBase:
    """
    Retrieves relevant Lint API methods, utility calls, and guide sections
    for a given benchmark instance.

    Usage:
        kb = LintAPIKnowledgeBase.load()
        context = kb.format_context(instance)
    """

    def __init__(
        self,
        tier1_store,
        tier2_store,
        tier3_store,
        tier4_store,
        tier5_store,
        tier1_entries: list[dict],
    ) -> None:
        self._tier1_store   = tier1_store    # FAISS: interface methods
        self._tier2_store   = tier2_store    # FAISS: full API (Context, LintFix, …)
        self._tier3_store   = tier3_store    # FAISS: API guide doc sections
        self._tier4_store   = tier4_store    # FAISS: UAST / PSI methods
        self._tier5_store   = tier5_store    # FAISS: SdkConstants
        self._tier1_entries = tier1_entries  # raw list for exact-match on scanner_interfaces

    # ------------------------------------------------------------------
    # Construction
    # ------------------------------------------------------------------

    @classmethod
    def load(cls, index_dir: Path = INDEX_DIR) -> "LintAPIKnowledgeBase":
        """Load a pre-built index from disk. Raises FileNotFoundError if not built yet."""
        tier1_path = index_dir / "tier1"
        tier2_path = index_dir / "tier2"
        tier3_path = index_dir / "tier3"
        tier4_path = index_dir / "tier4"

        for path in (tier1_path, tier2_path, tier3_path):
            if not path.exists():
                raise FileNotFoundError(
                    f"FAISS index not found at {path}. "
                    "Run: lintgen build-index"
                )

        try:
            from langchain_huggingface import HuggingFaceEmbeddings
            from langchain_community.vectorstores import FAISS
        except ImportError:
            raise SystemExit(
                "RAG dependencies not installed. Run: uv sync  (or pip install lintgen)"
            )

        embeddings = HuggingFaceEmbeddings(model_name=EMBEDDING_MODEL)

        tier1_store = FAISS.load_local(
            str(tier1_path), embeddings, allow_dangerous_deserialization=True
        )
        tier2_store = FAISS.load_local(
            str(tier2_path), embeddings, allow_dangerous_deserialization=True
        )
        tier3_store = FAISS.load_local(
            str(tier3_path), embeddings, allow_dangerous_deserialization=True
        )
        tier4_store = None
        if tier4_path.exists():
            tier4_store = FAISS.load_local(
                str(tier4_path), embeddings, allow_dangerous_deserialization=True
            )

        tier5_path  = index_dir / "tier5"
        tier5_store = None
        if tier5_path.exists():
            tier5_store = FAISS.load_local(
                str(tier5_path), embeddings, allow_dangerous_deserialization=True
            )

        tier1_entries = json.loads((CORPUS_DIR / "lint_interfaces.json").read_text())
        return cls(tier1_store, tier2_store, tier3_store, tier4_store, tier5_store, tier1_entries)

    # ------------------------------------------------------------------
    # Retrieval
    # ------------------------------------------------------------------

    def retrieve(
        self,
        instance: dict,
        k: int = DEFAULT_TOP_K,
        score_threshold: float = SCORE_THRESHOLD,
    ) -> dict[str, list[dict]]:
        """
        Return {"tier1": [...], "tier2": [...], "tier3": [...]} for instance.

        Tier 1: exact-match on scanner_interfaces, semantic fallback.
        Tier 2: dense search — API methods and utilities.
        Tier 3: dense search — API guide doc sections.
        """
        scanner_ifaces = instance.get("scanner_interfaces", [])
        nl_spec        = instance.get("nl_spec", "")
        query          = f"{nl_spec} [{' '.join(scanner_ifaces)}]"

        # Tier 1 — exact match first
        tier1_exact = [
            e for e in self._tier1_entries
            if e.get("interface", "").split(".")[-1] in scanner_ifaces
        ]
        # Tier 1 — semantic fallback
        if not tier1_exact and self._tier1_store:
            raw = self._tier1_store.similarity_search_with_relevance_scores(query, k=k)
            tier1_exact = [
                doc.metadata for doc, score in raw
                if score >= score_threshold
            ]

        # Tier 2 — full API semantic search
        tier2_docs = []
        if self._tier2_store:
            raw2 = self._tier2_store.similarity_search_with_relevance_scores(query, k=k)
            tier2_docs = [
                doc.metadata for doc, score in raw2
                if score >= score_threshold
            ]

        # Tier 3 — guide doc sections
        tier3_docs = []
        if self._tier3_store:
            raw3 = self._tier3_store.similarity_search_with_relevance_scores(query, k=k)
            tier3_docs = [
                doc.metadata for doc, score in raw3
                if score >= score_threshold
            ]

        # Tier 4 — UAST / PSI methods
        tier4_docs = []
        if self._tier4_store:
            raw4 = self._tier4_store.similarity_search_with_relevance_scores(query, k=k)
            tier4_docs = [
                doc.metadata for doc, score in raw4
                if score >= score_threshold
            ]

        # Tier 5 — SdkConstants
        tier5_docs = []
        if self._tier5_store:
            raw5 = self._tier5_store.similarity_search_with_relevance_scores(query, k=k)
            tier5_docs = [
                doc.metadata for doc, score in raw5
                if score >= score_threshold
            ]

        return {"tier1": tier1_exact, "tier2": tier2_docs, "tier3": tier3_docs, "tier4": tier4_docs, "tier5": tier5_docs}

    def format_context(
        self,
        instance: dict,
        k: int = DEFAULT_TOP_K,
        include_tiers: frozenset[int] = frozenset({1, 2, 3, 4, 5}),
    ) -> str:
        """
        Return a formatted string ready for prompt injection.

        include_tiers controls which retrieval tiers are rendered:
          {1,2,4,5}       → base+apis:      interface signatures + API methods +
                                             UAST/PSI methods + SdkConstants + imports
          {1,2,3,4,5}     → base+apis+docs: all tiers, additionally including
                                             guide doc excerpts (Tier 3)
          {3}             → base+docs:      guide doc excerpts only (use k=20)

        Sections:
          [Tier 1] Scanner interface methods to override (with interface declaration hint)
          [Tier 2] Available context / utility API methods + required imports
          [Tier 3] Relevant API guide excerpts          (omitted for base+apis)
          [Tier 4] UAST / PSI methods
          [Tier 5] SdkConstants
        """
        results = self.retrieve(instance, k=k)
        tier1   = results["tier1"] if 1 in include_tiers else []
        tier2   = results["tier2"] if 2 in include_tiers else []
        tier3   = results["tier3"] if 3 in include_tiers else []
        tier4   = results["tier4"] if 4 in include_tiers else []
        tier5   = results["tier5"] if 5 in include_tiers else []

        if not tier1 and not tier2 and not tier3 and not tier4 and not tier5:
            return ""

        lines: list[str] = ["Relevant Lint API methods:", "─" * 42]

        if tier1:
            # Group by interface so the declaration hint and description are
            # shown once per interface, then its methods listed beneath.
            seen_ifaces: dict[str, bool] = {}
            for entry in tier1:
                iface_fqn  = entry.get("interface", "")
                iface_name = iface_fqn.split(".")[-1]

                if iface_fqn not in seen_ifaces:
                    seen_ifaces[iface_fqn] = True
                    lines.append(f"\n// Implement: class YourDetector : Detector(), {iface_name}")
                    # Interface description: first two sentences
                    iface_desc = entry.get("interface_description", "")
                    if iface_desc:
                        # Cap at 400 chars, breaking at a word boundary
                        if len(iface_desc) > 400:
                            window = iface_desc[:400]
                            # Try sentence-like boundaries first
                            for sep in (". ", ".) ", ".\n", "; "):
                                cut = window.rfind(sep)
                                if cut > 200:
                                    iface_desc = iface_desc[:cut + len(sep)].strip()
                                    break
                            else:
                                # Fall back to last word boundary
                                cut = window.rfind(" ")
                                iface_desc = iface_desc[:cut].strip() if cut > 0 else window
                        lines.append(f"// {iface_desc.strip()}")
                    lines.append(f"// Scanner interface methods to override:")

                lines.append(f"  {entry.get('signature', '')}")
                if entry.get("method_description"):
                    desc = entry["method_description"].split(".")[0] + "."
                    lines.append(f"  // {desc}")
                lines.append("")

        if tier2:
            lines.append("// Available context / utility methods:")
            for entry in tier2:
                lines.append(f"  {entry.get('signature', '')}")
                if entry.get("method_description"):
                    desc = entry["method_description"].split(".")[0] + "."
                    lines.append(f"  // {desc}")
                lines.append("")

        if tier4:
            lines.append("// UAST / PSI methods:")
            for entry in tier4:
                lines.append(f"  {entry.get('signature', '')}")
                if entry.get("method_description"):
                    desc = entry["method_description"].split(".")[0] + "."
                    lines.append(f"  // {desc}")
                lines.append("")

        if tier5:
            lines.append("// Relevant SdkConstants:")
            for entry in tier5:
                lines.append(f"  SdkConstants.{entry['constant']} = \"{entry['value']}\"")
            lines.append("")

        # Deduplicated import list from tier1 + tier2 + tier4 + tier5
        all_imports: list[str] = []
        seen: set[str] = set()
        for entry in tier1 + tier2 + tier4 + tier5:
            for imp in entry.get("imports", []):
                if imp not in seen:
                    seen.add(imp)
                    all_imports.append(imp)

        if all_imports:
            lines.append("─" * 42)
            lines.append("Required imports:")
            for imp in sorted(all_imports):
                lines.append(f"  import {imp}")

        if tier3:
            lines.append("\n" + "─" * 42)
            lines.append("Relevant API guide excerpts:")
            lines.append("─" * 42)
            for entry in tier3:
                lines.append(f"\n### {entry.get('title', '')} ({entry.get('file', '')})")
                lines.append(_smart_truncate(entry.get("content", "")))

        return "\n".join(lines)


def get_knowledge_base(index_dir: Path = INDEX_DIR) -> LintAPIKnowledgeBase:
    """Singleton accessor — loads the index once per process."""
    global _KB_SINGLETON
    if _KB_SINGLETON is None:
        _KB_SINGLETON = LintAPIKnowledgeBase.load(index_dir)
    return _KB_SINGLETON
