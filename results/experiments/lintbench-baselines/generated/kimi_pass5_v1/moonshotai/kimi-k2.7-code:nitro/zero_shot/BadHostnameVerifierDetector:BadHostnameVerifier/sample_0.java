package com.android.tools.lint.checks;

import com.android.tools.lint.client.api.UElementHandler;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.ConstantEvaluator;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiParameterList;
import com.intellij.psi.PsiType;
import java.util.Collections;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.UBlockExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.ULambdaExpression;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UReturnExpression;
import org.jetbrains.uast.visitor.AbstractUastVisitor;

public class BadHostnameVerifierDetector extends Detector implements SourceCodeScanner {

    private static final String HOSTNAME_VERIFIER = "javax.net.ssl.HostnameVerifier";
    private static final String SSL_SESSION = "javax.net.ssl.SSLSession";

    public static final Issue ISSUE = Issue.create(
            "BadHostnameVerifier",
            "Insecure HostnameVerifier",
            "This check looks for implementations of `HostnameVerifier` whose `verify` method " +
            "always returns true (thus trusting any hostname) which could result in insecure " +
            "network traffic caused by trusting arbitrary hostnames in TLS/SSL certificates " +
            "presented by peers.\n" +
            "See https://goo.gle/BadHostnameVerifier",
            Category.SECURITY,
            6,
            Severity.WARNING,
            new Implementation(BadHostnameVerifierDetector.class, Scope.JAVA_FILE_SCOPE)
    );

    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList(HOSTNAME_VERIFIER);
    }

    @Override
    public void visitClass(@NotNull JavaContext context, @NotNull UClass declaration) {
        for (UMethod method : declaration.getMethods()) {
            if (isVerifyMethod(method) && isAlwaysTrue(method.getUastBody())) {
                context.report(
                        ISSUE,
                        method,
                        context.getNameLocation(method),
                        "Using a `HostnameVerifier` whose `verify()` method always returns true is insecure"
                );
            }
        }
    }

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.singletonList(ULambdaExpression.class);
    }

    @Override
    public UElementHandler createUastHandler(@NotNull JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitLambdaExpression(@NotNull ULambdaExpression node) {
                if (isHostnameVerifierLambda(node) && isAlwaysTrue(node.getBody())) {
                    context.report(
                            ISSUE,
                            node,
                            context.getLocation(node),
                            "Using a `HostnameVerifier` whose `verify()` method always returns true is insecure"
                    );
                }
            }
        };
    }

    private static boolean isVerifyMethod(@NotNull UMethod method) {
        if (!"verify".equals(method.getName())) {
            return false;
        }

        PsiMethod psiMethod = method.getJavaPsi();
        if (psiMethod == null) {
            return false;
        }

        PsiParameterList parameterList = psiMethod.getParameterList();
        if (parameterList.getParametersCount() != 2) {
            return false;
        }

        PsiParameter[] parameters = parameterList.getParameters();
        if (!"java.lang.String".equals(parameters[0].getType().getCanonicalText())) {
            return false;
        }
        if (!SSL_SESSION.equals(parameters[1].getType().getCanonicalText())) {
            return false;
        }

        PsiType returnType = psiMethod.getReturnType();
        return returnType != null && PsiType.BOOLEAN.equals(returnType);
    }

    private static boolean isHostnameVerifierLambda(@NotNull ULambdaExpression lambda) {
        PsiType type = lambda.getFunctionalInterfaceType();
        return type != null && HOSTNAME_VERIFIER.equals(type.getCanonicalText());
    }

    private static boolean isAlwaysTrue(@Nullable UExpression expression) {
        if (expression == null) {
            return false;
        }

        if (expression instanceof UReturnExpression) {
            return isTrue(((UReturnExpression) expression).getReturnExpression());
        }

        if (expression instanceof UBlockExpression) {
            final boolean[] foundReturn = {false};
            final boolean[] allTrue = {true};

            expression.accept(new AbstractUastVisitor() {
                @Override
                public boolean visitReturnExpression(@NotNull UReturnExpression node) {
                    foundReturn[0] = true;
                    if (!isTrue(node.getReturnExpression())) {
                        allTrue[0] = false;
                    }
                    return super.visitReturnExpression(node);
                }
            });

            return foundReturn[0] && allTrue[0];
        }

        return isTrue(expression);
    }

    private static boolean isTrue(@Nullable UExpression expression) {
        return Boolean.TRUE.equals(ConstantEvaluator.evaluate(expression));
    }
}