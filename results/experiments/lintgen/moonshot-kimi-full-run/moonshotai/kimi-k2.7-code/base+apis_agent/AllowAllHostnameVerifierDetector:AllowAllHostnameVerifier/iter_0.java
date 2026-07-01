package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiType;
import java.util.Arrays;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.uast.UBlockExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UElementHandler;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.ULambdaExpression;
import org.jetbrains.uast.ULiteralExpression;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UParenthesizedExpression;
import org.jetbrains.uast.UParameter;
import org.jetbrains.uast.UReturnExpression;

public class AllowAllHostnameVerifierDetector extends Detector implements SourceCodeScanner {

    private static final String HOSTNAME_VERIFIER = "javax.net.ssl.HostnameVerifier";
    private static final String SSL_SESSION = "javax.net.ssl.SSLSession";
    private static final String MESSAGE =
            "Insecure `HostnameVerifier`: `verify()` always returns true, which trusts any hostname";

    public static final Issue ISSUE = Issue.create(
            "AllowAllHostnameVerifier",
            "Insecure HostnameVerifier",
            "Using a `HostnameVerifier` whose `verify` method always returns true causes the app "
                    + "to trust any hostname presented in TLS/SSL certificates. This can allow "
                    + "attackers to perform man-in-the-middle attacks against secure connections.",
            Category.SECURITY,
            6,
            Severity.WARNING,
            new Implementation(AllowAllHostnameVerifierDetector.class, Scope.JAVA_FILE_SCOPE)
    );

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Arrays.asList(UMethod.class, ULambdaExpression.class);
    }

    @Override
    public UElementHandler createUastHandler(@NotNull JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitMethod(@NotNull UMethod node) {
                if (!isVerifyMethod(node)) {
                    return;
                }
                UClass containingClass = node.getContainingClass();
                if (containingClass == null
                        || !isHostnameVerifierImplementation(containingClass, context)) {
                    return;
                }
                if (alwaysReturnsTrue(node)) {
                    context.report(ISSUE, node, context.getLocation(node), MESSAGE);
                }
            }

            @Override
            public void visitLambdaExpression(@NotNull ULambdaExpression node) {
                PsiType functionalType = node.getFunctionalInterfaceType();
                if (!context.getEvaluator().typeMatches(functionalType, HOSTNAME_VERIFIER)) {
                    return;
                }
                if (alwaysReturnsTrue(node)) {
                    context.report(ISSUE, node, context.getLocation(node), MESSAGE);
                }
            }
        };
    }

    private static boolean isVerifyMethod(UMethod method) {
        if (!"verify".equals(method.getName())) {
            return false;
        }
        PsiType returnType = method.getReturnType();
        if (returnType == null || !PsiType.BOOLEAN.equals(returnType)) {
            return false;
        }
        List<UParameter> params = method.getUastParameters();
        if (params.size() != 2) {
            return false;
        }
        PsiType first = params.get(0).getType();
        PsiType second = params.get(1).getType();
        return first != null && first.equalsToText("java.lang.String")
                && second != null && second.equalsToText(SSL_SESSION);
    }

    private static boolean isHostnameVerifierImplementation(UClass cls, JavaContext context) {
        PsiClass psi = cls.getJavaPsi();
        if (psi == null) {
            return false;
        }
        return context.getEvaluator().implementsInterface(psi, HOSTNAME_VERIFIER, false);
    }

    private static boolean alwaysReturnsTrue(UMethod method) {
        UExpression body = method.getUastBody();
        return body != null && isAlwaysTrueExpression(body);
    }

    private static boolean alwaysReturnsTrue(ULambdaExpression lambda) {
        UExpression body = lambda.getBody();
        return body != null && isAlwaysTrueExpression(body);
    }

    private static boolean isAlwaysTrueExpression(UExpression expression) {
        if (isTrueLiteral(expression)) {
            return true;
        }
        if (expression instanceof UBlockExpression) {
            List<UExpression> expressions = ((UBlockExpression) expression).getExpressions();
            if (expressions.size() == 1) {
                return isReturnTrue(expressions.get(0));
            }
        }
        return false;
    }

    private static boolean isReturnTrue(UExpression expression) {
        if (!(expression instanceof UReturnExpression)) {
            return false;
        }
        UExpression returnValue = ((UReturnExpression) expression).getReturnExpression();
        return isTrueLiteral(returnValue);
    }

    private static boolean isTrueLiteral(UExpression expression) {
        UExpression unwrapped = skipParentheses(expression);
        return unwrapped instanceof ULiteralExpression
                && Boolean.TRUE.equals(((ULiteralExpression) unwrapped).getValue());
    }

    private static UExpression skipParentheses(UExpression expression) {
        UExpression current = expression;
        while (current instanceof UParenthesizedExpression) {
            current = ((UParenthesizedExpression) current).getExpression();
        }
        return current;
    }
}