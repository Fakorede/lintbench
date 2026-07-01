package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.*;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.*;
import org.jetbrains.uast.*;

import java.util.*;

public class AllowAllHostnameVerifierDetector extends Detector implements Detector.UastScanner {
    public static final Issue ISSUE = Issue.create(
        "AllowAllHostnameVerifier",
        "Insecure HostnameVerifier",
        "This check looks for use of HostnameVerifier implementations whose `verify` method " +
        "always returns true (thus trusting any hostname) which could result in insecure " +
        "network traffic caused by trusting arbitrary hostnames in TLS/SSL certificates " +
        "presented by peers.",
        Category.SECURITY,
        6,
        Severity.WARNING,
        new Implementation(
            AllowAllHostnameVerifierDetector.class,
            Scope.JAVA_FILE_SCOPE
        )
    );

    @Nullable
    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.singletonList(UMethod.class);
    }

    @Override
    public void visitMethod(@NonNull JavaContext context, @NonNull UMethod method) {
        if (!"verify".equals(method.getName())) {
            return;
        }

        List<UParameter> parameters = method.getUastParameters();
        if (parameters.size() != 2) {
            return;
        }

        PsiType returnType = method.getReturnType();
        if (returnType == null || !returnType.equalsToText("boolean")) {
            return;
        }

        PsiType type1 = parameters.get(0).getType();
        PsiType type2 = parameters.get(1).getType();
        if (type1 == null || type2 == null) {
            return;
        }

        JavaEvaluator evaluator = context.getEvaluator();
        if (!evaluator.typeMatches(type1, "java.lang.String") ||
            !evaluator.typeMatches(type2, "javax.net.ssl.SSLSession")) {
            return;
        }

        UClass containingClass = UastUtils.getContainingUClass(method);
        if (containingClass == null || !evaluator.implementsInterface(containingClass, "javax.net.ssl.HostnameVerifier", false)) {
            return;
        }

        UExpression body = method.getUastBody();
        if (isReturningTrue(body)) {
            context.report(ISSUE, method, context.getLocation(method),
                "Using a `HostnameVerifier` that always returns `true` disables hostname verification and is insecure.");
        }
    }

    private static boolean isReturningTrue(@Nullable UExpression body) {
        if (body == null) {
            return false;
        }
        UExpression expr = body;
        if (body instanceof UBlockExpression) {
            List<UExpression> statements = ((UBlockExpression) body).getExpressions();
            if (statements.size() == 1) {
                expr = statements.get(0);
            } else {
                return false;
            }
        }
        if (expr instanceof UReturnExpression) {
            UExpression returnExpr = ((UReturnExpression) expr).getReturnExpression();
            if (returnExpr instanceof ULiteralExpression) {
                Object value = ((ULiteralExpression) returnExpr).getValue();
                return Boolean.TRUE.equals(value);
            }
        }
        return false;
    }
}