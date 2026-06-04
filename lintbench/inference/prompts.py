"""
inference/prompts.py
--------------------
Prompt templates and output extraction for LintBench inference.

Prompt variants
---------------
  zero_shot                  NL spec only
  structured_zero_shot       zero_shot + strict output contract
  api_hint                   zero_shot + scanner-interface-specific API guidance
  skeleton                   zero_shot + pre-filled class skeleton
  few_shot                   NL spec + one worked example (language-matched)
  few_shot_surface_matched   few_shot picking example by scanner interface type
  cot                        zero_shot + chain-of-thought instruction
  few_shot_surface_matched_cot  few_shot_surface_matched + CoT instruction
"""

import re
from textwrap import dedent

# ---------------------------------------------------------------------------
# Few-shot examples — excluded from the benchmark to avoid contamination
# ---------------------------------------------------------------------------

# ── SourceCodeScanner examples ───────────────────────────────────────────────
# Source: TileServiceActivityDetector (dataset.jsonl, EASY, SourceCodeScanner/kt)
#         FirebaseMessagingDetector   (dataset.jsonl, EASY, SourceCodeScanner/java)

EXAMPLE_SOURCE_SCANNER_KT = '''
// EXAMPLE: TileServiceActivityDetector (Kotlin, SourceCodeScanner — method-call matching)
// Issue: StartActivityAndCollapseDeprecated
// Explanation: TileService#startActivityAndCollapse(Intent) is deprecated and
// will throw UnsupportedOperationException on apps targeting Android UpsideDownCake+.

package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.targetSdkAtLeast
import com.android.tools.lint.detector.api.targetSdkLessThan
import com.intellij.psi.PsiMethod
import java.util.EnumSet
import org.jetbrains.uast.UCallExpression

class TileServiceActivityDetector : Detector(), SourceCodeScanner {

  companion object {
    private const val UPSIDE_DOWN_CAKE_API_VERSION: Int = 34

    @JvmField
    val START_ACTIVITY_AND_COLLAPSE_DEPRECATED =
      Issue.create(
        id = "StartActivityAndCollapseDeprecated",
        briefDescription = "TileService.startActivityAndCollapse(Intent) is deprecated",
        explanation =
          """
                `TileService#startActivityAndCollapse(Intent)` has been deprecated, and will throw \
                an `UnsupportedOperationException` if used in apps targeting Android versions \
                UpsideDownCake and higher. Convert the Intent to a PendingIntent.
            """,
        category = Category.CORRECTNESS,
        priority = 6,
        severity = Severity.ERROR,
        implementation = Implementation(TileServiceActivityDetector::class.java, Scope.JAVA_FILE_SCOPE),
        androidSpecific = true,
      )
  }

  override fun getApplicableMethodNames() = listOf("startActivityAndCollapse")

  override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
    if (!context.evaluator.isMemberInSubClassOf(method, "android.service.quicksettings.TileService")) {
      return
    }
    val argument = node.valueArguments.firstOrNull() ?: return
    if (argument.getExpressionType()?.canonicalText != "android.content.Intent") return

    val location = context.getLocation(argument)
    val message =
      "TileService#startActivityAndCollapse(Intent) is deprecated. Use TileService#startActivityAndCollapse(PendingIntent) instead."
    context.report(
      Incident(START_ACTIVITY_AND_COLLAPSE_DEPRECATED, node, location, message).overrideSeverity(Severity.WARNING),
      targetSdkLessThan(UPSIDE_DOWN_CAKE_API_VERSION),
    )
    context.report(
      Incident(START_ACTIVITY_AND_COLLAPSE_DEPRECATED, node, location, message),
      targetSdkAtLeast(UPSIDE_DOWN_CAKE_API_VERSION),
    )
  }
}
'''.strip()

# Source: FirebaseMessagingDetector (dataset.jsonl, EASY, SourceCodeScanner/java)
EXAMPLE_SOURCE_SCANNER_JAVA = '''
// EXAMPLE: FirebaseMessagingDetector (Java, SourceCodeScanner — class visitor)
// Issue: MissingFirebaseInstanceTokenRefresh
// Explanation: Apps using Firebase Cloud Messaging should implement
// FirebaseMessagingService#onNewToken() to observe token changes.

package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.intellij.psi.PsiMethod;
import java.util.Collections;
import java.util.List;
import org.jetbrains.uast.UClass;

public class FirebaseMessagingDetector extends Detector implements SourceCodeScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(FirebaseMessagingDetector.class, Scope.JAVA_FILE_SCOPE);

    private static final String FIREBASE_MESSAGING_SERVICE =
            "com.google.firebase.messaging.FirebaseMessagingService";

    public static final Issue MISSING_TOKEN_REFRESH =
            Issue.create(
                            "MissingFirebaseInstanceTokenRefresh",
                            "Missing Firebase Messaging Callback",
                            "Apps that use Firebase Cloud Messaging should implement the "
                                    + "`FirebaseMessagingService#onNewToken()` callback in order to "
                                    + "observe token changes.",
                            Category.CORRECTNESS,
                            6,
                            Severity.WARNING,
                            IMPLEMENTATION)
                    .setAndroidSpecific(true);

    public FirebaseMessagingDetector() {}

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        if (FIREBASE_MESSAGING_SERVICE.equals(declaration.getQualifiedName())) {
            return;
        }
        for (PsiMethod method : declaration.getMethods()) {
            if (method.getName().equals("onNewToken")) {
                return;
            }
        }
        context.report(
                MISSING_TOKEN_REFRESH,
                declaration,
                context.getNameLocation(declaration),
                "Apps that use Firebase Cloud Messaging should implement "
                        + "`onNewToken()` in order to observe token changes");
    }

    @Nullable
    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList(FIREBASE_MESSAGING_SERVICE);
    }
}
'''.strip()

# ── XmlScanner examples ──────────────────────────────────────────────────────
# Source: C2dmDetector                       (dataset.jsonl, EASY, XmlScanner/kt)
#         ManifestPermissionAttributeDetector (dataset.jsonl, EASY, XmlScanner/java)

EXAMPLE_XML_SCANNER_KT = '''
// EXAMPLE: C2dmDetector (Kotlin, XmlScanner — manifest element check)
// Issue: UsingC2DM
// Explanation: The C2DM library does not work on Android P or newer devices;
// migrate to Firebase Cloud Messaging.

package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.TAG_ACTION
import com.android.SdkConstants.TAG_INTENT_FILTER
import com.android.SdkConstants.TAG_RECEIVER
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import com.android.utils.XmlUtils.getFirstSubTagByName
import com.android.utils.XmlUtils.getNextTagByName
import org.w3c.dom.Element

class C2dmDetector : Detector(), XmlScanner {

  override fun getApplicableElements(): Collection<String> {
    return listOf(TAG_RECEIVER)
  }

  override fun visitElement(context: XmlContext, element: Element) {
    val attribute = element.getAttributeNodeNS(ANDROID_URI, ATTR_NAME) ?: return
    val receiverName = attribute.value
    if (receiverName != "com.google.android.c2dm.C2DMBroadcastReceiver" &&
        receiverName != "com.google.android.gcm.GCMBroadcastReceiver") {
      return
    }

    var haveReceive = false
    var haveRegistration = false
    var intentFilter = getFirstSubTagByName(element, TAG_INTENT_FILTER)
    while (intentFilter != null) {
      var action = getFirstSubTagByName(intentFilter, TAG_ACTION)
      while (action != null) {
        val actionName = action.getAttributeNS(ANDROID_URI, ATTR_NAME)
        if (actionName == "com.google.android.c2dm.intent.RECEIVE") haveReceive = true
        else if (actionName == "com.google.android.c2dm.intent.REGISTRATION") haveRegistration = true
        action = getNextTagByName(action, TAG_ACTION)
      }
      intentFilter = getNextTagByName(intentFilter, TAG_INTENT_FILTER)
    }

    if (haveReceive && haveRegistration) {
      context.report(ISSUE, attribute, context.getValueLocation(attribute),
        "The C2DM library does not work on Android P or newer devices; " +
        "migrate to Firebase Cloud Messaging")
    }
  }

  companion object {
    @JvmField
    val ISSUE = Issue.create(
      id = "UsingC2DM",
      briefDescription = "Using C2DM",
      explanation = "The C2DM library does not work on Android P or newer devices; " +
        "migrate to Firebase Cloud Messaging to ensure reliable message delivery.",
      category = Category.SECURITY,
      priority = 8,
      severity = Severity.ERROR,
      implementation = Implementation(C2dmDetector::class.java, Scope.MANIFEST_SCOPE),
    )
  }
}
'''.strip()

