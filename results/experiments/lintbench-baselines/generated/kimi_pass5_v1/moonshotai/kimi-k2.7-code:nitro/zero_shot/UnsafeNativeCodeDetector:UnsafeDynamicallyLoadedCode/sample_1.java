package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.TextType;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import java.util.Arrays;
import java.util.List;
import org.jetbrains.uast.UCallExpression;

public class UnsafeNativeCodeDetector extends Detector implements Detector.UastScanner {

    private static final Implementation IMPLEMENTATION = new Implementation(
            UnsafeNativeCodeDetector.class,
            Scope.JAVA_FILE_SCOPE
    );

    public static final Issue ISSUE = Issue.create(
            "UnsafeDynamicallyLoadedCode",
            "`load` used to dynamically load code",
            "Dynamically loading code from locations other than the application's library "
                    + "directory or the Android platform's built-in library directories is "
                    + "dangerous, as there is an increased risk that the code could have been "
                    + "tampered with. Applications should use `loadLibrary` when possible, "
                    + "which provides increased assurance that libraries are loaded from one "
                    + "of these safer locations. Application developers should use the features "
                    + "of their development environment to place application native libraries "
                    + "into the lib directory of their compiled APKs.",
            Category.SECURITY,
            6,
            Severity.WARNING,
            IMPLEMENTATION
    );

    @Override
    public List<String> applicableFunctionNames() {
        return Arrays.asList("load");
    }

    @Override
    public void visitFunctionCall(JavaContext context, UCallExpression node) {
        if (node.isConstructor()) {
            return;
        }

        PsiMethod method = node.resolve();
        if (method == null) {
            return;
        }

        PsiClass containingClass = method.getContainingClass();
        if (containingClass == null) {
            return;
        }

        String className = containingClass.getQualifiedName();
        if ("java.lang.Runtime".equals(className) || "java.lang.System".equals(className)) {
            String message = ISSUE.getBriefDescription(TextType.TEXT);
            context.report(ISSUE, node, context.getLocation(node), message);
        }
    }
}