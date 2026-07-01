package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import java.util.Collections;
import java.util.List;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UExpression;

public class UnsafeNativeCodeDetector extends Detector implements SourceCodeScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(UnsafeNativeCodeDetector.class, Scope.JAVA_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "UnsafeNativeCodeLocation",
                    "Native code outside library directory",
                    "In general, application native code should only be placed in the application's "
                    + "library directory, not in other locations such as the res or assets directories. "
                    + "Placing the code in the library directory provides increased assurance that the "
                    + "code will not be tampered with after application installation. Application "
                    + "developers should use the features of their development environment to place "
                    + "application native libraries into the lib directory of their compiled APKs. "
                    + "Embedding non-shared library native executables into applications should "
                    + "be avoided when possible.",
                    Category.SECURITY,
                    4,
                    Severity.WARNING,
                    IMPLEMENTATION);

    @Override
    public List<String> getApplicableMethodNames() {
        return Collections.singletonList("load");
    }

    @Override
    public void visitMethodCall(
            @NonNull JavaContext context,
            @NonNull UCallExpression node,
            @NonNull PsiMethod method) {
        String methodName = method.getName();
        if (!"load".equals(methodName)) {
            return;
        }

        PsiClass containingClass = method.getContainingClass();
        if (containingClass == null) {
            return;
        }
        String qualifiedName = containingClass.getQualifiedName();
        if (!"java.lang.System".equals(qualifiedName) && !"java.lang.Runtime".equals(qualifiedName)) {
            return;
        }

        List<UExpression> valueArguments = node.getValueArguments();
        if (valueArguments.size() != 1) {
            return;
        }

        UExpression argument = valueArguments.get(0);
        if (isUnsafePath(argument)) {
            context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    "Shared libraries should be loaded from the application's library directory using `System.loadLibrary` rather than `System.load` with a hardcoded or dynamic path.");
        }
    }

    private boolean isUnsafePath(UExpression expression) {
        if (expression == null) {
            return true;
        }

        String source = expression.asSourceString();
        if (source == null) {
            return true;
        }

        String lower = source.toLowerCase();

        // If it explicitly references system or vendor directories, we don't flag it as unsafe native code location
        if (lower.startsWith("\"/system/") || lower.startsWith("\"/vendor/") || lower.startsWith("\"/apex/")) {
            return false;
        }

        // If it explicitly references nativeLibraryDir, it's safe
        if (lower.contains("nativelibrarydir") || lower.contains("nativelibdir")) {
            return false;
        }

        return true;
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        // No-op
    }
}