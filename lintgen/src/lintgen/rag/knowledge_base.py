"""
lintgen.rag.knowledge_base — LintAPIKnowledgeBase

Two-tier retrieval:
  Tier 1 — Interface index (hand-curated, ~50 entries)
            Exact-match on scanner_interfaces first; semantic search fallback.
  Tier 2 — Full Lint API index (auto-constructed from source, ~500-1000 entries)
            FAISS dense retrieval, top-k, parent-child swap.

Index is built once by build_corpus.py and persisted under rag/index/.
"""

from __future__ import annotations

from pathlib import Path
from typing import Optional

# Populated by get_knowledge_base() on first call.
_KB_SINGLETON: Optional["LintAPIKnowledgeBase"] = None

INDEX_DIR = Path(__file__).parent / "index"
CORPUS_DIR = Path(__file__).parent / "corpus"

EMBEDDING_MODEL = "BAAI/bge-large-en-v1.5"
DEFAULT_TOP_K = 5
SCORE_THRESHOLD = 0.4


class LintAPIKnowledgeBase:
    """
    Retrieves relevant Lint API methods and imports for a given benchmark instance.

    Usage:
        kb = LintAPIKnowledgeBase.load()
        context = kb.format_context(instance)
    """

    def __init__(self, tier1_store, tier2_store, tier1_entries: list[dict]) -> None:
        self._tier1_store    = tier1_store     # FAISS vectorstore for interface methods
        self._tier2_store    = tier2_store     # FAISS vectorstore for full API
        self._tier1_entries  = tier1_entries   # raw list for exact-match lookup

    # ------------------------------------------------------------------
    # Construction
    # ------------------------------------------------------------------

    @classmethod
    def load(cls, index_dir: Path = INDEX_DIR) -> "LintAPIKnowledgeBase":
        """Load a pre-built index from disk. Raises FileNotFoundError if not built yet."""
        tier1_path = index_dir / "tier1"
        tier2_path = index_dir / "tier2"
        if not tier1_path.exists() or not tier2_path.exists():
            raise FileNotFoundError(
                f"FAISS index not found at {index_dir}. "
                "Run: lintgen build-index"
            )

        try:
            from langchain_community.embeddings import HuggingFaceEmbeddings
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

        import json
        tier1_entries = json.loads((CORPUS_DIR / "lint_interfaces.json").read_text())
        return cls(tier1_store, tier2_store, tier1_entries)

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
        Return {"tier1": [...], "tier2": [...]} of retrieved API entries for instance.

        Tier 1: exact-match on scanner_interfaces, semantic fallback.
        Tier 2: dense search on nl_spec + scanner_interfaces query.
        """
        scanner_ifaces = instance.get("scanner_interfaces", [])
        nl_spec        = instance.get("nl_spec", "")
        query          = f"{nl_spec} [{' '.join(scanner_ifaces)}]"

        # Tier 1 — exact match first
        tier1_exact = [
            e for e in self._tier1_entries
            if e.get("interface") in scanner_ifaces
        ]

        # Tier 1 — semantic fallback if exact match is empty
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

        return {"tier1": tier1_exact, "tier2": tier2_docs}

    def format_context(
        self,
        instance: dict,
        k: int = DEFAULT_TOP_K,
    ) -> str:
        """
        Return a formatted string ready for prompt injection.

        Format:
          Relevant Lint API methods:
          ─────────────────────────
          [Tier 1: interface methods to override]

          [Tier 2: context/utility methods]

          Required imports:
          [deduplicated import list]
        """
        results = self.retrieve(instance, k=k)
        tier1   = results["tier1"]
        tier2   = results["tier2"]

        if not tier1 and not tier2:
            return ""

        lines: list[str] = ["Relevant Lint API methods:", "─" * 42]

        if tier1:
            lines.append("\n// Scanner interface methods to override:")
            for entry in tier1:
                lines.append(f"  {entry.get('signature', '')}")
                if entry.get("description"):
                    lines.append(f"  // {entry['description']}")
                if entry.get("example"):
                    lines.append(f"  // e.g. {entry['example']}")
                lines.append("")

        if tier2:
            lines.append("// Available context / utility methods:")
            for entry in tier2:
                lines.append(f"  {entry.get('signature', '')}")
                if entry.get("description"):
                    lines.append(f"  // {entry['description']}")
                lines.append("")

        # Deduplicated import list
        all_imports: list[str] = []
        seen: set[str] = set()
        for entry in tier1 + tier2:
            for imp in entry.get("imports", []):
                if imp not in seen:
                    seen.add(imp)
                    all_imports.append(imp)

        if all_imports:
            lines.append("─" * 42)
            lines.append("Required imports:")
            for imp in sorted(all_imports):
                lines.append(f"  import {imp}")

        return "\n".join(lines)


def get_knowledge_base(index_dir: Path = INDEX_DIR) -> LintAPIKnowledgeBase:
    """Singleton accessor — loads the index once per process."""
    global _KB_SINGLETON
    if _KB_SINGLETON is None:
        _KB_SINGLETON = LintAPIKnowledgeBase.load(index_dir)
    return _KB_SINGLETON