# Source: ManifestPermissionAttributeDetector (dataset.jsonl, EASY, XmlScanner/java)
EXAMPLE_XML_SCANNER_JAVA = '''
// EXAMPLE: ManifestPermissionAttributeDetector (Java, XmlScanner — manifest attribute check)
// Issue: InvalidPermission
// Explanation: Not all elements support the permission attribute. If set on an
// invalid element it is a no-op and ignored.

package com.android.tools.lint.checks;

import static com.android.SdkConstants.ATTR_PERMISSION;
import static com.android.xml.AndroidManifest.NODE_ACTIVITY;
import static com.android.xml.AndroidManifest.NODE_ACTIVITY_ALIAS;
import static com.android.xml.AndroidManifest.NODE_APPLICATION;
import static com.android.xml.AndroidManifest.NODE_PATH_PERMISSION;
import static com.android.xml.AndroidManifest.NODE_PROVIDER;
import static com.android.xml.AndroidManifest.NODE_RECEIVER;
import static com.android.xml.AndroidManifest.NODE_SERVICE;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import org.w3c.dom.Attr;
import java.util.Collection;
import java.util.Collections;

public class ManifestPermissionAttributeDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "InvalidPermission",
                    "Invalid Permission Attribute",
                    "Not all elements support the permission attribute. If a permission is set on"
                            + " an invalid element, it is a no-op and ignored. Ensure that this"
                            + " permission attribute was set on the correct element.",
                    Category.SECURITY,
                    5,
                    Severity.ERROR,
                    new Implementation(
                            ManifestPermissionAttributeDetector.class, Scope.MANIFEST_SCOPE));

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(ATTR_PERMISSION);
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String parent = attribute.getOwnerElement().getTagName();
        switch (parent) {
            case NODE_ACTIVITY:
            case NODE_APPLICATION:
            case NODE_PROVIDER:
            case NODE_SERVICE:
            case NODE_RECEIVER:
            case NODE_ACTIVITY_ALIAS:
            case NODE_PATH_PERMISSION:
                return;
        }
        context.report(ISSUE, attribute, context.getLocation(attribute),
                "Protecting an unsupported element with a permission is a no-op and "
                        + "potentially dangerous");
    }
}
'''.strip()

# ── GradleScanner example ────────────────────────────────────────────────────

EXAMPLE_GRADLE_SCANNER_KT = '''
// EXAMPLE: MissingResourcesPropertiesDetector (Kotlin, GradleScanner)
// Issue: MissingResourcesProperties
// Explanation: When generateLocaleConfig is turned on, the default locale must
// be specified in a resources.properties file.

package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.GradleContext
import com.android.tools.lint.detector.api.GradleScanner
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import java.io.File

class MissingResourcesPropertiesDetector : Detector(), GradleScanner {

    override fun checkDslPropertyAssignment(
        context: GradleContext,
        property: String,
        value: String,
        parent: String,
        parentParent: String?,
        propertyCookie: Any,
        valueCookie: Any,
        statementCookie: Any,
    ) {
        if (context.project.isLibrary) return
        if (property == "generateLocaleConfig" && value == "true") {
            if (context.project.resourceFolders.none { File(it, "resources.properties").exists() }) {
                val incident = Incident(
                    ISSUE, propertyCookie,
                    context.getLocation(propertyCookie),
                    "Missing resources.properties file"
                )
                context.client.report(context, incident)
            }
        }
    }

    companion object {
        val ISSUE = Issue.create(
            id = "MissingResourcesProperties",
            briefDescription = "Missing resources.properties file",
            explanation = "When `generateLocaleConfig` is turned on, the default locale must be " +
                "specified in a resources.properties file.",
            category = Category.CORRECTNESS,
            priority = 2,
            severity = Severity.WARNING,
            implementation = Implementation(
                MissingResourcesPropertiesDetector::class.java, Scope.GRADLE_SCOPE
            ),
            androidSpecific = true,
        )
    }
}
'''.strip()

# ── OtherFileScanner example ─────────────────────────────────────────────────

EXAMPLE_OTHER_FILE_SCANNER_JAVA = '''
// EXAMPLE: PrivateKeyDetector (Java, OtherFileScanner)
// Issue: PackagedPrivateKey
// Explanation: In general, you should not package private key files inside your app.

package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Lint;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.OtherFileScanner;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import java.util.EnumSet;
import kotlin.text.Charsets;

public class PrivateKeyDetector extends Detector implements OtherFileScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "PackagedPrivateKey",
                    "Packaged private key",
                    "In general, you should not package private key files inside your app.",
                    Category.SECURITY,
                    8,
                    Severity.FATAL,
                    new Implementation(PrivateKeyDetector.class, Scope.OTHER_SCOPE))
                    .setAndroidSpecific(true);

    @Override
    public EnumSet<Scope> getApplicableFiles() {
        return Scope.OTHER_SCOPE;
    }

    @Override
    public void run(Context context) {
        if (!context.getProject().getReportIssues()) {
            return;
        }
        File file = context.file;
        if (isPrivateKeyFile(file)) {
            String fileName = Lint.getFileNameWithParent(context.getClient(), file);
            context.report(ISSUE, Location.create(file),
                    String.format("The `%1$s` file seems to be a private key file. " +
                            "Please make sure not to embed this in your APK file.", fileName));
        }
    }

    private static boolean isPrivateKeyFile(File file) {
        if (!file.isFile()
                || (!Lint.endsWith(file.getPath(), "pem")
                        && !Lint.endsWith(file.getPath(), "key"))) {
            return false;
        }
        try {
            String firstLine = Files.asCharSource(file, Charsets.US_ASCII).readFirstLine();
            return firstLine != null
                    && firstLine.startsWith("---")
                    && firstLine.contains("PRIVATE KEY");
        } catch (IOException ex) {
            return false;
        }
    }
}
'''.strip()

# ---------------------------------------------------------------------------
# Scanner-interface → example mapping (surface matching)
# ---------------------------------------------------------------------------

