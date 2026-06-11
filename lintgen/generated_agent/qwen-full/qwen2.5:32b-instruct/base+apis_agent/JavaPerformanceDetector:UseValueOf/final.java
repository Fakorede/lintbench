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
import org.jetbrains.uast.ULiteralExpression;
import org.jetbrains.uast.USimpleNameReferenceExpression;
import org.jetbrains.uast.UElement;

import java.util.Collections;
import java.util.List;

public class JavaPerformanceDetector extends Detector implements SourceCodeScanner {

    public static final Issue ISSUE = Issue.create(
            "UseValueOf",
            "Should use `valueOf` instead of `new` for wrapper classes.",
            "You should not call the constructor for wrapper classes directly, such as `new Integer(42)`. Instead, call the `Integer.valueOf` factory method, such as `Integer.valueOf(42)`. This will typically use less memory because common integers such as 0 and 1 will share a single instance.",
            Category.PERFORMANCE,
            5,
            Severity.WARNING,
            new Implementation(
                    JavaPerformanceDetector.class,
                    Scope.JAVA_FILE_SCOPE
            )
    );

    @Override
    public List<String> getApplicableConstructorTypes() {
        return Collections.singletonList("java.lang.Integer");
    }

    @Override
    public void visitConstructor(JavaContext context, UCallExpression node, PsiMethod constructor) {
        if (constructor.getName().equals("<init>") && constructor.getParameterList().getParametersCount() == 1) {
            final UElement argument = node.getReceiver();
            if (argument instanceof ULiteralExpression || argument instanceof USimpleNameReferenceExpression) {
                context.report(ISSUE, node, context.getLocation(node),
                        "Use `Integer.valueOf()` instead of the constructor for better memory usage.");
            }
        }
    }

    @Override
    public List<String> getApplicableMethodNames() {
        return Collections.singletonList("valueOf");
    }

    @Override
    public void visitMethodCall(JavaContext context, UCallExpression node, PsiMethod method) {
        if (method.getName().equals("valueOf") && method.getParameterList().getParametersCount() == 1) {
            // No action needed here as this is the correct usage.
        }
    }

}