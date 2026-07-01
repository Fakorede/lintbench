package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiParameterList;
import com.intellij.psi.PsiType;
import java.util.Collections;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.UBlockExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.ULambdaExpression;
import org.jetbrains.uast.ULiteralExpression;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UParenthesizedExpression;
import org.jetbrains.uast.UReturnExpression;

public class AllowAllHostnameVerifierDetector extends Detector implements SourceCodeScanner {

    private static final String HOSTNAME_VERIFIER = "javax.net.ssl.HostnameVerifier";
    private static final String SSL_SESSION = "javax.net.ssl.SSLSession";
    private static final String MESSAGE =
            "Insecure `HostnameVerifier`: `verify()` always returns true, which trusts any hostname";

    public static final Issue ISSUE =
            Issue.create(
                    "AllowAllHostnameVerifier",
                    "Insecure HostnameVerifier",
                    "Using a `HostnameVerifier` whose `verify()` method always returns true causes the app "
                            + "to trust any hostname presented in TLS/SSL certificates. This can allow "
                            + "attackers to perform man-in-the-middle attacks against secure connections.",
                    Category.SECURITY,
                    6,
                    Severity.WARNING,
                    new Implementation(AllowAllHostnameVerifierDetector.class, Scope.JAVA_FILE_SCOPE));

    @Nullable
    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList(HOSTNAME_VERIFIER);
    }

    @Override
    public void visitClass(@NotNull JavaContext context, @NotNull UClass declaration) {
        for (UMethod method : declaration.getMethods()) {
            if (isVerifyMethodSignature(method) && alwaysReturnsTrue(method)) {
                context.report(ISSUE, method, context.getLocation(method), MESSAGE);
            }
        }
    }

    @Override
    public void visitClass(@NotNull JavaContext context, @NotNull ULambdaExpression lambda) {
        UExpression body = lambda.getBody();
        if (body != null && alwaysReturnsTrue(body)) {
            context.report(ISSUE, lambda, context.getLocation(lambda), MESSAGE);
        }
    }

    private static boolean isVerifyMethodSignature(@NotNull UMethod method) {
        if (!"verify".equals(method.getName())) {
            return false;
        }
        PsiType returnType = method.getReturnType();
        if (returnType == null || !PsiType.BOOLEAN.equals(returnType)) {
            return false;
        }
        PsiParameterList parameterList = method.getParameterList();
        if (parameterList.getParametersCount() != 2) {
            return false;
        }
        PsiParameter[] parameters = parameterList.getParameters();
        PsiType first = parameters[0].getType();
        PsiType second = parameters[1].getType();
        return first != null
                && first.equalsToText("java.lang.String")
                && second != null
                && second.equalsToText(SSL_SESSION);
    }

    private static boolean alwaysReturnsTrue(@NotNull UMethod method) {
        UExpression body = method.getUastBody();
        return body != null && alwaysReturnsTrue(body);
    }

    private static boolean alwaysReturnsTrue(@Nullable UExpression expression) {
        if (expression == null) {
            return false;
        }
        if (isTrueLiteral(expression)) {
            return true;
        }
        if (expression instanceof UBlockExpression) {
            List<UExpression> expressions = ((UBlockExpression) expression).getExpressions();
            if (expressions.size() == 1) {
                return isReturnTrue(expressions.get(0));
            }
            boolean sawTrueReturn = false;
            for (UExpression expr : expressions) {
                if (expr instanceof UReturnExpression) {
                    UExpression returnValue = ((UReturnExpression) expr).getReturnExpression();
                    if (!isTrueLiteral(returnValue)) {
                        return false;
                    }
                    sawTrueReturn = true;
                }
            }
            return sawTrueReturn;
        }
        return false;
    }

    private static boolean isReturnTrue(@Nullable UExpression expression) {
        if (!(expression instanceof UReturnExpression)) {
            return false;
        }
        UExpression returnValue = ((UReturnExpression) expression).getReturnExpression();
        return isTrueLiteral(returnValue);
    }

    private static boolean isTrueLiteral(@Nullable UExpression expression) {
        UExpression unwrapped = skipParentheses(expression);
        return unwrapped instanceof ULiteralExpression
                && Boolean.TRUE.equals(((ULiteralExpression) unwrapped).getValue());
    }

    @Nullable
    private static UExpression skipParentheses(@Nullable UExpression expression) {
        UExpression current = expression;
        while (current instanceof UParenthesizedExpression) {
            current = ((UParenthesizedExpression) current).getExpression();
        }
        return current;
    }
}