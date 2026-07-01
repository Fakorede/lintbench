package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.*;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.uast.*;

import java.util.Collections;
import java.util.List;

public class BadHostnameVerifierDetector extends Detector implements SourceCodeScanner {

    private static final String HOSTNAME_VERIFIER = "javax.net.ssl.HostnameVerifier";

    public static final Issue ISSUE = Issue.create(
            "BadHostnameVerifier",
            "Insecure HostnameVerifier",
            "This HostnameVerifier implementation unconditionally returns true from its verify(...) method. "
                    + "That causes the app to trust any hostname presented in TLS/SSL certificates, "
                    + "which makes it vulnerable to man-in-the-middle attacks.",
            Category.SECURITY,
            6,
            Severity.WARNING,
            new Implementation(BadHostnameVerifierDetector.class, Scope.JAVA_FILE_SCOPE)
    );

    @Override
    @NotNull
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.singletonList(UMethod.class);
    }

    @Override
    @NotNull
    public UElementHandler createUastHandler(@NotNull final JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitMethod(@NotNull UMethod method) {
                if (!"verify".equals(method.getName())) {
                    return;
                }

                PsiMethod psiMethod = method.getJavaPsi();
                if (psiMethod == null) {
                    return;
                }

                if (!PsiType.BOOLEAN.equals(psiMethod.getReturnType())) {
                    return;
                }

                PsiParameter[] parameters = psiMethod.getParameterList().getParameters();
                if (parameters.length != 2
                        || !parameters[0].getType().equalsToText("java.lang.String")
                        || !parameters[1].getType().equalsToText("javax.net.ssl.SSLSession")) {
                    return;
                }

                PsiClass containingClass = psiMethod.getContainingClass();
                if (containingClass == null
                        || !context.getEvaluator().implementsInterface(containingClass, HOSTNAME_VERIFIER, false)) {
                    return;
                }

                UExpression body = method.getUastBody();
                if (body == null) {
                    return;
                }

                if (alwaysReturnsTrue(body)) {
                    context.report(
                            ISSUE,
                            method,
                            context.getLocation(method),
                            "Insecure HostnameVerifier: verify() unconditionally returns true"
                    );
                }
            }
        };
    }

    private static boolean alwaysReturnsTrue(@NotNull UExpression expression) {
        if (expression instanceof UReturnExpression) {
            UExpression returnExpression = ((UReturnExpression) expression).getReturnExpression();
            return isTrueLiteral(returnExpression);
        }

        if (expression instanceof UBlockExpression) {
            List<UExpression> expressions = ((UBlockExpression) expression).getExpressions();
            if (expressions.size() != 1) {
                return false;
            }
            return alwaysReturnsTrue(expressions.get(0));
        }

        return false;
    }

    private static boolean isTrueLiteral(UExpression expression) {
        return expression instanceof ULiteralExpression
                && Boolean.TRUE.equals(((ULiteralExpression) expression).getValue());
    }
}