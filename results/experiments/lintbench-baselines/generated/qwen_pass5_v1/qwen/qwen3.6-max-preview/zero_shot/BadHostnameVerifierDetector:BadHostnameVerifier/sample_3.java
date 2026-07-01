package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.*;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NonNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.*;

import java.util.Collections;
import java.util.List;

public class BadHostnameVerifierDetector extends Detector implements UastScanner {

    public static final Issue ISSUE = Issue.create(
            "BadHostnameVerifier",
            "Insecure HostnameVerifier",
            "This check looks for implementations of `HostnameVerifier` whose `verify` method " +
            "always returns true (thus trusting any hostname) which could result in insecure " +
            "network traffic caused by trusting arbitrary hostnames in TLS/SSL certificates " +
            "presented by peers.",
            Category.SECURITY,
            8,
            Severity.WARNING,
            new Implementation(BadHostnameVerifierDetector.class, Scope.JAVA_FILE_SCOPE));

    @Nullable
    @Override
    public List<String> getApplicableMethodNames() {
        return Collections.singletonList("verify");
    }

    @Override
    public void visitMethod(@NonNull JavaContext context, @NonNull UMethod method) {
        if (!"verify".equals(method.getName())) {
            return;
        }

        PsiType returnType = method.getReturnType();
        if (returnType == null || !PsiType.BOOLEAN.equals(returnType)) {
            return;
        }

        List<UParameter> parameters = method.getUastParameters();
        if (parameters.size() != 2) {
            return;
        }

        String param1Type = parameters.get(0).getType().getCanonicalText();
        String param2Type = parameters.get(1).getType().getCanonicalText();

        if (!"java.lang.String".equals(param1Type) || !"javax.net.ssl.SSLSession".equals(param2Type)) {
            return;
        }

        UClass containingClass = UastUtils.getContainingUClass(method);
        if (containingClass == null) {
            return;
        }

        boolean implementsHostnameVerifier = false;
        for (PsiType superType : containingClass.getSuperTypes()) {
            if ("javax.net.ssl.HostnameVerifier".equals(superType.getCanonicalText())) {
                implementsHostnameVerifier = true;
                break;
            }
        }

        if (!implementsHostnameVerifier) {
            return;
        }

        UExpression body = method.getUastBody();
        if (body == null) {
            return;
        }

        if (alwaysReturnsTrue(body)) {
            context.report(ISSUE, context.getLocation(method),
                    "Insecure HostnameVerifier: verify() always returns true");
        }
    }

    private boolean alwaysReturnsTrue(@NonNull UExpression body) {
        if (body instanceof UReturnExpression) {
            return isLiteralTrue(((UReturnExpression) body).getReturnExpression());
        }
        if (body instanceof UBlockExpression) {
            List<UExpression> statements = ((UBlockExpression) body).getExpressions();
            int returnCount = 0;
            for (UExpression stmt : statements) {
                if (stmt instanceof UReturnExpression) {
                    returnCount++;
                    if (!isLiteralTrue(((UReturnExpression) stmt).getReturnExpression())) {
                        return false;
                    }
                }
            }
            return returnCount == 1;
        }
        return false;
    }

    private boolean isLiteralTrue(@Nullable UExpression expression) {
        if (expression instanceof ULiteralExpression) {
            ULiteralExpression literal = (ULiteralExpression) expression;
            return Boolean.TRUE.equals(literal.getValue());
        }
        return false;
    }
}