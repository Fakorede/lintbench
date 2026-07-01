package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.intellij.psi.PsiMethod;
import java.util.Collections;
import java.util.List;
import org.jetbrains.uast.UCallExpression;

public class UnsafeNativeCodeDetector extends Detector implements Detector.SourceCodeScanner {

    public static final Issue ISSUE = Issue.create(
            "UnsafeDynamicallyLoadedCode",
            "Unsafe dynamic code loading",
            "Dynamically loading code from locations other than the application's library "
                    + "directory or the Android platform's built-in library directories is dangerous, "
                    + "as there is an increased risk that the code could have been tampered with. "
                    + "Applications should use `loadLibrary` when possible, which provides increased "
                    + "assurance that libraries are loaded from one of these safer locations. "
                    + "Application developers should use the features of their development environment "
                    + "to place application native libraries into the lib directory of their compiled APKs.",
            Category.SECURITY,
            6,
            Severity.WARNING,
            new Implementation(
                    UnsafeNativeCodeDetector.class,
                    Scope.JAVA_FILE_SCOPE));

    @Override
    public List<String> getApplicableMethodNames() {
        return Collections.singletonList("load");
    }

    @Override
    public void visitMethodCall(JavaContext context, UCallExpression call, PsiMethod method) {
        if (method == null) {
            return;
        }

        PsiMethod directMethod = call.resolve();
        if (directMethod == null) {
            directMethod = method;
        }

        PsiMethod containingMethod = directMethod;
        if (containingMethod.getContainingClass() == null) {
            return;
        }

        String className = containingMethod.getContainingClass().getQualifiedName();
        if ("java.lang.System".equals(className) || "java.lang.Runtime".equals(className)) {
            context.report(
                    ISSUE,
                    call,
                    context.getLocation(call),
                    "Dynamically loading code via `load` is unsafe; use `loadLibrary` instead");
        }
    }
}