package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.JavaEvaluator;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiParameterList;
import com.intellij.psi.PsiType;
import java.util.Arrays;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.UBlockExpression;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UElementHandler;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.ULambdaExpression;
import org.jetbrains.uast.ULiteralExpression;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UParenthesizedExpression;
import org.jetbrains.uast.UReturnExpression;

public class AllowAllHostnameVerifierDetector extends Detector implements Detector.UastScanner {

    private static final String HOSTNAME_VERIFIER = "javax.net.ssl.HostnameVerifier";
    private static final String VERIFY_METHOD = "verify";
    private static final String STRING_TYPE = "java.lang.String";
    private static final String SSL_SESSION_TYPE = "javax.net.ssl.SSLSession";

    public static final Issue ISSUE = Issue.create(
            "AllowAllHostnameVerifier",
            "Insecure HostnameVerifier",
            "This `HostnameVerifier` implementation always returns true, which means it will trust "
                    + "any hostname. This can result in insecure network traffic caused by trusting "
                    + "arbitrary hostnames in TLS/SSL certificates presented by peers.",
            Category.SECURITY,
            6,
            Severity.WARNING,
            new Implementation(AllowAllHostnameVerifierDetector.class, Scope.JAVA_FILE_SCOPE))
            .setMoreInfo("https://goo.gle/AllowAllHostnameVerifier");

    @Override
    @NotNull
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Arrays.asList(UMethod.class, ULambdaExpression.class);
    }

    @Override
    @NotNull
    public UElementHandler createUastHandler(@NotNull JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitMethod(@NotNull UMethod node) {
                if (!isVerifyMethod(node)) {
                    return;
                }

                PsiMethod psiMethod = node.getJavaPsi();
                if (psiMethod == null) {
                    return;
                }

                JavaEvaluator evaluator = context.getEvaluator();
                if (!evaluator.isMemberInSubClassOf(psiMethod, HOSTNAME_VERIFIER, false)) {
                    return;
                }

                UExpression body = node.getUastBody();
                if (body != null && alwaysReturnsTrue(body)) {
                    context.report(ISSUE, context.getLocation(node),
                            "Insecure HostnameVerifier: verify() always returns true");
                }
            }

            @Override
            public void visitLambdaExpression(@NotNull ULambdaExpression node) {
                if (!isHostnameVerifierLambda(node)) {
                    return;
                }

                UExpression body = node.getBody();
                if (body != null && alwaysReturnsTrue(body)) {
                    context.report(ISSUE, context.getLocation(node),
                            "Insecure HostnameVerifier: verify() always returns true");
                }
            }
        };
    }

    private static boolean isVerifyMethod(@NotNull UMethod node) {
        PsiMethod method = node.getJavaPsi();
        if (method == null) {
            return false;
        }

        if (!VERIFY_METHOD.equals(method.getName())) {
            return false;
        }

        PsiParameterList parameterList = method.getParameterList();
        if (parameterList.getParametersCount() != 2) {
            return false;
        }

        PsiParameter[] parameters = parameterList.getParameters();
        if (!isOfBaseType(parameters[0].getType(), STRING_TYPE)
                || !isOfBaseType(parameters[1].getType(), SSL_SESSION_TYPE)) {
            return false;
        }

        PsiType returnType = method.getReturnType();
        if (returnType == null) {
            return false;
        }

        String canonicalText = returnType.getCanonicalText();
        return "boolean".equals(canonicalText) || "java.lang.Boolean".equals(canonicalText);
    }

    private static boolean isHostnameVerifierLambda(@NotNull ULambdaExpression node) {
        PsiType type = node.getFunctionalInterfaceType();
        if (type == null) {
            type = node.getExpressionType();
        }
        return type != null && HOSTNAME_VERIFIER.equals(type.getCanonicalText());
    }

    private static boolean alwaysReturnsTrue(@NotNull UExpression body) {
        if (body instanceof UReturnExpression) {
            return isTrueLiteral(((UReturnExpression) body).getReturnExpression());
        }

        if (body instanceof UBlockExpression) {
            boolean foundReturn = false;
            for (UExpression expression : ((UBlockExpression) body).getExpressions()) {
                if (expression instanceof UReturnExpression) {
                    foundReturn = true;
                    if (!isTrueLiteral(((UReturnExpression) expression).getReturnExpression())) {
                        return false;
                    }
                }
            }
            return foundReturn;
        }

        return isTrueLiteral(body);
    }

    private static boolean isTrueLiteral(@Nullable UExpression expression) {
        if (expression == null) {
            return false;
        }

        if (expression instanceof UParenthesizedExpression) {
            return isTrueLiteral(((UParenthesizedExpression) expression).getExpression());
        }

        if (expression instanceof ULiteralExpression) {
            Object value = ((ULiteralExpression) expression).getValue();
            return Boolean.TRUE.equals(value);
        }

        return false;
    }

    private static boolean isOfBaseType(@NotNull PsiType type, @NotNull String fqcn) {
        return type.getDeepComponentType().getCanonicalText().startsWith(fqcn);
    }
}