package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.*;
import com.intellij.psi.PsiClass;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.uast.*;

import java.util.Arrays;
import java.util.List;

public class AllowAllHostnameVerifierDetector extends Detector implements Detector.UastScanner {
    public static final Issue ISSUE = Issue.create(
        "AllowAllHostnameVerifier",
        "Insecure HostnameVerifier",
        "This check looks for use of HostnameVerifier implementations whose `verify` method always returns true (thus trusting any hostname) which could result in insecure network traffic caused by trusting arbitrary hostnames in TLS/SSL certificates presented by peers.",
        Category.SECURITY,
        6,
        Severity.WARNING,
        new Implementation(AllowAllHostnameVerifierDetector.class, Scope.JAVA_FILE_SCOPE)
    );

    private static final String HOSTNAME_VERIFIER = "javax.net.ssl.HostnameVerifier";

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Arrays.asList(UMethod.class, ULambdaExpression.class);
    }

    @Override
    public void visitMethod(@NotNull JavaContext context, @NotNull UMethod method) {
        if (!"verify".equals(method.getName())) return;
        if (method.getUastParameters().size() != 2) return;

        PsiClass containingClass = method.getContainingClass();
        if (containingClass == null) return;

        if (!context.getEvaluator().implementsInterface(containingClass, HOSTNAME_VERIFIER, false)) {
            return;
        }

        if (returnsTrue(method.getUastBody())) {
            context.report(ISSUE, method, context.getLocation(method),
                "Insecure HostnameVerifier implementation: verify() always returns true");
        }
    }

    @Override
    public void visitLambdaExpression(@NotNull JavaContext context, @NotNull ULambdaExpression node) {
        if (node.getFunctionalInterfaceType() == null) return;
        String canonicalText = node.getFunctionalInterfaceType().getCanonicalText();
        if (!HOSTNAME_VERIFIER.equals(canonicalText)) return;

        if (returnsTrue(node.getBody())) {
            context.report(ISSUE, node, context.getLocation(node),
                "Insecure HostnameVerifier implementation: verify() always returns true");
        }
    }

    private static boolean returnsTrue(UExpression body) {
        if (body == null) return false;
        UExpression expr = body;
        if (body instanceof UBlockExpression) {
            List<UExpression> statements = ((UBlockExpression) body).getExpressions();
            if (statements.size() == 1 && statements.get(0) instanceof UReturnExpression) {
                expr = ((UReturnExpression) statements.get(0)).getReturnExpression();
            } else {
                return false;
            }
        } else if (body instanceof UReturnExpression) {
            expr = ((UReturnExpression) body).getReturnExpression();
        }

        if (expr instanceof ULiteralExpression) {
            Object value = ((ULiteralExpression) expr).getValue();
            return Boolean.TRUE.equals(value);
        }
        return false;
    }
}