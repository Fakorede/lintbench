package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.ULambdaExpression;
import org.jetbrains.uast.ULiteralExpression;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UParameter;
import org.jetbrains.uast.UParenthesizedExpression;
import org.jetbrains.uast.UReturnExpression;
import org.jetbrains.uast.visitor.AbstractUastVisitor;

import java.util.Collections;
import java.util.List;

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
        for (UMethod method : declaration.getMethods()) {
            if ("verify".equals(method.getName())) {
                List<UParameter> parameters = method.getUastParameters();
                if (parameters.size() == 2) {
                    UExpression body = method.getUastBody();
                    if (body != null && alwaysReturnsTrue(body)) {
                        context.report(
                                ISSUE,
                                method,
                                context.getNameLocation(method),
                                "Strict HostnameVerifier checks are disabled. This `verify` method always returns `true` (trusting any hostname), which is insecure."
                        );
                    }
                }
            }
        }
    }

    private static boolean alwaysReturnsTrue(@NotNull UExpression body) {
        if (isTrueLiteral(body)) {
            return true;
        }
        ReturnVisitor visitor = new ReturnVisitor();
        body.accept(visitor);
        return visitor.hasReturns() && visitor.allReturnsTrue();
    }

    private static boolean isTrueLiteral(@Nullable UExpression expression) {
        if (expression instanceof ULiteralExpression) {
            Object value = ((ULiteralExpression) expression).getValue();
            return Boolean.TRUE.equals(value);
        }
        if (expression instanceof UParenthesizedExpression) {
            return isTrueLiteral(((UParenthesizedExpression) expression).getExpression());
        }
        return false;
    }

    private static class ReturnVisitor extends AbstractUastVisitor {
        private boolean hasReturns = false;
        private boolean allReturnsTrue = true;

        @Override
        public boolean visitReturnExpression(@NotNull UReturnExpression node) {
            hasReturns = true;
            UExpression returnExpression = node.getReturnExpression();
            if (returnExpression == null || !isTrueLiteral(returnExpression)) {
                allReturnsTrue = false;
            }
            return super.visitReturnExpression(node);
        }

        @Override
        public boolean visitLambdaExpression(@NotNull ULambdaExpression node) {
            return true; // Skip lambdas inside the verify method
        }

        @Override
        public boolean visitClass(@NotNull UClass node) {
            return true; // Skip anonymous/local classes inside the verify method
        }

        public boolean hasReturns() {
            return hasReturns;
        }

        public boolean allReturnsTrue() {
            return allReturnsTrue;
        }
    }
}