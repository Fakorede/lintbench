"""
lintgen.rag — Lint API knowledge base and retrieval.

Public API:
  LintAPIKnowledgeBase   FAISS-backed retriever (Tier 1 + Tier 2)
  get_knowledge_base()   singleton accessor
"""

from .knowledge_base import LintAPIKnowledgeBase, get_knowledge_base

__all__ = ["LintAPIKnowledgeBase", "get_knowledge_base"]
