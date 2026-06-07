"""
inference/stubs/java.py
-----------------------
Java method stub tables for the skeleton prompt variant.
"""

from textwrap import dedent

# Java stubs for direct Detector overrides
JAVA_STUBS: dict[str, str] = {
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
JAVA_HANDLER_STUBS: dict[str, str] = {
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

# Type name → Java import line
JAVA_TYPE_IMPORTS: dict[str, str] = {
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

BASE_JAVA_IMPORTS: list[str] = [
    "import com.android.annotations.NonNull;",
    "import com.android.tools.lint.detector.api.Category;",
    "import com.android.tools.lint.detector.api.Detector;",
    "import com.android.tools.lint.detector.api.Implementation;",
    "import com.android.tools.lint.detector.api.Issue;",
    "import com.android.tools.lint.detector.api.Scope;",
    "import com.android.tools.lint.detector.api.Severity;",
]