# Each entry: (signal_set, kotlin_example, java_example)
# All 7 scanner interfaces are covered. Interfaces without a concise standalone
# example fall back to the closest conceptual match:
#   ClassScanner        → SourceCodeScanner  (same UAST analysis domain)
#   BinaryResourceScanner → XmlScanner       (same resource scanning domain)
#   ResourceFolderScanner → XmlScanner       (same resource scanning domain)
# _pick_examples deduplicates, so a mixed instance gets one example per
# *distinct* example string, not one per interface.
_SURFACE_EXAMPLES: list[tuple[set, str, str]] = [
    (
        {"GradleScanner"},
        EXAMPLE_GRADLE_SCANNER_KT,
        EXAMPLE_GRADLE_SCANNER_KT,       # no Java-only example; Kotlin example is instructive
    ),
    (
        {"OtherFileScanner"},
        EXAMPLE_OTHER_FILE_SCANNER_JAVA, # no Kotlin-only example; Java example is instructive
        EXAMPLE_OTHER_FILE_SCANNER_JAVA,
    ),
    (
        {"XmlScanner", "BinaryResourceScanner", "ResourceFolderScanner"},
        EXAMPLE_XML_SCANNER_KT,
        EXAMPLE_XML_SCANNER_JAVA,
    ),
    (
        {"SourceCodeScanner", "ClassScanner"},
        EXAMPLE_SOURCE_SCANNER_KT,
        EXAMPLE_SOURCE_SCANNER_JAVA,
    ),
]

_DEFAULT_EXAMPLES = (EXAMPLE_SOURCE_SCANNER_KT, EXAMPLE_SOURCE_SCANNER_JAVA)


def _pick_examples(instance: dict, lang_ext: str) -> str:
    """
    Return one few-shot example per matched scanner interface, separated by
    a divider. For multi-interface detectors (e.g. SourceCodeScanner +
    XmlScanner) this provides one concrete example per interface pattern so
    the model sees how each scanner callback is implemented.

    Matches on scanner_interfaces only (not api_surfaces) to avoid false
    positives.
    """
    signals: set[str] = set(instance.get("scanner_interfaces", []))
    seen_examples: list[str] = []
    seen_texts: set[str] = set()

    for marker_set, kt_ex, java_ex in _SURFACE_EXAMPLES:
        if signals & marker_set:
            ex = kt_ex if lang_ext == "kt" else java_ex
            if ex not in seen_texts:
                seen_examples.append(ex)
                seen_texts.add(ex)

    if not seen_examples:
        kt_ex, java_ex = _DEFAULT_EXAMPLES
        seen_examples.append(kt_ex if lang_ext == "kt" else java_ex)

    return "\n\n---\n\n".join(seen_examples)


# ---------------------------------------------------------------------------
# API-surface hints
# ---------------------------------------------------------------------------

_API_HINTS: dict[str, str] = {
    "SourceCodeScanner": dedent("""\
        SourceCodeScanner API notes:
        - Visitor methods (visitMethodCall, visitMethod, visitCallExpression, visitClass, etc.)
          receive a JavaContext and the relevant UAST node.
        - If using createUastHandler, return a UElementHandler subclass and override only the
          visitor methods that match the UElement types returned by getApplicableUastTypes().
        - Type checks: context.evaluator.extendsClass(cls, "com.example.Foo", false)
          or context.evaluator.implementsInterface(cls, "com.example.Bar", false)
        - Report: context.report(ISSUE, node, context.getLocation(node), "message")"""),

    "XmlScanner": dedent("""\
        XmlScanner API notes:
        - visitElement(context, element): element is an org.w3c.dom.Element;
          use element.getAttribute("name") and element.getTagName().
        - visitAttribute(context, attribute): attribute is an org.w3c.dom.Attr;
          use attribute.getValue() and attribute.getName().
        - appliesTo(folderType) restricts scanning to specific ResourceFolderType values
          (e.g. ResourceFolderType.MANIFEST, ResourceFolderType.LAYOUT).
        - Report: context.report(ISSUE, element, context.getLocation(element), "message")"""),

    "ClassScanner": dedent("""\
        ClassScanner API notes:
        - checkClass(context, declaration): declaration is a UClass (UAST).
        - Use context.evaluator to inspect superclass, interfaces, and annotations.
        - Report: context.report(ISSUE, declaration, context.getNameLocation(declaration), "message")"""),

    "BinaryResourceScanner": dedent("""\
        BinaryResourceScanner API notes:
        - checkBinaryResource(context): context is a ResourceContext.
        - Use context.getResourceFolderType() to filter by folder (e.g. ResourceFolderType.DRAWABLE).
        - Use context.file for the raw File reference.
        - Report: context.report(ISSUE, Location.create(context.file), "message")"""),

    "ResourceFolderScanner": dedent("""\
        ResourceFolderScanner API notes:
        - checkResourceFolder(context, folder): folder is a ResourceFolder.
        - Use context.getResourceFolderType() to filter by ResourceFolderType.
        - Report: context.report(ISSUE, Location.create(folder.getFolderFile()), "message")"""),

    "GradleScanner": dedent("""\
        GradleScanner API notes:
        - checkDslPropertyAssignment(context, property, value, parent): called for DSL assignments.
        - checkMethodCall(context, statement, parent, propertiesMap): called for method calls.
        - Values are GradleCoordinate strings or raw string literals.
        - Report: context.report(ISSUE, context.getLocation(value), "message")"""),

    "OtherFileScanner": dedent("""\
        OtherFileScanner API notes:
        - run(context): context is a Context with context.file for the current file.
        - Use getApplicableFiles() to restrict to specific file names or extensions.
        - Report: context.report(ISSUE, Location.create(context.file), "message")"""),
}


def _build_api_hint(instance: dict) -> str:
    """Return a scanner API guidance block based on scanner_interfaces."""
    interfaces = instance.get("scanner_interfaces", [])
    lines = [_API_HINTS[iface] for iface in interfaces if iface in _API_HINTS]
    if not lines:
        return ""
    return "\nScanner API guidance:\n" + "\n\n".join(lines) + "\n"


# ---------------------------------------------------------------------------
# Skeleton generator — method-stub lookup tables
# ---------------------------------------------------------------------------

# Methods that live INSIDE the UElementHandler returned by createUastHandler(),
# not directly on the Detector class.
_UAST_HANDLER_METHODS: frozenset = frozenset({
    "visitMethod", "visitCallExpression", "visitSimpleNameReferenceExpression",
    "visitBinaryExpression", "visitReturnExpression", "visitIfExpression",
    "visitAnnotation", "visitThrowExpression", "visitDeclarationsExpression",
    "visitClassLiteralExpression", "visitCallableReferenceExpression",
    "visitParameter", "visitTypeReferenceExpression",
    "visitQualifiedReferenceExpression", "visitImportStatement",
    "visitVariable", "visitBlockExpression",
})

