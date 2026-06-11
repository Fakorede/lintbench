package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;

import com.intellij.psi.PsiMethod;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UMethod;

import java.util.Collections;
import java.util.List;

public class JavaPerformanceDetector extends Detector implements Detector.SourceCodeScanner {

    public static final Issue ISSUE = Issue.create(
            "UseSparseArray",
            "HashMap can be replaced with SparseArray for better performance when keys are integers.",
            "For maps where the keys are of type integer, it's typically more efficient to use the Android `SparseArray` API. This check identifies scenarios where you might want to consider using `SparseArray` instead of `HashMap` for better performance.\n" +
                    "\n" +
                    "This is particularly useful when the value types are primitives like ints, where you can use `SparseIntArray` and avoid auto-boxing the values from `int` to `Integer`.",
            Category.PERFORMANCE,
            5,
            Severity.WARNING,
            new Implementation(
                    JavaPerformanceDetector.class,
                    Scope.JAVA_FILE_SCOPE
            )
    );

    @Override
    public List<String> getApplicableMethodNames() {
        return Collections.singletonList("HashMap");
    }

    @Override
    public void visitMethodCall(JavaContext context, UCallExpression call, PsiMethod method) {
        if (method != null && method.getName().equals("<init>") && method.getContainingClass() != null &&
                method.getContainingClass().getQualifiedName().equals("java.util.HashMap")) {

            // Report the issue
            context.report(ISSUE, call, context.getLocation(call), "Consider using SparseArray instead of HashMap for better performance");
        }
    }

    @Override
    public List<String> getApplicableConstructorTypes() {
        return Collections.singletonList("HashMap");
    }

    @Override
    public void visitConstructor(JavaContext context, UCallExpression node, PsiMethod constructor) {
        if (constructor != null && constructor.getName().equals("<init>") && constructor.getContainingClass() != null &&
                constructor.getContainingClass().getQualifiedName().equals("java.util.HashMap")) {

            // Report the issue
            context.report(ISSUE, node, context.getLocation(node), "Consider using SparseArray instead of HashMap for better performance");
        }
    }

}