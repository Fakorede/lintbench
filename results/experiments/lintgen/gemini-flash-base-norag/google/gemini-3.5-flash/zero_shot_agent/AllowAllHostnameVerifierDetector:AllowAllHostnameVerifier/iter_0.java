package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.intellij.psi.PsiType;
import java.util.Collections;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.ULiteralExpression;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UReturnExpression;
import org.jetbrains.uast.visitor.AbstractUastVisitor;

public class AllowAllHostnameVerifierDetector extends Detector implements Detector.UastScanner {

    public static final Issue ISSUE = Issue.create(
            "AllowAllHostnameVerifier",
            "Insecure HostnameVerifier",
            "This check looks for use of HostnameVerifier implementations whose `verify` " +
            "method always returns true (thus trusting any hostname) which could result " +
            "in insecure network traffic caused by trusting arbitrary hostnames in TLS/SSL " +
            "certificates presented by peers.",
            Category.SECURITY,
            6,
            Severity.WARNING,
            new Implementation(
                    AllowAllHostnameVerifierDetector.class,
                    Scope.JAVA_FILE_SCOPE
            )
    ).addMoreInfo("https://goo.gle/AllowAllHostnameVerifier");

    @Nullable
    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList("javax.net.ssl.HostnameVerifier");
    }

    @Override
    public void visitClass(@NotNull JavaContext context, @NotNull UClass declaration) {
        if (declaration.isInterface()) {
            return;
        }

        UMethod verifyMethod = null;
        for (UMethod method : declaration.getMethods()) {
            if ("verify".equals(method.getName())) {
                if (method.getUastParameters().size() == 2) {
                    PsiType returnType = method.getReturnType();
                    if (returnType != null && (PsiType.BOOLEAN.equals(returnType) || "java.lang.Boolean".equals(returnType.getCanonicalText()))) {
                        verifyMethod = method;
                        break;
                    }
                }
            }
        }

        if (verifyMethod != null && alwaysReturnsTrue(verifyMethod)) {
            context.report(
                    ISSUE,
                    verifyMethod,
                    context.getNameLocation(verifyMethod),
                    "Trusting all hostnames can lead to insecure network connections"
            );
        }
    }

    private static boolean alwaysReturnsTrue(@NotNull UMethod method) {
        UExpression body = method.getUastBody();
        if (body == null) {
            return false;
        }

        UExpression singleExpression = skipParentheses(body);
        if (singleExpression instanceof ULiteralExpression) {
            return Boolean.TRUE.equals(((ULiteralExpression) singleExpression).getValue());
        }
        if (singleExpression instanceof UReturnExpression) {
            UExpression returnVal = ((UReturnExpression) singleExpression).getReturnExpression();
            return returnVal != null && isTrueLiteral(returnVal);
        }

        ReturnVisitor visitor = new ReturnVisitor();
        body.accept(visitor);
        return visitor.hasReturns() && visitor.alwaysReturnsTrue();
    }

    private static UExpression skipParentheses(UExpression expression) {
        while (expression instanceof org.jetbrains.uast.UParenthesizedExpression) {
            expression = ((org.jetbrains.uast.UParenthesizedExpression) expression).getExpression();
        }
        return expression;
    }

    private static boolean isTrueLiteral(UExpression expression) {
        if (expression == null) {
            return false;
        }
        UExpression clean = skipParentheses(expression);
        if (clean instanceof ULiteralExpression) {
            Object value = ((ULiteralExpression) clean).getValue();
            return Boolean.TRUE.equals(value);
        }
        return false;
    }

    private static class ReturnVisitor extends AbstractUastVisitor {
        private int returnCount = 0;
        private int trueReturnCount = 0;

        public boolean hasReturns() {
            return returnCount > 0;
        }

        public boolean alwaysReturnsTrue() {
            return returnCount == trueReturnCount;
        }

        @Override
        public boolean visitReturnExpression(@NotNull UReturnExpression node) {
            returnCount++;
            UExpression returnExpression = node.getReturnExpression();
            if (returnExpression != null && isTrueLiteral(returnExpression)) {
                trueReturnCount++;
            }
            return super.visitReturnExpression(node);
        }

        @Override
        public boolean visitLambdaExpression(@NotNull org.jetbrains.uast.ULambdaExpression node) {
            return true; // Skip nested lambdas
        }

        @Override
        public boolean visitClass(@NotNull UClass node) {
            return true; // Skip nested classes
        }
    }
}