# Kotlin stubs for direct Detector overrides (no leading indent)
_KT_STUBS: dict[str, str] = {
    # ── SourceCodeScanner ─────────────────────────────────────────────────
    "getApplicableMethodNames":
        "override fun getApplicableMethodNames(): List<String>? = TODO()",
    "visitMethodCall": dedent("""\
        override fun visitMethodCall(
            context: JavaContext, node: UCallExpression, method: PsiMethod,
        ) {
            TODO()
        }"""),
    "getApplicableUastTypes":
        "override fun getApplicableUastTypes(): List<Class<out UElement>>? = TODO()",
    "applicableSuperClasses":
        "override fun applicableSuperClasses(): List<String>? = TODO()",
    "visitClass": dedent("""\
        override fun visitClass(context: JavaContext, declaration: UClass) {
            TODO()
        }"""),
    "getApplicableReferenceNames":
        "override fun getApplicableReferenceNames(): List<String>? = TODO()",
    "visitReference": dedent("""\
        override fun visitReference(
            context: JavaContext, reference: UReferenceExpression, referenced: PsiElement,
        ) {
            TODO()
        }"""),
    "getApplicableConstructorTypes":
        "override fun getApplicableConstructorTypes(): List<String>? = TODO()",
    "visitConstructor": dedent("""\
        override fun visitConstructor(
            context: JavaContext, node: UCallExpression, constructor: PsiMethod,
        ) {
            TODO()
        }"""),
    "applicableAnnotations":
        "override fun applicableAnnotations(): List<String>? = TODO()",
    "isApplicableAnnotationUsage":
        "override fun isApplicableAnnotationUsage(type: AnnotationUsageType): Boolean = TODO()",
    "visitAnnotationUsage": dedent("""\
        override fun visitAnnotationUsage(
            context: JavaContext, element: UElement, annotation: UAnnotation,
            qualifiedName: String,
        ) {
            TODO()
        }"""),
    "appliesToResourceRefs":
        "override fun appliesToResourceRefs(): Boolean = TODO()",
    # ── XmlScanner ────────────────────────────────────────────────────────
    "getApplicableElements":
        "override fun getApplicableElements(): Collection<String>? = TODO()",
    "visitElement": dedent("""\
        override fun visitElement(context: XmlContext, element: Element) {
            TODO()
        }"""),
    "getApplicableAttributes":
        "override fun getApplicableAttributes(): Collection<String>? = TODO()",
    "visitAttribute": dedent("""\
        override fun visitAttribute(context: XmlContext, attribute: Attr) {
            TODO()
        }"""),
    "appliesTo":
        "override fun appliesTo(folderType: ResourceFolderType): Boolean = TODO()",
    "visitDocument": dedent("""\
        override fun visitDocument(context: XmlContext, document: Document) {
            TODO()
        }"""),
    "visitElementAfter": dedent("""\
        override fun visitElementAfter(context: XmlContext, element: Element) {
            TODO()
        }"""),
    # ── Detector lifecycle ─────────────────────────────────────────────────
    "beforeCheckRootProject": dedent("""\
        override fun beforeCheckRootProject(context: Context) {
            TODO()
        }"""),
    "afterCheckRootProject": dedent("""\
        override fun afterCheckRootProject(context: Context) {
            TODO()
        }"""),
    "beforeCheckFile": dedent("""\
        override fun beforeCheckFile(context: Context) {
            TODO()
        }"""),
    "afterCheckFile": dedent("""\
        override fun afterCheckFile(context: Context) {
            TODO()
        }"""),
    "beforeCheckEachProject": dedent("""\
        override fun beforeCheckEachProject(context: Context) {
            TODO()
        }"""),
    "afterCheckEachProject": dedent("""\
        override fun afterCheckEachProject(context: Context) {
            TODO()
        }"""),
    "filterIncident": dedent("""\
        override fun filterIncident(context: Context, incident: Incident, map: LintMap): Boolean {
            TODO()
        }"""),
    "checkPartialResults": dedent("""\
        override fun checkPartialResults(context: Context, partialResults: PartialResult) {
            TODO()
        }"""),
    "checkMergedProject": dedent("""\
        override fun checkMergedProject(context: Context) {
            TODO()
        }"""),
    # ── GradleScanner ─────────────────────────────────────────────────────
    "checkDslPropertyAssignment": dedent("""\
        override fun checkDslPropertyAssignment(
            context: GradleContext, property: String, value: String,
            parent: String, parentParent: String?,
            valueCookie: Any, statementCookie: Any,
        ) {
            TODO()
        }"""),
    # ── OtherFileScanner ──────────────────────────────────────────────────
    "run": dedent("""\
        override fun run(context: Context) {
            TODO()
        }"""),
    # ── BinaryResourceScanner ─────────────────────────────────────────────
    "checkBinaryResource": dedent("""\
        override fun checkBinaryResource(context: ResourceContext) {
            TODO()
        }"""),
    # ── ResourceFolderScanner ─────────────────────────────────────────────
    "checkFolder": dedent("""\
        override fun checkFolder(context: ResourceContext, folderName: String) {
            TODO()
        }"""),
}

# Kotlin stubs for UElementHandler methods (inside createUastHandler body)
_KT_HANDLER_STUBS: dict[str, str] = {
    "visitMethod": dedent("""\
        override fun visitMethod(node: UMethod) {
            TODO()
        }"""),
    "visitCallExpression": dedent("""\
        override fun visitCallExpression(node: UCallExpression) {
            TODO()
        }"""),
    "visitSimpleNameReferenceExpression": dedent("""\
        override fun visitSimpleNameReferenceExpression(node: USimpleNameReferenceExpression) {
            TODO()
        }"""),
    "visitBinaryExpression": dedent("""\
        override fun visitBinaryExpression(node: UBinaryExpression) {
            TODO()
        }"""),
    "visitReturnExpression": dedent("""\
        override fun visitReturnExpression(node: UReturnExpression) {
            TODO()
        }"""),
    "visitClass": dedent("""\
        override fun visitClass(node: UClass) {
            TODO()
        }"""),
    "visitAnnotation": dedent("""\
        override fun visitAnnotation(node: UAnnotation) {
            TODO()
        }"""),
}

