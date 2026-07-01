package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.ConstantEvaluator;
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
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.ULambdaExpression;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UReturnExpression;
import org.jetbrains.uast.UThrowExpression;

public class BadHostnameVerifierDetector extends Detector implements SourceCodeScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(BadHostnameVerifierDetector.class, Scope.JAVA_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "BadHostnameVerifier",
                    "Insecure HostnameVerifier",
                    "A HostnameVerifier whose verify() method always returns true will accept any "
                            + "hostname in a TLS/SSL certificate. This allows an attacker to "
                            + "perform man-in-the-middle attacks against the connection.",
                    Category.SECURITY,
                    6,
                    Severity.WARNING,
                    IMPLEMENTATION);

    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList("javax.net.ssl.HostnameVerifier");
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        for (UMethod method : declaration.getMethods()) {
            if (!isVerifyMethod(method)) {
                continue;
            }

            UExpression body = method.getUastBody();
            if (body == null) {
                continue;
            }

            State state = new State();
            traverse(body, context, state);

            if (!state.hasReturn
                    && !state.hasCall
                    && !state.hasThrow
                    && ConstantEvaluator.evaluate(context, body) == Boolean.TRUE) {
                state.hasReturn = true;
            }

            if (state.hasReturn
                    && !state.hasNonTrueReturn
                    && !state.hasCall
                    && !state.hasThrow) {
                context.report(
                        ISSUE,
                        method,
                        context.getLocation(method),
                        "verify() always returns true, trusting every hostname; this is insecure");
            }
        }
    }

    private boolean isVerifyMethod(@NonNull UMethod method) {
        if (!"verify".equals(method.getName())) {
            return false;
        }

        PsiParameterList parameterList = method.getParameterList();
        if (parameterList.getParametersCount() != 2) {
            return false;
        }

        PsiParameter[] parameters = parameterList.getParameters();
        PsiType first = parameters[0].getType();
        PsiType second = parameters[1].getType();
        if (first == null
                || second == null
                || !first.equalsToText("java.lang.String")
                || !second.equalsToText("javax.net.ssl.SSLSession")) {
            return false;
        }

        PsiType returnType = method.getReturnType();
        return returnType != null && returnType.equals(PsiType.BOOLEAN);
    }

    private void traverse(
            @NonNull UElement element,
            @NonNull JavaContext context,
            @NonNull State state) {
        if (element instanceof UMethod
                || element instanceof UClass
                || element instanceof ULambdaExpression) {
            return;
        }

        if (element instanceof UReturnExpression) {
            visitReturnExpression((UReturnExpression) element, context, state);
        } else if (element instanceof UThrowExpression) {
            visitThrowExpression((UThrowExpression) element, state);
        } else if (element instanceof UCallExpression) {
            visitCallExpression((UCallExpression) element, state);
        }

        for (UElement child : element.getUastContents()) {
            traverse(child, context, state);
        }
    }

    public void visitThrowExpression(
            @NonNull UThrowExpression expression, @NonNull State state) {
        state.hasThrow = true;
    }

    public void visitCallExpression(
            @NonNull UCallExpression expression, @NonNull State state) {
        state.hasCall = true;
    }

    public void visitReturnExpression(
            @NonNull UReturnExpression expression,
            @NonNull JavaContext context,
            @NonNull State state) {
        state.hasReturn = true;
        Object value = ConstantEvaluator.evaluate(context, expression.getReturnExpression());
        if (value != Boolean.TRUE) {
            state.hasNonTrueReturn = true;
        }
    }

    private static class State {
        boolean hasReturn;
        boolean hasNonTrueReturn;
        boolean hasCall;
        boolean hasThrow;
    }
}