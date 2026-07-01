package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
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

public class UnsafeNativeCodeDetector extends Detector implements Detector.UastScanner {

    public static final Issue ISSUE = Issue.create(
            "UnsafeDynamicallyLoadedCode",
            "Dynamically loaded code",
            "Dynamically loading code from locations other than the application's library "
                    + "directory or the Android platform's built-in library directories is dangerous, "
                    + "as there is an increased risk that the code could have been tampered with. "
                    + "Applications should use `loadLibrary` when possible, which provides increased "
                    + "assurance that libraries are loaded from one of these safer locations. "
                    + "Application developers should use the features of their development "
                    + "environment to place application native libraries into the lib directory "
                    + "of their compiled APKs.",
            Category.SECURITY,
            6,
            Severity.WARNING,
            new Implementation(UnsafeNativeCodeDetector.class, Scope.JAVA_FILE_SCOPE));

    @Override
    public List<String> getApplicableMethodNames() {
        return Collections.singletonList("load");
    }

    @Override
    public void visitMethodCall(@NonNull JavaContext context, @NonNull UCallExpression node,
            @NonNull PsiMethod method) {
        if (!context.getEvaluator().isMemberInSubClassOf(method, "java.lang.Runtime", false)
                && !context.getEvaluator().isMemberInSubClassOf(method, "java.lang.System", false)) {
            return;
        }

        context.report(ISSUE, node, context.getLocation(node),
                "Dynamically loading code is unsafe; use loadLibrary when possible");
    }
}