# Java stubs for direct Detector overrides
_JAVA_STUBS: dict[str, str] = {
    # ── SourceCodeScanner ─────────────────────────────────────────────────
    "getApplicableMethodNames":
        "@Override\npublic List<String> getApplicableMethodNames() { return TODO(); }",
    "visitMethodCall": dedent("""\
        @Override
        public void visitMethodCall(
                @NonNull JavaContext context,
                @NonNull UCallExpression node,
                @NonNull PsiMethod method) {
            // TODO
        }"""),
    "getApplicableUastTypes":
        "@Override\npublic List<Class<? extends UElement>> getApplicableUastTypes() { return TODO(); }",
    "applicableSuperClasses":
        "@Override\npublic List<String> applicableSuperClasses() { return TODO(); }",
    "visitClass": dedent("""\
        @Override
        public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
            // TODO
        }"""),
    "getApplicableReferenceNames":
        "@Override\npublic List<String> getApplicableReferenceNames() { return TODO(); }",
    "visitReference": dedent("""\
        @Override
        public void visitReference(
                @NonNull JavaContext context,
                @NonNull UReferenceExpression reference,
                @NonNull PsiElement referenced) {
            // TODO
        }"""),
    "getApplicableConstructorTypes":
        "@Override\npublic List<String> getApplicableConstructorTypes() { return TODO(); }",
    "visitConstructor": dedent("""\
        @Override
        public void visitConstructor(
                @NonNull JavaContext context,
                @NonNull UCallExpression node,
                @NonNull PsiMethod constructor) {
            // TODO
        }"""),
    "applicableAnnotations":
        "@Override\npublic List<String> applicableAnnotations() { return TODO(); }",
    "isApplicableAnnotationUsage":
        "@Override\npublic boolean isApplicableAnnotationUsage(@NonNull AnnotationUsageType type) { return TODO(); }",
    "visitAnnotationUsage": dedent("""\
        @Override
        public void visitAnnotationUsage(
                @NonNull JavaContext context,
                @NonNull UElement element,
                @NonNull UAnnotation annotation,
                @NonNull String qualifiedName) {
            // TODO
        }"""),
    "appliesToResourceRefs":
        "@Override\npublic boolean appliesToResourceRefs() { return TODO(); }",
    # ── XmlScanner ────────────────────────────────────────────────────────
    "getApplicableElements":
        "@Override\npublic Collection<String> getApplicableElements() { return TODO(); }",
    "visitElement": dedent("""\
        @Override
        public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
            // TODO
        }"""),
    "getApplicableAttributes":
        "@Override\npublic Collection<String> getApplicableAttributes() { return TODO(); }",
    "visitAttribute": dedent("""\
        @Override
        public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
            // TODO
        }"""),
    "appliesTo":
        "@Override\npublic boolean appliesTo(@NonNull ResourceFolderType folderType) { return TODO(); }",
    "visitDocument": dedent("""\
        @Override
        public void visitDocument(@NonNull XmlContext context, @NonNull Document document) {
            // TODO
        }"""),
    "visitElementAfter": dedent("""\
        @Override
        public void visitElementAfter(@NonNull XmlContext context, @NonNull Element element) {
            // TODO
        }"""),
    # ── Detector lifecycle ─────────────────────────────────────────────────
    "beforeCheckRootProject": dedent("""\
        @Override
        public void beforeCheckRootProject(@NonNull Context context) {
            // TODO
        }"""),
    "afterCheckRootProject": dedent("""\
        @Override
        public void afterCheckRootProject(@NonNull Context context) {
            // TODO
        }"""),
    "beforeCheckFile": dedent("""\
        @Override
        public void beforeCheckFile(@NonNull Context context) {
            // TODO
        }"""),
    "afterCheckFile": dedent("""\
        @Override
        public void afterCheckFile(@NonNull Context context) {
            // TODO
        }"""),
    "beforeCheckEachProject": dedent("""\
        @Override
        public void beforeCheckEachProject(@NonNull Context context) {
            // TODO
        }"""),
    "afterCheckEachProject": dedent("""\
        @Override
        public void afterCheckEachProject(@NonNull Context context) {
            // TODO
        }"""),
    "filterIncident": dedent("""\
        @Override
        public boolean filterIncident(
                @NonNull Context context, @NonNull Incident incident, @NonNull LintMap map) {
            return TODO();
        }"""),
    "checkPartialResults": dedent("""\
        @Override
        public void checkPartialResults(
                @NonNull Context context, @NonNull PartialResult partialResults) {
            // TODO
        }"""),
    "checkMergedProject": dedent("""\
        @Override
        public void checkMergedProject(@NonNull Context context) {
            // TODO
        }"""),
    # ── GradleScanner ─────────────────────────────────────────────────────
    "checkDslPropertyAssignment": dedent("""\
        @Override
        public void checkDslPropertyAssignment(
                @NonNull GradleContext context,
                @NonNull String property, @NonNull String value,
                @NonNull String parent, @Nullable String parentParent,
                @NonNull Object valueCookie, @NonNull Object statementCookie) {
            // TODO
        }"""),
    # ── OtherFileScanner ──────────────────────────────────────────────────
    "run": dedent("""\
        @Override
        public void run(@NonNull Context context) {
            // TODO
        }"""),
    # ── BinaryResourceScanner ─────────────────────────────────────────────
    "checkBinaryResource": dedent("""\
        @Override
        public void checkBinaryResource(@NonNull ResourceContext context) {
            // TODO
        }"""),
    # ── ResourceFolderScanner ─────────────────────────────────────────────
    "checkFolder": dedent("""\
        @Override
        public void checkFolder(@NonNull ResourceContext context, @NonNull String folderName) {
            // TODO
        }"""),
}

# Java stubs for UElementHandler methods (inside createUastHandler body)
_JAVA_HANDLER_STUBS: dict[str, str] = {
    "visitMethod": dedent("""\
        @Override
        public void visitMethod(@NonNull UMethod node) {
            // TODO
        }"""),
    "visitCallExpression": dedent("""\
        @Override
        public void visitCallExpression(@NonNull UCallExpression node) {
            // TODO
        }"""),
    "visitSimpleNameReferenceExpression": dedent("""\
        @Override
        public void visitSimpleNameReferenceExpression(@NonNull USimpleNameReferenceExpression node) {
            // TODO
        }"""),
    "visitBinaryExpression": dedent("""\
        @Override
        public void visitBinaryExpression(@NonNull UBinaryExpression node) {
            // TODO
        }"""),
    "visitReturnExpression": dedent("""\
        @Override
        public void visitReturnExpression(@NonNull UReturnExpression node) {
            // TODO
        }"""),
    "visitClass": dedent("""\
        @Override
        public void visitClass(@NonNull UClass node) {
            // TODO
        }"""),
    "visitAnnotation": dedent("""\
        @Override
        public void visitAnnotation(@NonNull UAnnotation node) {
            // TODO
        }"""),
}

# Type name → Kotlin import line
_KT_TYPE_IMPORTS: dict[str, str] = {
    "JavaContext":          "import com.android.tools.lint.detector.api.JavaContext",
    "XmlContext":           "import com.android.tools.lint.detector.api.XmlContext",
    "Context":              "import com.android.tools.lint.detector.api.Context",
    "GradleContext":        "import com.android.tools.lint.detector.api.GradleContext",
    "ResourceContext":      "import com.android.tools.lint.detector.api.ResourceContext",
    "Incident":             "import com.android.tools.lint.detector.api.Incident",
    "LintMap":              "import com.android.tools.lint.detector.api.LintMap",
    "PartialResult":        "import com.android.tools.lint.detector.api.PartialResult",
    "AnnotationUsageType":  "import com.android.tools.lint.detector.api.AnnotationUsageType",
    "ResourceFolderType":   "import com.android.resources.ResourceFolderType",
    "UElementHandler":      "import com.android.tools.lint.client.api.UElementHandler",
    "UCallExpression":      "import org.jetbrains.uast.UCallExpression",
    "UClass":               "import org.jetbrains.uast.UClass",
    "UElement":             "import org.jetbrains.uast.UElement",
    "UMethod":              "import org.jetbrains.uast.UMethod",
    "UBinaryExpression":    "import org.jetbrains.uast.UBinaryExpression",
    "UReturnExpression":    "import org.jetbrains.uast.UReturnExpression",
    "USimpleNameReferenceExpression": "import org.jetbrains.uast.USimpleNameReferenceExpression",
    "UReferenceExpression": "import org.jetbrains.uast.UReferenceExpression",
    "UAnnotation":          "import org.jetbrains.uast.UAnnotation",
    "PsiMethod":            "import com.intellij.psi.PsiMethod",
    "PsiElement":           "import com.intellij.psi.PsiElement",
    "Element":              "import org.w3c.dom.Element",
    "Attr":                 "import org.w3c.dom.Attr",
    "Document":             "import org.w3c.dom.Document",
    "EnumSet":              "import java.util.EnumSet",
}

