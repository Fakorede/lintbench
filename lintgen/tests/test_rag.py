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
    interfaces = {e["interface"] for e in entries}
    expected = {"XmlScanner", "SourceCodeScanner", "GradleScanner"}
    assert expected <= interfaces, f"Missing interfaces: {expected - interfaces}"


def test_knowledge_base_format_context_no_index(tmp_path):
    """format_context should raise FileNotFoundError if index not built."""
    from lintgen.rag.knowledge_base import LintAPIKnowledgeBase
    with pytest.raises(FileNotFoundError):
        LintAPIKnowledgeBase.load(index_dir=tmp_path / "nonexistent")
