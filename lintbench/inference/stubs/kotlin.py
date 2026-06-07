"""
inference/stubs/kotlin.py
-------------------------
Kotlin method stub tables for the skeleton prompt variant.
"""

from textwrap import dedent

# Methods that live inside the UElementHandler returned by createUastHandler(),
# not directly on the Detector class.
UAST_HANDLER_METHODS: frozenset = frozenset({
    "visitMethod", "visitCallExpression", "visitSimpleNameReferenceExpression",
    "visitBinaryExpression", "visitReturnExpression", "visitIfExpression",
    "visitAnnotation", "visitThrowExpression", "visitDeclarationsExpression",
    "visitClassLiteralExpression", "visitCallableReferenceExpression",
    "visitParameter", "visitTypeReferenceExpression",
    "visitQualifiedReferenceExpression", "visitImportStatement",
    "visitVariable", "visitBlockExpression",
})

# Kotlin stubs for direct Detector overrides (no leading indent)
KT_STUBS: dict[str, str] = {
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
KT_HANDLER_STUBS: dict[str, str] = {
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

# Type name → Kotlin import line
KT_TYPE_IMPORTS: dict[str, str] = {
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

BASE_KT_IMPORTS: list[str] = [
    "import com.android.tools.lint.detector.api.Category",
    "import com.android.tools.lint.detector.api.Detector",
    "import com.android.tools.lint.detector.api.Implementation",
    "import com.android.tools.lint.detector.api.Issue",
    "import com.android.tools.lint.detector.api.Scope",
    "import com.android.tools.lint.detector.api.Severity",
]