# Type name → Java import line
_JAVA_TYPE_IMPORTS: dict[str, str] = {
    "JavaContext":          "import com.android.tools.lint.detector.api.JavaContext;",
    "XmlContext":           "import com.android.tools.lint.detector.api.XmlContext;",
    "Context":              "import com.android.tools.lint.detector.api.Context;",
    "GradleContext":        "import com.android.tools.lint.detector.api.GradleContext;",
    "ResourceContext":      "import com.android.tools.lint.detector.api.ResourceContext;",
    "Incident":             "import com.android.tools.lint.detector.api.Incident;",
    "LintMap":              "import com.android.tools.lint.detector.api.LintMap;",
    "PartialResult":        "import com.android.tools.lint.detector.api.PartialResult;",
    "AnnotationUsageType":  "import com.android.tools.lint.detector.api.AnnotationUsageType;",
    "ResourceFolderType":   "import com.android.resources.ResourceFolderType;",
    "UElementHandler":      "import com.android.tools.lint.client.api.UElementHandler;",
    "UCallExpression":      "import org.jetbrains.uast.UCallExpression;",
    "UClass":               "import org.jetbrains.uast.UClass;",
    "UElement":             "import org.jetbrains.uast.UElement;",
    "UMethod":              "import org.jetbrains.uast.UMethod;",
    "UBinaryExpression":    "import org.jetbrains.uast.UBinaryExpression;",
    "UReturnExpression":    "import org.jetbrains.uast.UReturnExpression;",
    "USimpleNameReferenceExpression": "import org.jetbrains.uast.USimpleNameReferenceExpression;",
    "UReferenceExpression": "import org.jetbrains.uast.UReferenceExpression;",
    "UAnnotation":          "import org.jetbrains.uast.UAnnotation;",
    "PsiMethod":            "import com.intellij.psi.PsiMethod;",
    "PsiElement":           "import com.intellij.psi.PsiElement;",
    "Element":              "import org.w3c.dom.Element;",
    "Attr":                 "import org.w3c.dom.Attr;",
    "Document":             "import org.w3c.dom.Document;",
    "Collection":           "import java.util.Collection;",
    "List":                 "import java.util.List;",
    "Nullable":             "import com.android.annotations.Nullable;",
    "EnumSet":              "import java.util.EnumSet;",
}

_BASE_KT_IMPORTS: list[str] = [
    "import com.android.tools.lint.detector.api.Category",
    "import com.android.tools.lint.detector.api.Detector",
    "import com.android.tools.lint.detector.api.Implementation",
    "import com.android.tools.lint.detector.api.Issue",
    "import com.android.tools.lint.detector.api.Scope",
    "import com.android.tools.lint.detector.api.Severity",
]

_BASE_JAVA_IMPORTS: list[str] = [
    "import com.android.annotations.NonNull;",
    "import com.android.tools.lint.detector.api.Category;",
    "import com.android.tools.lint.detector.api.Detector;",
    "import com.android.tools.lint.detector.api.Implementation;",
    "import com.android.tools.lint.detector.api.Issue;",
    "import com.android.tools.lint.detector.api.Scope;",
    "import com.android.tools.lint.detector.api.Severity;",
]


def _infer_scope(interfaces: list[str]) -> str:
    """Return a Scope expression for the Implementation constructor."""
    _SINGLE: dict[str, str] = {
        "SourceCodeScanner":     "Scope.JAVA_FILE_SCOPE",
        "GradleScanner":         "Scope.GRADLE_SCOPE",
        "XmlScanner":            "Scope.RESOURCE_FILE_SCOPE",
        "OtherFileScanner":      "Scope.OTHER_SCOPE",
        "BinaryResourceScanner": "Scope.BINARY_RESOURCE_FILE_SCOPE",
        "ResourceFolderScanner": "Scope.RESOURCE_FOLDER_SCOPE",
    }
    _ENUM_ITEM: dict[str, str] = {
        "SourceCodeScanner":     "Scope.JAVA_FILE",
        "GradleScanner":         "Scope.GRADLE_FILE",
        "XmlScanner":            "Scope.RESOURCE_FILE",
        "BinaryResourceScanner": "Scope.BINARY_RESOURCE_FILE",
        "ResourceFolderScanner": "Scope.RESOURCE_FOLDER",
    }
    if len(interfaces) == 1:
        return _SINGLE.get(interfaces[0], "Scope.JAVA_FILE_SCOPE")
    items = [_ENUM_ITEM[i] for i in interfaces if i in _ENUM_ITEM]
    return f"EnumSet.of({', '.join(items)})" if items else "Scope.JAVA_FILE_SCOPE"


def _collect_imports(stub_text: str, type_map: dict[str, str]) -> list[str]:
    """Return import lines for type names that appear as whole words in stub_text."""
    return [
        imp for name, imp in type_map.items()
        if re.search(r'\b' + re.escape(name) + r'\b', stub_text)
    ]


def _build_skeleton(instance: dict) -> str:
    """
    Return a detector skeleton populated from the instance's methods_to_generate.
    Stubs are generated for every method in the list using exact Lint API signatures.
    UElementHandler methods are embedded inside createUastHandler() rather than
    placed directly on the Detector class.
    """
    lang_ext   = instance["check_lang"]
    detector   = instance["detector"]
    issue_id   = instance["issue_id"]
    brief      = instance.get("brief_description", "").replace('"', '\\"')
    category   = instance.get("category", "CORRECTNESS")
    severity   = instance.get("severity", "WARNING")
    priority   = instance.get("priority") or 5
    interfaces = instance.get("scanner_interfaces", ["SourceCodeScanner"])
    methods    = instance.get("methods_to_generate", [])
    base_class = instance.get("base_class", "Detector")

    scope      = _infer_scope(interfaces)
    iface_str  = ", ".join(interfaces)

    use_handler    = "createUastHandler" in methods
    handler_meths  = [m for m in methods if m in _UAST_HANDLER_METHODS] if use_handler else []
    detector_meths = [
        m for m in methods
        if m != "createUastHandler" and m not in handler_meths
    ]

    if lang_ext == "kt":
        # Direct detector-level stubs
        method_blocks: list[str] = []
        for m in detector_meths:
            method_blocks.append(
                _KT_STUBS.get(m, f"override fun {m}(/*TODO*/) {{ TODO() }}")
            )

        # createUastHandler with embedded handler stubs
        if use_handler:
            inner_lines: list[str] = []
            for m in handler_meths:
                stub = _KT_HANDLER_STUBS.get(m, f"override fun {m}(node: UElement) {{ TODO() }}")
                inner_lines.append(
                    "\n".join("        " + line for line in stub.splitlines())
                )
            inner = (
                "\n\n".join(inner_lines)
                if inner_lines
                else "        // TODO: override visitor methods matching getApplicableUastTypes()"
            )
            method_blocks.append(
                f"override fun createUastHandler(context: JavaContext): UElementHandler =\n"
                f"    object : UElementHandler() {{\n"
                f"{inner}\n"
                f"    }}"
            )

        body = "\n\n".join(
            "\n".join("    " + line for line in block.splitlines())
            for block in method_blocks
        )

        all_stubs = "\n".join(method_blocks) + f" {iface_str} {scope}"
        extra = _collect_imports(all_stubs, _KT_TYPE_IMPORTS)
        imports = "\n".join(sorted(set(_BASE_KT_IMPORTS + extra)))

        companion = "\n".join([
            "    companion object {",
            "        private val IMPLEMENTATION = Implementation(",
            f"            {detector}::class.java,",
            f"            {scope},",
            "        )",
            "",
            "        @JvmField",
            "        val ISSUE = Issue.create(",
            f'            id = "{issue_id}",',
            f'            briefDescription = "{brief}",',
            '            explanation = "TODO",',
            f"            category = Category.{category},",
            f"            priority = {priority},",
            f"            severity = Severity.{severity},",
            "            implementation = IMPLEMENTATION,",
            "        )",
            "    }",
        ])

        # If the base class already provides the scanner interface (e.g. LayoutDetector
        # implements XmlScanner), omit the redundant interface list.
        _BASE_PROVIDES_IFACE = {"LayoutDetector", "ResourceXmlDetector"}
        if base_class in _BASE_PROVIDES_IFACE:
            class_header = f"class {detector} : {base_class}()"
        else:
            class_header = f"class {detector} : {base_class}(), {iface_str}"

        sections = [
            f"package com.android.tools.lint.checks",
            "",
            imports,
            "",
            f"{class_header} {{",
            "",
            companion,
        ]
        if body:
            sections += ["", body]
        sections.append("}")
        return "\n".join(sections)

    else:  # java
        # Direct detector-level stubs
        method_blocks = []
        for m in detector_meths:
            method_blocks.append(
                _JAVA_STUBS.get(m, f"public void {m}(/*TODO*/) {{ // TODO }}")
            )

        # createUastHandler with embedded handler stubs
        if use_handler:
            inner_lines = []
            for m in handler_meths:
                stub = _JAVA_HANDLER_STUBS.get(m, f"public void {m}(UElement node) {{ // TODO }}")
                inner_lines.append(
                    "\n".join("            " + line for line in stub.splitlines())
                )
            inner = (
                "\n\n".join(inner_lines)
                if inner_lines
                else "            // TODO: override visitor methods matching getApplicableUastTypes()"
            )
            method_blocks.append(dedent(f"""\
                @Override
                public UElementHandler createUastHandler(@NonNull JavaContext context) {{
                    return new UElementHandler() {{
                {inner}
                    }};
                }}"""))

        indent = "    "
        body = "\n\n".join(
            "\n".join(indent + line for line in block.splitlines())
            for block in method_blocks
        )

        # Infer imports — also inject Collection/List/Nullable tokens as needed
        collection_hint = " Collection" if any(
            m in ("getApplicableElements", "getApplicableAttributes") for m in methods
        ) else ""
        list_hint = " List" if any(
            m in ("getApplicableMethodNames", "getApplicableReferenceNames",
                  "getApplicableConstructorTypes", "getApplicableUastTypes",
                  "applicableSuperClasses", "applicableAnnotations") for m in methods
        ) else ""
        nullable_hint = " Nullable" if "checkDslPropertyAssignment" in methods else ""
        all_stubs = "\n".join(method_blocks) + f" {iface_str} {scope}{collection_hint}{list_hint}{nullable_hint}"
        extra = _collect_imports(all_stubs, _JAVA_TYPE_IMPORTS)
        imports = "\n".join(sorted(set(_BASE_JAVA_IMPORTS + extra)))

        _BASE_PROVIDES_IFACE = {"LayoutDetector", "ResourceXmlDetector"}
        if base_class in _BASE_PROVIDES_IFACE:
            java_class_header = f"public class {detector} extends {base_class} {{"
        else:
            java_class_header = f"public class {detector} extends {base_class} implements {iface_str} {{"

        header = [
            f"package com.android.tools.lint.checks;",
            "",
            imports,
            "",
            java_class_header,
            "",
            f"    private static final Implementation IMPLEMENTATION =",
            f"            new Implementation({detector}.class, {scope});",
            "",
            f"    public static final Issue ISSUE =",
            f"            Issue.create(",
            f'                    "{issue_id}",',
            f'                    "{brief}",',
            f'                    "TODO",',
            f"                    Category.{category},",
            f"                    {priority},",
            f"                    Severity.{severity},",
            f"                    IMPLEMENTATION);",
        ]
        sections = header
        if body:
            sections += ["", body]
        sections.append("}")
        return "\n".join(sections)


