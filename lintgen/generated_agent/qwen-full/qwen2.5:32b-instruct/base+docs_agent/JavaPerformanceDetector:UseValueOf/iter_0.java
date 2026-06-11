package com.android.tools.lint.checks;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiNewExpression;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UastBinaryOwnerNode;
import org.jetbrains.uast.visitor.AbstractUastVisitor;

import java.util.Arrays;
import java.util.List;

public class JavaPerformanceDetector extends Detector implements Detector.UastScanner {
    private static final String ISSUE_ID = "UseValueOf";
    private static final String ISSUE_NAME = "Should use valueOf instead of new for wrapper classes";
    private static final String ISSUE_EXPLANATION =
            "You should not call the constructor for wrapper classes directly, such as `new Integer(42)`. Instead, call the `valueOf` factory method, such as `Integer.valueOf(42)`." +
                    " This will typically use less memory because common integers such as 0 and 1 will share a single instance.";

    private static final Issue ISSUE = Issue.create(
            ISSUE_ID,
            ISSUE_NAME,
            ISSUE_EXPLANATION,
            Category.PERFORMANCE,
            5, // Priority
            Severity.WARNING,
            new Implementation(JavaPerformanceDetector.class, Scope.JAVA_FILE_SCOPE)
    );

    @Override
    public List<Class<? extends UastBinaryOwnerNode>> getApplicableUastNodes() {
        return Arrays.asList(PsiNewExpression.class);
    }

    @Override
    public void visitMethodCallExpression(@SuppressWarnings("rawtypes") PsiElement element) {
        if (element instanceof PsiNewExpression) {
            PsiNewExpression newExpr = (PsiNewExpression) element;
            String className = newExpr.getClassReference().getQualifiedName();
            if (isWrapperClass(className)) {
                reportIssue(element);
            }
        }
    }

    private boolean isWrapperClass(String className) {
        return "java.lang.Integer".equals(className)
                || "java.lang.Long".equals(className)
                || "java.lang.Short".equals(className)
                || "java.lang.Byte".equals(className)
                || "java.lang.Double".equals(className)
                || "java.lang.Float".equals(className)
                || "java.lang.Character".equals(className);
    }

    private void reportIssue(PsiElement element) {
        context.report(ISSUE, element, context.getLocation(element), ISSUE_EXPLANATION);
    }
}