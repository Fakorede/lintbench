92 of 148 meet the criteria. Top suggestions — prioritised by fewest tests and shortest LOC for a clean smoke test, with a mix of scanner types:

**EASY, xml_only, single-issue:**

| Instance | Tests | LOC | Why |
|---|---|---|---|
| `DataBindingDetector:XmlEscapeNeeded` | 2 | 89 | Simplest XML; attribute scanner |
| `InvalidImeActionIdDetector:InvalidImeActionId` | 3 | 92 | Attribute scanner, clear spec |
| `WebViewDetector:WebViewLayout` | 3 | 93 | Element scanner, simple logic |
| `MonochromeLauncherIconDetector:MonochromeLauncherIcon` | 4 | 98 | Short, resource check |
| `Utf8Detector:EnforceUTF8` | 13 | 117 | Very short LOC despite many tests |

**EASY, source_only, single-issue:**

| Instance | Tests | LOC | Why |
|---|---|---|---|
| `WrongConstructorDetector:NotConstructor` | 3 | 88 | Simplest source scanner |
| `PublicKeyCredentialDetector:PublicKeyCredential` | 4 | 86 | Shortest LOC in source_only |
| `StorageDetector:UsableSpace` | 2 | 94 | Only 2 tests |
| `AllowAllHostnameVerifierDetector:AllowAllHostnameVerifier` | 2 | 115 | Security check, clear intent |
| `SecretDetector:SecretInSource` | 2 | 143 | Constructor-based detection |

For maximum coverage with minimal runs, I'd pick one from each scanner type:
- **`DataBindingDetector:XmlEscapeNeeded`** — xml_only representative
- **`WrongConstructorDetector:NotConstructor`** or **`PublicKeyCredentialDetector:PublicKeyCredential`** — source_only representative