# ---------------------------------------------------------------------------
# Prompt templates
# ---------------------------------------------------------------------------

SYSTEM_PROMPT = """\
You are an expert Android developer specialising in Android Lint custom checks.
You write Lint Detector implementations in {lang} that are correct, idiomatic,
and compile cleanly against the Android Lint API.

Rules:
- Output ONLY the detector source file. No explanation, no markdown fences.
- Use the exact package: com.android.tools.lint.checks
- The class name must match the detector name derived from the issue ID.
- Implement every method listed in the required methods.
- Import only from: com.android.tools.lint.*, com.intellij.psi.*, org.jetbrains.uast.*
- Do NOT include a main() method or any test code.
"""

# ── zero_shot ────────────────────────────────────────────────────────────────

ZERO_SHOT_TEMPLATE = """\
Implement an Android Lint Detector for the following issue.

Specification:
{nl_spec}
{more_info}
Generate the complete source file now.\
"""

# ── api_hint (merged with structured_zero_shot) ───────────────────────────────

API_HINT_TEMPLATE = """\
Implement an Android Lint Detector in {lang} for the following issue.

Issue ID: {issue_id}
Detector class name: {detector}
Language: {lang}
Category: {category}
Severity: {severity}
Base class to extend: {base_class}
Scanner interfaces to implement: {scanner_interfaces}

Specification:
{nl_spec}

Lint API methods to override:
{methods_list}
{api_hint}{more_info}
Output contract — follow exactly:
- Generate exactly one {lang} source file
- Package must be: com.android.tools.lint.checks
- Class name must be: {detector}
- Do NOT wrap the output in markdown code fences
- Do NOT include any explanation, comments outside the file, or extra text
- The file must start with: package com.android.tools.lint.checks

Generate the complete {detector}.{ext} source file now.\
"""

# ── skeleton ─────────────────────────────────────────────────────────────────

SKELETON_TEMPLATE = """\
Complete the following Android Lint Detector skeleton in {lang}.

Issue ID: {issue_id}
Detector class name: {detector}
Language: {lang}
Category: {category}
Severity: {severity}
Base class to extend: {base_class}

Specification:
{nl_spec}

Starter skeleton — fill in all TODO() stubs and add any helper methods needed:
```{ext}
{skeleton}
```
{more_info}
Output the complete {detector}.{ext} source file with all methods fully implemented.\
"""

# ── few_shot ─────────────────────────────────────────────────────────────────

FEW_SHOT_TEMPLATE = """\
{example_header}

{example}

---

Now implement a NEW Android Lint Detector in {lang} for the following issue.

Issue ID: {issue_id}
Detector class name: {detector}
Language: {lang}
Category: {category}
Severity: {severity}
Base class to extend: {base_class}
Scanner interfaces to implement: {scanner_interfaces}

Specification:
{nl_spec}

Lint API methods to override:
{methods_list}
{more_info}
Generate the complete {detector}.{ext} source file now.\
"""

# ── few_shot_surface_matched — reuses FEW_SHOT_TEMPLATE with surface-picked example ──

# ── compile_repair ───────────────────────────────────────────────────────────

COMPILE_REPAIR_SYSTEM = """\
You are an expert Android developer specialising in Android Lint custom checks.
Fix compilation errors in {lang} Android Lint Detector code.

Rules:
- Output ONLY the corrected source file. No explanation, no markdown fences.
- Preserve the exact package: com.android.tools.lint.checks
- Preserve the class name: {detector}
- Fix every compilation error listed. Do not introduce new errors.
- Do NOT remove or change the detector logic — only fix compilation issues.
"""

COMPILE_REPAIR_TEMPLATE = """\
The following {lang} Android Lint Detector failed to compile.
Fix all compilation errors and return the corrected source file.

Compilation errors:
{compile_errors}

Original code:
```{ext}
{original_code}
```

Output ONLY the corrected {detector}.{ext} source file. \
Do not include any explanation or markdown fences.\
"""

# ---------------------------------------------------------------------------
# All supported prompt variants (for CLI validation)
# ---------------------------------------------------------------------------

ALL_VARIANTS = [
    "zero_shot",
    "api_hint",
    "skeleton",
    "few_shot_surface_matched",
]


# ---------------------------------------------------------------------------
# Public API
# ---------------------------------------------------------------------------

