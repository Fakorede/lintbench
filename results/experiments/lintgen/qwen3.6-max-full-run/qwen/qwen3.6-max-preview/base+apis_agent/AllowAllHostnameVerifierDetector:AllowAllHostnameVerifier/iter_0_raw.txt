package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.*;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.*;

import java.util.Arrays;
import java.util.List;

public class AllowAllHostnameVerifierDetector extends Detector implements SourceCodeScanner {

    public static final Issue ISSUE = Issue.create(
        "AllowAllHostnameVerifier",
        "Insecure HostnameVerifier",
        "This check looks for use of HostnameVerifier implementations whose `verify` method always returns true (thus trusting any hostname) which could result in insecure network traffic caused by trusting arbitrary hostnames in TLS/SSL certificates presented by peers.",
        Category.SECURITY,
        6,
        Severity.WARNING,
        new Implementation(AllowAllHostnameVerifierDetector.class, Scope.JAVA_FILE)
    );

    @Nullable
    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Arrays.asList(UMethod.class, ULambdaExpression.class);
    }

    @Nullable
    @Override
    public UElementHandler createUastHandler(@NotNull JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitMethod(@NotNull UMethod node) {
                checkMethod(context, node);
            }

            @Override
            public void visitLambdaExpression(@NotNull ULambdaExpression node) {
                checkLambda(context, node);
            }
        };
    }

    private void checkMethod(@NotNull JavaContext context, @NotNull UMethod method) {
        if (!"verify".equals(method.getName())) return;
        if (method.getUastParameters().size() != 2) return;

        UClass containingClass = method.getContainingUClass();
        if (containingClass == null) return;

        JavaEvaluator evaluator = context.getEvaluator();
        if (!evaluator.implementsInterface(containingClass, "javax.net.ssl.HostnameVerifier", false)) {
            return;
        }

        if (alwaysReturnsTrue(method.getUastBody())) {
            context.report(ISSUE, method, context.getLocation(method),
                "Insecure `HostnameVerifier`: `verify()` always returns `true`");
        }
    }

    private void checkLambda(@NotNull JavaContext context, @NotNull ULambdaExpression lambda) {
        PsiType functionalType = lambda.getFunctionalInterfaceType();
        if (functionalType == null) return;

        JavaEvaluator evaluator = context.getEvaluator();
        if (!evaluator.typeMatches(functionalType, "javax.net.ssl.HostnameVerifier")) return;

        if (alwaysReturnsTrue(lambda.getBody())) {
            context.report(ISSUE, lambda, context.getLocation(lambda),
                "Insecure `HostnameVerifier`: `verify()` always returns `true`");
        }
    }

    private boolean alwaysReturnsTrue(@Nullable UExpression body) {
        if (body == null) return false;

        if (body instanceof UBlockExpression) {
            List<UExpression> statements = ((UBlockExpression) body).getExpressions();
            if (statements.size() == 1) {
                UExpression stmt = statements.get(0);
                if (stmt instanceof UReturnExpression) {
                    return isTrueLiteral(((UReturnExpression) stmt).getReturnExpression());
                }
            }
        } else if (body instanceof UReturnExpression) {
            return isTrueLiteral(((UReturnExpression) body).getReturnExpression());
        } else {
            // Expression body (e.g., Kotlin single-expression function or lambda)
            return isTrueLiteral(body);
        }
        return false;
    }

    private boolean isTrueLiteral(@Nullable UExpression expr) {
        if (expr == null) return false;
        expr = UastUtils.skipParentheses(expr);
        if (expr instanceof ULiteralExpression) {
            Object value = ((ULiteralExpression) expr).getValue();
            return Boolean.TRUE.equals(value);
        }
        return false;
    }
}