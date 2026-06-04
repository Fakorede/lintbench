"""
Tests for lintgen.rag — corpus loading and format_context output.
These tests run without a built FAISS index (stub-mode only).
"""

import json
from pathlib import Path

import pytest

CORPUS_DIR = Path(__file__).parent.parent / "src" / "lintgen" / "rag" / "corpus"


def test_lint_interfaces_corpus_loads():
    path = CORPUS_DIR / "lint_interfaces.json"
    assert path.exists(), "lint_interfaces.json missing"
    entries = json.loads(path.read_text())
    assert len(entries) > 0

    required_keys = {"interface", "method", "signature", "imports"}
    for e in entries:
        missing = required_keys - e.keys()
        assert not missing, f"Entry {e.get('method')} missing keys: {missing}"


def test_all_interfaces_covered():
    path = CORPUS_DIR / "lint_interfaces.json"
    entries = json.loads(path.read_text())
    # interface field is now a FQN e.g. "com.android.tools.lint.detector.api.XmlScanner"
    short_names = {e["interface"].split(".")[-1] for e in entries}
    expected = {"XmlScanner", "SourceCodeScanner", "GradleScanner"}
    assert expected <= short_names, f"Missing interfaces: {expected - short_names}"


def test_lint_fullapi_corpus_loads():
    path = CORPUS_DIR / "lint_fullapi.json"
    assert path.exists(), "lint_fullapi.json missing"
    entries = json.loads(path.read_text())
    assert len(entries) > 100, f"Expected >100 entries, got {len(entries)}"
    required_keys = {"class", "method", "signature", "imports"}
    for e in entries:
        missing = required_keys - e.keys()
        assert not missing, f"Entry {e.get('method')} missing keys: {missing}"


def test_lint_guides_corpus_loads():
    path = CORPUS_DIR / "lint_guides.json"
    assert path.exists(), "lint_guides.json missing"
    entries = json.loads(path.read_text())
    assert len(entries) > 100, f"Expected >100 chunked entries, got {len(entries)}"
    required_keys = {"source", "file", "title", "chunk_index", "content"}
    for e in entries:
        missing = required_keys - e.keys()
        assert not missing, f"Entry {e.get('title')} missing keys: {missing}"
    # All chunks within embedding model limit
    oversized = [e for e in entries if len(e["content"]) > 2048]
    assert not oversized, f"{len(oversized)} chunks exceed 2048 chars"
    # Both api-guide and usage sources present
    sources = {e["source"] for e in entries}
    assert "api-guide" in sources
    assert "usage" in sources


def test_knowledge_base_format_context_no_index(tmp_path):
    """format_context should raise FileNotFoundError if index not built."""
    from lintgen.rag.knowledge_base import LintAPIKnowledgeBase
    with pytest.raises(FileNotFoundError):
        LintAPIKnowledgeBase.load(index_dir=tmp_path / "nonexistent")
