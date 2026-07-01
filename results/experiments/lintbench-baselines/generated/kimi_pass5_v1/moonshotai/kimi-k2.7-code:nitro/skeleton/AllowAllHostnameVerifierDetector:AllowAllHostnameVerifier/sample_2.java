package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiParameterList;
import com.intellij.psi.PsiType;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.jetbrains.uast.UBlockExpression;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UElementHandler;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.ULambdaExpression;
import org.jetbrains.uast.ULiteralExpression;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UReferenceExpression;
import org.jetbrains.uast.UReturnExpression;

public class AllowAllHostnameVerifierDetector extends Detector implements SourceCodeScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(AllowAllHostnameVerifierDetector.class, Scope.JAVA_FILE_SCOPE);

    private static final String HOSTNAME_VERIFIER = "javax.net.ssl.HostnameVerifier";
    private static final String ALLOW_ALL_HOSTNAME_VERIFIER =
            "org.apache.http.conn.ssl.AllowAllHostnameVerifier";
    private static final String SSL_SOCKET_FACTORY = "org.apache.http.conn.ssl.SSLSocketFactory";
    private static final String ALLOW_ALL_HOSTNAME_VERIFIER_FIELD = "ALLOW_ALL_HOSTNAME_VERIFIER";
    private static final String SET_HOSTNAME_VERIFIER = "setHostnameVerifier";
    private static final String SET_DEFAULT_HOSTNAME_VERIFIER = "setDefaultHostnameVerifier";

    public static final Issue ISSUE =
            Issue.create(
                    "AllowAllHostnameVerifier",
                    "Insecure `HostnameVerifier`",
                    "This check looks for use of `HostnameVerifier` implementations whose `verify` "
                            + "method always returns `true` (thus trusting any hostname) which could "
                            + "result in insecure network traffic caused by trusting arbitrary hostnames "
                            + "in TLS/SSL certificates presented by peers.",
                    Category.SECURITY,
                    6,
                    Severity.WARNING,
                    IMPLEMENTATION);

    @Override
    public List<String> getApplicableConstructorTypes() {
        return Collections.singletonList(ALLOW_ALL_HOSTNAME_VERIFIER);
    }

    @Override
    public void visitConstructor(
            @NonNull JavaContext context,
            @NonNull UCallExpression node,
            @NonNull PsiMethod constructor) {
        context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Using `AllowAllHostnameVerifier` is unsafe");
    }

    @Override
    public List<String> getApplicableMethodNames() {
        return Arrays.asList(SET_HOSTNAME_VERIFIER, SET_DEFAULT_HOSTNAME_VERIFIER);
    }

    @Override
    public void visitMethodCall(
            @NonNull JavaContext context,
            @NonNull UCallExpression node,
            @NonNull PsiMethod method) {
        List<UExpression> arguments = node.getValueArguments();
        if (arguments.size() != 1) {
            return;
        }
        UExpression argument = arguments.get(0);
        if (!(argument instanceof UReferenceExpression)) {
            return;
        }
        PsiElement resolved = ((UReferenceExpression) argument).resolve();
        if (!(resolved instanceof PsiField)) {
            return;
        }
        PsiField field = (PsiField) resolved;
        if (ALLOW_ALL_HOSTNAME_VERIFIER_FIELD.equals(field.getName())
                && SSL_SOCKET_FACTORY.equals(getQualifiedName(field.getContainingClass()))) {
            context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    "Using `ALLOW_ALL_HOSTNAME_VERIFIER` is unsafe");
        }
    }

    @Override
    public List<String> getApplicableSuperClasses() {
        return Collections.singletonList(HOSTNAME_VERIFIER);
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        if (declaration.isInterface()) {
            return;
        }
        for (UMethod method : declaration.getMethods()) {
            if (isVerifyMethod(method) && returnsTrue(method.getUastBody())) {
                context.report(
                        ISSUE,
                        method,
                        context.getLocation(method),
                        "`verify` should not always return true");
                return;
            }
        }
    }

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.singletonList(ULambdaExpression.class);
    }

    @Override
    public UElementHandler createUastHandler(@NonNull JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitLambdaExpression(@NonNull ULambdaExpression node) {
                PsiType functionalType = node.getFunctionalInterfaceType();
                if (functionalType != null
                        && HOSTNAME_VERIFIER.equals(functionalType.getCanonicalText())
                        && returnsTrue(node.getBody())) {
                    context.report(
                            ISSUE,
                            node,
                            context.getLocation(node),
                            "HostnameVerifier that always returns true should not be used");
                }
            }
        };
    }

    private static boolean isVerifyMethod(@NonNull UMethod method) {
        if (!"verify".equals(method.getName())) {
            return false;
        }
        PsiParameterList parameterList = method.getParameterList();
        if (parameterList.getParametersCount() != 2) {
            return false;
        }
        PsiParameter[] parameters = parameterList.getParameters();
        PsiType returnType = method.getReturnType();
        return "java.lang.String".equals(parameters[0].getType().getCanonicalText())
                && "javax.net.ssl.SSLSession".equals(parameters[1].getType().getCanonicalText())
                && returnType != null
                && PsiType.BOOLEAN.equals(returnType);
    }

    private static boolean returnsTrue(UExpression body) {
        if (body == null) {
            return false;
        }
        if (body instanceof UBlockExpression) {
            List<UExpression> expressions = ((UBlockExpression) body).getExpressions();
            if (expressions.size() == 1) {
                return returnsTrue(expressions.get(0));
            }
            boolean seenReturn = false;
            for (UExpression expression : expressions) {
                if (expression instanceof UReturnExpression) {
                    seenReturn = true;
                    UExpression returnValue = ((UReturnExpression) expression).getReturnExpression();
                    if (!isTrueLiteral(returnValue)) {
                        return false;
                    }
                }
            }
            return seenReturn;
        } else if (body instanceof UReturnExpression) {
            return isTrueLiteral(((UReturnExpression) body).getReturnExpression());
        } else {
            return isTrueLiteral(body);
        }
    }

    private static boolean isTrueLiteral(UExpression expression) {
        return expression instanceof ULiteralExpression
                && Boolean.TRUE.equals(((ULiteralExpression) expression).getValue());
    }

    private static String getQualifiedName(PsiClass clazz) {
        return clazz != null ? clazz.getQualifiedName() : null;
    }
}