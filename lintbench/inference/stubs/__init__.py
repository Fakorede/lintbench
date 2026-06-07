"""
inference/stubs — Method stub tables and smoke-test templates for LintBench.

Submodules:
  kotlin  — KT_STUBS, KT_HANDLER_STUBS, KT_TYPE_IMPORTS, BASE_KT_IMPORTS,
             UAST_HANDLER_METHODS
  java    — JAVA_STUBS, JAVA_HANDLER_STUBS, JAVA_TYPE_IMPORTS, BASE_JAVA_IMPORTS
  smoke   — STUB_KT, STUB_JAVA, APIDETECTOR_EXTRA_KT
"""

from .kotlin import (
    UAST_HANDLER_METHODS,
    KT_STUBS,
    KT_HANDLER_STUBS,
    KT_TYPE_IMPORTS,
    BASE_KT_IMPORTS,
)
from .java import (
    JAVA_STUBS,
    JAVA_HANDLER_STUBS,
    JAVA_TYPE_IMPORTS,
    BASE_JAVA_IMPORTS,
)
from .smoke import (
    STUB_KT,
    STUB_JAVA,
    APIDETECTOR_EXTRA_KT,
)