def build_prompt(instance: dict, variant: str) -> tuple[str, str]:
    """Return (system_prompt, user_prompt) for the given instance and variant."""
    lang     = "Kotlin" if instance["check_lang"] == "kt" else "Java"
    ext      = instance["check_lang"]
    issue_id = instance["issue_id"]
    detector = instance["detector"]

    methods_list = "\n".join(
        f"  {i+1}. {m}" for i, m in enumerate(instance["methods_to_generate"])
    )

    more_info = ""
    if instance.get("more_info_urls"):
        urls = "\n".join(f"  - {u}" for u in instance["more_info_urls"])
        more_info = f"\nReference documentation:\n{urls}\n"

    base_class = instance.get("base_class", "Detector")

    common = dict(
        lang=lang,
        ext=ext,
        issue_id=issue_id,
        detector=detector,
        category=instance["category"],
        severity=instance["severity"],
        base_class=base_class,
        scanner_interfaces=", ".join(instance["scanner_interfaces"]) or "SourceCodeScanner",
        nl_spec=instance["nl_spec"],
        methods_list=methods_list,
        more_info=more_info,
    )

    system = SYSTEM_PROMPT.format(lang=lang)

    if variant == "zero_shot":
        user = ZERO_SHOT_TEMPLATE.format(**common)

    elif variant == "api_hint":
        api_hint = _build_api_hint(instance)
        user = API_HINT_TEMPLATE.format(api_hint=api_hint, **common)

    elif variant == "skeleton":
        skeleton = _build_skeleton(instance)
        user = SKELETON_TEMPLATE.format(skeleton=skeleton, **common)

    elif variant == "few_shot_surface_matched":
        examples = _pick_examples(instance, ext)
        n = examples.count("\n\n---\n\n") + 1
        header = (
            f"Here {'are' if n > 1 else 'is'} {n} example{'s' if n > 1 else ''} "
            f"of complete Android Lint Detector{'s' if n > 1 else ''} in {lang} "
            f"using the same scanner interface{'s' if n > 1 else ''} "
            f"({common['scanner_interfaces']}):"
        )
        user = FEW_SHOT_TEMPLATE.format(example=examples, example_header=header, **common)

    else:
        raise ValueError(
            f"Unknown prompt variant: {variant!r}. "
            f"Choose from: {', '.join(ALL_VARIANTS)}"
        )

    return system, user


def build_repair_prompt(
    instance: dict,
    original_code: str,
    compile_errors: list[str],
) -> tuple[str, str]:
    """
    Return (system_prompt, user_prompt) for a compile-repair pass.

    Shows the model the failed code and compiler errors so it can
    produce a corrected file (compile_repair_1 mode).
    """
    lang     = "Kotlin" if instance["check_lang"] == "kt" else "Java"
    ext      = instance["check_lang"]
    detector = instance["detector"]

    errors_str = "\n".join(compile_errors) if compile_errors else "(no error detail available)"

    system = COMPILE_REPAIR_SYSTEM.format(lang=lang, detector=detector)
    user   = COMPILE_REPAIR_TEMPLATE.format(
        lang=lang,
        ext=ext,
        detector=detector,
        compile_errors=errors_str,
        original_code=original_code,
    )
    return system, user


# ---------------------------------------------------------------------------
# Stub / smoke-test helpers (unchanged from generate/)
# ---------------------------------------------------------------------------

_STUB_KT = """\
package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import org.jetbrains.uast.UCallExpression
import com.intellij.psi.PsiMethod

class {detector} : Detector(), SourceCodeScanner {{
    companion object {{
        val ISSUE = Issue.create(
            id = "{issue_id}",
            briefDescription = "Stub",
            explanation = "Stub detector generated by smoke test.",
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation({detector}::class.java, Scope.JAVA_FILE_SCOPE)
        ){extra_constants}
    }}

    override fun getApplicableMethodNames(): List<String> = emptyList()
}}
"""

_APIDETECTOR_EXTRA_KT = """

        // Constants expected by ApiDetectorTest static imports.
        @JvmField val UNSUPPORTED: Issue = Issue.create("NewApi", "Stub", "Stub",
            Category.CORRECTNESS, 6, Severity.ERROR,
            Implementation(ApiDetector::class.java, Scope.JAVA_FILE_SCOPE))
        @JvmField val INLINED: Issue = Issue.create("InlinedApi", "Stub", "Stub",
            Category.CORRECTNESS, 6, Severity.WARNING,
            Implementation(ApiDetector::class.java, Scope.JAVA_FILE_SCOPE))
        @JvmField val UNUSED: Issue = Issue.create("UnusedAttribute", "Stub", "Stub",
            Category.CORRECTNESS, 6, Severity.WARNING,
            Implementation(ApiDetector::class.java, Scope.JAVA_FILE_SCOPE))
        @JvmField val OBSOLETE_SDK: Issue = Issue.create("ObsoleteSdkInt", "Stub", "Stub",
            Category.PERFORMANCE, 6, Severity.WARNING,
            Implementation(ApiDetector::class.java, Scope.JAVA_FILE_SCOPE))
        const val KEY_REQUIRES_API = "requiresApi"
        const val REPEATED_API_ANNOTATION_REQUIRES_ALL = true"""

_STUB_JAVA = """\
package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.*;
import java.util.Collections;
import java.util.List;

public class {detector} extends Detector implements SourceCodeScanner {{

    public static final Issue ISSUE = Issue.create(
            "{issue_id}", "Stub", "Stub detector generated by smoke test.",
            Category.CORRECTNESS, 5, Severity.WARNING,
            new Implementation({detector}.class, Scope.JAVA_FILE_SCOPE));

    @Override
    public List<String> getApplicableMethodNames() {{
        return Collections.emptyList();
    }}
}}
"""


def stub_detector(instance: dict) -> str:
    """Return a minimal syntactically valid detector for smoke testing."""
    if instance["check_lang"] == "kt":
        extra = _APIDETECTOR_EXTRA_KT if instance["detector"] == "ApiDetector" else ""
        return _STUB_KT.format(
            detector=instance["detector"],
            issue_id=instance["issue_id"],
            extra_constants=extra,
        )
    return _STUB_JAVA.format(
        detector=instance["detector"],
        issue_id=instance["issue_id"],
    )


def extract_code(raw: str, ext: str) -> str:
    """
    Extract source code from the model's response.
    Handles:
      1. ```kotlin/java/... fenced blocks  — picks the block that starts with
         'package', falling back to the longest block (avoids grabbing a small
         snippet Claude writes during CoT reasoning)
      2. Plain ``` fenced blocks (same preference logic)
      3. Raw code (starts with package/comment)
      4. CoT prefix — reasoning text before the package declaration (strips preamble)
    """
    # 1 & 2: fenced blocks
    for lang_tag in (ext, "kotlin" if ext == "kt" else "java", ""):
        pattern = rf"```{lang_tag}\s*\n(.*?)```"
        blocks = [m.strip() for m in re.findall(pattern, raw, re.DOTALL | re.IGNORECASE)]
        if blocks:
            # Prefer the block that looks like a full source file
            for block in blocks:
                if block.startswith("package"):
                    return block
            return max(blocks, key=len)

    # 3: raw code with no preamble
    stripped = raw.strip()
    if stripped.startswith("package") or stripped.startswith("/*"):
        return stripped

    # 4: CoT preamble — find the first package declaration and take everything from there
    m = re.search(r"^(package\s+\S)", raw, re.MULTILINE)
    if m:
        return raw[m.start():].strip()

    return stripped
