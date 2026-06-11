package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;

import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElementHandler;
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
            6,
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
    public void visitMethodCall(JavaContext context, UCallExpression call) {
        if (call.getReceiver() != null && call.getMethodName().equals("<init>")) {
            context.report(ISSUE, call, context.getLocation(call), "Consider using SparseArray for better performance when keys are integers.");
        }
    }

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.singletonList(UCallExpression.class);
    }

    @Override
    public UElementHandler createUastHandler(final JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitClass(UClass node) {
                for (UMethod method : node.getMethods()) {
                    if ("HashMap".equals(method.getName())) {
                        context.report(ISSUE, method, context.getLocation(method), "Consider using SparseArray for better performance when keys are integers.");
                    }
                }
            }

            @Override
            public void visitMethodCall(UCallExpression call) {
                if (call.getReceiver() != null && call.getMethodName().equals("<init>")) {
                    context.report(ISSUE, call, context.getLocation(call), "Consider using SparseArray for better performance when keys are integers.");
                }
            }
        };
    }
}