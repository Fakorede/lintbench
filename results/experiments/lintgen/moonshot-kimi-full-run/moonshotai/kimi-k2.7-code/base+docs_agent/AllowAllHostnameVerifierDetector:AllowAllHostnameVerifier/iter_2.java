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
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiType;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.UBlockExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.ULambdaExpression;
import org.jetbrains.uast.ULiteralExpression;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UParenthesizedExpression;
import org.jetbrains.uast.UReturnExpression;
import org.jetbrains.uast.UTypeReferenceExpression;

public class AllowAllHostnameVerifierDetector extends Detector implements SourceCodeScanner {

    private static final String HOSTNAME_VERIFIER =
            "javax.net.ssl.HostnameVerifier";
    private static final String X509_HOSTNAME_VERIFIER =
            "org.apache.http.conn.ssl.X509HostnameVerifier";

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
                    EnumSet.of(Scope.JAVA_FILE_SCOPE)));

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Arrays.asList(UClass.class, ULambdaExpression.class);
    }

    @Override
    public UElementHandler createUastHandler(@NotNull JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitClass(@NotNull UClass node) {
                if (!implementsHostnameVerifier(node)) {
                    return;
                }

                for (UMethod method : node.getMethods()) {
                    if (isInsecureVerifyMethod(method)) {
                        context.report(
                                ISSUE,
                                method,
                                context.getNameLocation(method),
                                "Insecure HostnameVerifier implementation whose verify() method always returns true");
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
                            "Insecure HostnameVerifier lambda whose verify() always returns true");
                }
            }
        };
    }

    private static boolean implementsHostnameVerifier(@NotNull UClass node) {
        for (UTypeReferenceExpression superType : node.getUastSuperTypes()) {
            String qualifiedName = superType.getQualifiedName();
            if (HOSTNAME_VERIFIER.equals(qualifiedName)
                    || X509_HOSTNAME_VERIFIER.equals(qualifiedName)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isHostnameVerifierLambda(@NotNull ULambdaExpression node) {
        PsiType type = node.getFunctionalInterfaceType();
        if (!(type instanceof PsiClassType)) {
            return false;
        }
        String qualifiedName = ((PsiClassType) type).getCanonicalText();
        return HOSTNAME_VERIFIER.equals(qualifiedName)
                || X509_HOSTNAME_VERIFIER.equals(qualifiedName);
    }

    private static boolean isInsecureVerifyMethod(@NotNull UMethod method) {
        if (!"verify".equals(method.getName())) {
            return false;
        }
        PsiType returnType = method.getReturnType();
        return returnType != null
                && returnType.equals(PsiType.BOOLEAN)
                && bodyAlwaysReturnsTrue(method.getUastBody());
    }

    private static boolean bodyAlwaysReturnsTrue(@Nullable UExpression body) {
        if (body == null) {
            return false;
        }
        if (body instanceof UBlockExpression) {
            List<UExpression> expressions = ((UBlockExpression) body).getExpressions();
            if (expressions.size() != 1) {
                return false;
            }
            return expressionReturnsTrue(expressions.get(0));
        }
        return expressionReturnsTrue(body);
    }

    private static boolean expressionReturnsTrue(@Nullable UExpression expression) {
        if (expression == null) {
            return false;
        }
        if (expression instanceof UParenthesizedExpression) {
            return expressionReturnsTrue(((UParenthesizedExpression) expression).getExpression());
        }
        if (expression instanceof UReturnExpression) {
            return expressionReturnsTrue(((UReturnExpression) expression).getReturnExpression());
        }
        return expression instanceof ULiteralExpression
                && Boolean.TRUE.equals(((ULiteralExpression) expression).getValue());
    }
}