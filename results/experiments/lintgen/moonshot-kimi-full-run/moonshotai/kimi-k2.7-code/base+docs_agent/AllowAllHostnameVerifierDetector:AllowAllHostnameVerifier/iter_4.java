package com.android.tools.lint.checks;

import com.android.tools.lint.client.api.UElementHandler;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import java.util.Arrays;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.UBlockExpression;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.ULambdaExpression;
import org.jetbrains.uast.ULiteralExpression;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UParenthesizedExpression;
import org.jetbrains.uast.UReturnExpression;
import org.jetbrains.uast.UastCallKind;

public class AllowAllHostnameVerifierDetector extends Detector implements SourceCodeScanner {

    private static final String HOSTNAME_VERIFIER = "javax.net.ssl.HostnameVerifier";
    private static final String X509_HOSTNAME_VERIFIER = "org.apache.http.conn.ssl.X509HostnameVerifier";
    private static final String ALLOW_ALL_HOSTNAME_VERIFIER =
            "org.apache.http.conn.ssl.AllowAllHostnameVerifier";

    public static final Issue ISSUE = Issue.create(
            "AllowAllHostnameVerifier",
            "Insecure `HostnameVerifier`",
            "This check looks for use of HostnameVerifier implementations whose `verify` method "
                    + "always returns true (thus trusting any hostname), which could result in "
                    + "insecure network traffic caused by trusting arbitrary hostnames in TLS/SSL "
                    + "certificates presented by peers.",
            Category.SECURITY,
            6,
            Severity.WARNING,
            new Implementation(
                    AllowAllHostnameVerifierDetector.class,
                    Scope.JAVA_FILE_SCOPE));

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Arrays.asList(UClass.class, ULambdaExpression.class, UCallExpression.class);
    }

    @Override
    public UElementHandler createUastHandler(@NotNull JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitClass(@NotNull UClass node) {
                if (!isHostnameVerifierImplementation(context, node)) {
                    return;
                }
                for (UMethod method : node.getMethods()) {
                    if (isInsecureVerifyMethod(method)) {
                        context.report(
                                ISSUE,
                                method,
                                context.getNameLocation(method),
                                "Insecure `HostnameVerifier` implementation: `verify` always returns `true`");
                    }
                }
            }

            @Override
            public void visitLambdaExpression(@NotNull ULambdaExpression node) {
                if (!isHostnameVerifierLambda(node)) {
                    return;
                }
                UExpression body = node.getBody();
                if (bodyAlwaysReturnsTrue(body)) {
                    context.report(
                            ISSUE,
                            node,
                            context.getLocation(node),
                            "Insecure `HostnameVerifier` lambda: `verify` always returns `true`");
                }
            }

            @Override
            public void visitCallExpression(@NotNull UCallExpression node) {
                if (node.getKind() != UastCallKind.CONSTRUCTOR_CALL) {
                    return;
                }
                PsiMethod method = node.resolve();
                if (method == null || !method.isConstructor()) {
                    return;
                }
                PsiClass containingClass = method.getContainingClass();
                if (containingClass == null) {
                    return;
                }
                if (ALLOW_ALL_HOSTNAME_VERIFIER.equals(containingClass.getQualifiedName())) {
                    context.report(
                            ISSUE,
                            node,
                            context.getLocation(node),
                            "Using `AllowAllHostnameVerifier` is insecure because it trusts any hostname");
                }
            }
        };
    }

    private static boolean isHostnameVerifierImplementation(
            @NotNull JavaContext context, @NotNull UClass node) {
        return context.getEvaluator().implementsInterface(node, HOSTNAME_VERIFIER, false)
                || context.getEvaluator().implementsInterface(node, X509_HOSTNAME_VERIFIER, false);
    }

    private static boolean isHostnameVerifierLambda(@NotNull ULambdaExpression node) {
        PsiType type = node.getFunctionalInterfaceType();
        if (!(type instanceof PsiClassType)) {
            return false;
        }
        PsiClass psiClass = ((PsiClassType) type).resolve();
        if (psiClass == null) {
            return false;
        }
        String qualifiedName = psiClass.getQualifiedName();
        return HOSTNAME_VERIFIER.equals(qualifiedName)
                || X509_HOSTNAME_VERIFIER.equals(qualifiedName);
    }

    private static boolean isInsecureVerifyMethod(@NotNull UMethod method) {
        if (!"verify".equals(method.getName())) {
            return false;
        }
        PsiType returnType = method.getReturnType();
        if (returnType != PsiType.BOOLEAN) {
            return false;
        }
        return bodyAlwaysReturnsTrue(method.getUastBody());
    }

    private static boolean bodyAlwaysReturnsTrue(@Nullable UExpression body) {
        if (body == null) {
            return false;
        }
        body = skipParentheses(body);
        if (body instanceof UBlockExpression) {
            List<UExpression> expressions = ((UBlockExpression) body).getExpressions();
            if (expressions.isEmpty()) {
                return false;
            }
            for (UExpression expression : expressions) {
                if (expression instanceof UReturnExpression) {
                    if (!isTrueLiteral(((UReturnExpression) expression).getReturnExpression())) {
                        return false;
                    }
                }
            }
            return isTrueLiteral(expressions.get(expressions.size() - 1));
        }
        if (body instanceof UReturnExpression) {
            return isTrueLiteral(((UReturnExpression) body).getReturnExpression());
        }
        return isTrueLiteral(body);
    }

    private static boolean isTrueLiteral(@Nullable UExpression expression) {
        expression = skipParentheses(expression);
        if (expression instanceof UReturnExpression) {
            return isTrueLiteral(((UReturnExpression) expression).getReturnExpression());
        }
        return expression instanceof ULiteralExpression
                && Boolean.TRUE.equals(((ULiteralExpression) expression).getValue());
    }

    @NotNull
    private static UExpression skipParentheses(@NotNull UExpression expression) {
        UExpression current = expression;
        while (current instanceof UParenthesizedExpression) {
            UExpression inner = ((UParenthesizedExpression) current).getExpression();
            if (inner == null) {
                break;
            }
            current = inner;
        }
        return current;
    }
}