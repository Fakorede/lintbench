package com.android.tools.lint.checks;

import static org.jetbrains.uast.UastExpressionUtils.isConstructorCall;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.UElementHandler;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.PsiUtil;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.uast.UBlockExpression;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.ULambdaExpression;
import org.jetbrains.uast.ULiteralExpression;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UReturnExpression;
import org.jetbrains.uast.UastExpressionUtils;

public class AllowAllHostnameVerifierDetector extends Detector implements SourceCodeScanner {

    public static final Issue ISSUE = Issue.create(
            "AllowAllHostnameVerifier",
            "Insecure HostnameVerifier",
            "This check looks for `HostnameVerifier` implementations whose `verify` method "
                    + "always returns true (thus trusting any hostname), which could result in "
                    + "insecure network traffic caused by trusting arbitrary hostnames in "
                    + "TLS/SSL certificates presented by peers.\n\n"
                    + "Reference: https://goo.gle/AllowAllHostnameVerifier",
            Category.SECURITY,
            6,
            Severity.WARNING,
            new Implementation(AllowAllHostnameVerifierDetector.class, Scope.JAVA_FILE_SCOPE));

    private static final String HOSTNAME_VERIFIER = "javax.net.ssl.HostnameVerifier";
    private static final String ALLOW_ALL_HOSTNAME_VERIFIER =
            "org.apache.http.conn.ssl.AllowAllHostnameVerifier";

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Arrays.asList(UClass.class, ULambdaExpression.class, UCallExpression.class);
    }

    @Override
    public UElementHandler createUastHandler(@NotNull JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitClass(@NotNull UClass node) {
                if (!context.getEvaluator().implementsInterface(node, HOSTNAME_VERIFIER, false)) {
                    return;
                }
                for (UMethod method : node.getMethods()) {
                    if (isVerifyMethod(method) && alwaysReturnsTrue(method)) {
                        context.report(
                                ISSUE,
                                method,
                                context.getLocation(method),
                                "Using a `HostnameVerifier` that unconditionally returns true is insecure");
                        return;
                    }
                }
            }

            @Override
            public void visitLambdaExpression(@NotNull ULambdaExpression node) {
                if (!isHostnameVerifierLambda(context, node)) {
                    return;
                }
                if (lambdaAlwaysReturnsTrue(node)) {
                    context.report(
                            ISSUE,
                            node,
                            context.getLocation(node),
                            "Using a `HostnameVerifier` that unconditionally returns true is insecure");
                }
            }

            @Override
            public void visitCallExpression(@NotNull UCallExpression node) {
                if (!UastExpressionUtils.isConstructorCall(node)) {
                    return;
                }
                PsiMethod constructor = node.resolve();
                if (constructor == null) {
                    return;
                }
                PsiClass containingClass = constructor.getContainingClass();
                if (containingClass == null) {
                    return;
                }
                String qualifiedName = containingClass.getQualifiedName();
                if (ALLOW_ALL_HOSTNAME_VERIFIER.equals(qualifiedName)) {
                    context.report(
                            ISSUE,
                            node,
                            context.getLocation(node),
                            "Using `AllowAllHostnameVerifier` is insecure");
                }
            }
        };
    }

    private static boolean isVerifyMethod(UMethod method) {
        if (!"verify".equals(method.getName())) {
            return false;
        }
        if (method.getParameterList().getParametersCount() != 2) {
            return false;
        }
        PsiType returnType = method.getReturnType();
        return returnType != null && returnType.equalsToText("boolean");
    }

    private static boolean alwaysReturnsTrue(UMethod method) {
        UExpression body = method.getUastBody();
        if (body == null) {
            return false;
        }
        List<UReturnExpression> returns = new ArrayList<>();
        collectReturns(body, returns);
        if (returns.isEmpty()) {
            return false;
        }
        for (UReturnExpression ret : returns) {
            if (!isTrueLiteral(ret.getReturnExpression())) {
                return false;
            }
        }
        return true;
    }

    private static boolean lambdaAlwaysReturnsTrue(ULambdaExpression lambda) {
        UExpression body = lambda.getBody();
        if (body == null) {
            return false;
        }
        if (!(body instanceof UBlockExpression)) {
            return isTrueLiteral(body);
        }
        List<UReturnExpression> returns = new ArrayList<>();
        collectReturns(body, returns);
        if (returns.isEmpty()) {
            return false;
        }
        for (UReturnExpression ret : returns) {
            if (!isTrueLiteral(ret.getReturnExpression())) {
                return false;
            }
        }
        return true;
    }

    private static boolean isHostnameVerifierLambda(JavaContext context, ULambdaExpression lambda) {
        PsiType type = lambda.getFunctionalInterfaceType();
        if (type == null) {
            return false;
        }
        PsiClass psiClass = PsiUtil.resolveClassInClassTypeOnly(type);
        if (psiClass == null) {
            return false;
        }
        return context.getEvaluator().implementsInterface(psiClass, HOSTNAME_VERIFIER, false);
    }

    private static void collectReturns(UElement element, List<UReturnExpression> out) {
        if (element == null) {
            return;
        }
        if (element instanceof UReturnExpression) {
            out.add((UReturnExpression) element);
            return;
        }
        if (element instanceof UClass || element instanceof ULambdaExpression) {
            return;
        }
        for (UElement child : element.getChildren()) {
            collectReturns(child, out);
        }
    }

    private static boolean isTrueLiteral(UExpression expression) {
        return expression instanceof ULiteralExpression
                && Boolean.TRUE.equals(((ULiteralExpression) expression).getValue());
    }
}