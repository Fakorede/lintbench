package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiType;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UReturnExpression;
import org.jetbrains.uast.UThrowExpression;

public class BadHostnameVerifierDetector extends Detector implements SourceCodeScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(BadHostnameVerifierDetector.class, Scope.JAVA_FILE_SCOPE);

    private static final String HOSTNAME_VERIFIER = "javax.net.ssl.HostnameVerifier";

    public static final Issue ISSUE =
            Issue.create(
                            "BadHostnameVerifier",
                            "Insecure HostnameVerifier",
                            "Implementations of `javax.net.ssl.HostnameVerifier` whose `verify` "
                                    + "method always returns true trust any hostname, which can "
                                    + "result in insecure network traffic caused by trusting "
                                    + "arbitrary hostnames in TLS/SSL certificates presented by "
                                    + "peers.",
                            Category.SECURITY,
                            6,
                            Severity.WARNING,
                            IMPLEMENTATION)
                    .setAndroidSpecific(true);

    private static final class VerifyState {
        final UMethod method;
        boolean sawTrueReturn;
        boolean trustAll = true;

        VerifyState(UMethod method) {
            this.method = method;
        }
    }

    private final Deque<VerifyState> mVerifyStack = new ArrayDeque<>();

    public BadHostnameVerifierDetector() {}

    @Nullable
    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList(HOSTNAME_VERIFIER);
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        // Classes are filtered by applicableSuperClasses.
    }

    @Override
    public void visitMethod(@NonNull JavaContext context, @NonNull UMethod node) {
        if (isVerifyMethod(node)) {
            mVerifyStack.push(new VerifyState(node));
        }
    }

    @Override
    public void afterVisitMethod(@NonNull JavaContext context, @NonNull UMethod node) {
        if (!mVerifyStack.isEmpty() && mVerifyStack.peek().method == node) {
            VerifyState state = mVerifyStack.pop();
            if (state.sawTrueReturn && state.trustAll) {
                context.report(
                        ISSUE,
                        node,
                        context.getNameLocation(node),
                        "Implementations of `HostnameVerifier` whose `verify` method always "
                                + "returns true are insecure because they trust any hostname");
            }
        }
    }

    @Override
    public void visitReturnExpression(@NonNull JavaContext context, @NonNull UReturnExpression node) {
        if (mVerifyStack.isEmpty()) {
            return;
        }
        VerifyState state = mVerifyStack.peek();
        UExpression returnValue = node.getReturnExpression();
        Object value = returnValue != null ? context.getEvaluator().evaluate(returnValue) : null;
        if (Boolean.TRUE.equals(value)) {
            state.sawTrueReturn = true;
        } else {
            state.trustAll = false;
        }
    }

    @Override
    public void visitThrowExpression(@NonNull JavaContext context, @NonNull UThrowExpression node) {
        if (!mVerifyStack.isEmpty()) {
            mVerifyStack.peek().trustAll = false;
        }
    }

    @Override
    public void visitCallExpression(@NonNull JavaContext context, @NonNull UCallExpression node) {
        if (!mVerifyStack.isEmpty()) {
            mVerifyStack.peek().trustAll = false;
        }
    }

    private boolean isVerifyMethod(@NonNull PsiMethod method) {
        if (!"verify".equals(method.getName())
                || method.getParameterList().getParametersCount() != 2) {
            return false;
        }
        PsiParameter[] params = method.getParameterList().getParameters();
        PsiType returnType = method.getReturnType();
        return returnType != null
                && returnType.equalsToText("boolean")
                && params[0].getType().equalsToText("java.lang.String")
                && params[1].getType().equalsToText("javax.net.ssl.SSLSession");
    }
}