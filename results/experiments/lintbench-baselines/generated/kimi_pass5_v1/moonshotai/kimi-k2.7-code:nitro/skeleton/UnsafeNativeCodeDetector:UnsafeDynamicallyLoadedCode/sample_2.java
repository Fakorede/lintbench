package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.intellij.psi.PsiMethod;
import java.util.Arrays;
import java.util.List;
import org.jetbrains.uast.UCallExpression;

public class UnsafeNativeCodeDetector extends Detector implements SourceCodeScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(UnsafeNativeCodeDetector.class, Scope.JAVA_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "UnsafeDynamicallyLoadedCode",
                    "`load` used to dynamically load code",
                    "Dynamically loading code from locations other than the application's library directory or the Android platform's built-in library directories is dangerous, as there is an increased risk that the code could have been tampered with. Applications should use `loadLibrary` when possible, which provides increased assurance that libraries are loaded from one of these safer locations. Application developers should use the features of their development environment to place application native libraries into the lib directory of their compiled APKs.",
                    Category.SECURITY,
                    4,
                    Severity.WARNING,
                    IMPLEMENTATION);

    @Override
    public List<String> getApplicableMethodNames() {
        return Arrays.asList("load");
    }

    @Override
    public void visitMethodCall(
            JavaContext context,
            UCallExpression node,
            PsiMethod method) {
        if (!"load".equals(method.getName())) {
            return;
        }

        String className = null;
        if (method.getContainingClass() != null) {
            className = method.getContainingClass().getQualifiedName();
        }

        if ("java.lang.System".equals(className) || "java.lang.Runtime".equals(className)) {
            context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    "`load` used to dynamically load code");
        }
    }

    @Override
    public void afterCheckEachProject(Context context) {
        // No additional project-level checks required.
    }
}