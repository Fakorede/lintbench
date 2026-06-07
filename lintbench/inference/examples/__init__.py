"""
inference/examples — Few-shot detector examples for surface-matched prompting.

Each module exports a single SOURCE string (the full detector source with a
comment header). This package re-exports them under canonical names used by
prompts.py.

Scanner interface coverage:
  SourceCodeScanner (Kotlin)  : EXAMPLE_SOURCE_SCANNER_KT
  SourceCodeScanner (Java)    : EXAMPLE_SOURCE_SCANNER_JAVA
  SourceCodeScanner annotation: EXAMPLE_SOURCE_SCANNER_ANNOTATION_KT
  XmlScanner (Kotlin)         : EXAMPLE_XML_SCANNER_KT
  XmlScanner (Java)           : EXAMPLE_XML_SCANNER_JAVA
  XmlScanner Java attr        : EXAMPLE_XML_SCANNER_JAVA_ATTR
  GradleScanner               : EXAMPLE_GRADLE_SCANNER_KT
  GradleScanner method-call   : EXAMPLE_GRADLE_SCANNER_KT2
  OtherFileScanner (Java)     : EXAMPLE_OTHER_FILE_SCANNER_JAVA
  OtherFileScanner (Kotlin)   : EXAMPLE_OTHER_FILE_SCANNER_KT
  BinaryResourceScanner       : EXAMPLE_BINARY_RESOURCE_SCANNER_KT
  ResourceFolderScanner       : EXAMPLE_RESOURCE_FOLDER_SCANNER_KT
"""

from .tile_service_activity_detector       import SOURCE as EXAMPLE_SOURCE_SCANNER_KT
from .firebase_messaging_detector          import SOURCE as EXAMPLE_SOURCE_SCANNER_JAVA
from .kotlin_nullness_annotation_detector  import SOURCE as EXAMPLE_SOURCE_SCANNER_ANNOTATION_KT
from .c2dm_detector                        import SOURCE as EXAMPLE_XML_SCANNER_KT
from .manifest_permission_attribute_detector import SOURCE as EXAMPLE_XML_SCANNER_JAVA
from .hardcoded_debug_mode_detector        import SOURCE as EXAMPLE_XML_SCANNER_JAVA_ATTR
from .missing_resources_properties_detector import SOURCE as EXAMPLE_GRADLE_SCANNER_KT
from .proguard_android_txt_detector        import SOURCE as EXAMPLE_GRADLE_SCANNER_KT2
from .private_key_detector                 import SOURCE as EXAMPLE_OTHER_FILE_SCANNER_JAVA
from .property_file_detector               import SOURCE as EXAMPLE_OTHER_FILE_SCANNER_KT
from .tile_provider_detector               import SOURCE as EXAMPLE_BINARY_RESOURCE_SCANNER_KT
from .locale_config_detector               import SOURCE as EXAMPLE_RESOURCE_FOLDER_SCANNER_KT

__all__ = [
    "EXAMPLE_SOURCE_SCANNER_KT",
    "EXAMPLE_SOURCE_SCANNER_JAVA",
    "EXAMPLE_SOURCE_SCANNER_ANNOTATION_KT",
    "EXAMPLE_XML_SCANNER_KT",
    "EXAMPLE_XML_SCANNER_JAVA",
    "EXAMPLE_XML_SCANNER_JAVA_ATTR",
    "EXAMPLE_GRADLE_SCANNER_KT",
    "EXAMPLE_GRADLE_SCANNER_KT2",
    "EXAMPLE_OTHER_FILE_SCANNER_JAVA",
    "EXAMPLE_OTHER_FILE_SCANNER_KT",
    "EXAMPLE_BINARY_RESOURCE_SCANNER_KT",
    "EXAMPLE_RESOURCE_FOLDER_SCANNER_KT",
]
