package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.ConstantEvaluator;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypes;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.UIfExpression;
import org.jetbrains.uast.ULambdaExpression;
import org.jetbrains.uast.ULoopExpression;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UParameter;
import org.jetbrains.uast.UReturnExpression;
import org.jetbrains.uast.USwitchExpression;
import org.jetbrains.uast.UThrowExpression;
import org.jetbrains.uast.UTryExpression;
import org.jetbrains.uast.UWhenExpression;
import org.jetbrains.uast.UastUtils;
import org.jetbrains.uast.UCatchClause;

public class BadHostnameVerifierDetector extends Detector implements SourceCodeScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(BadHostnameVerifierDetector.class, Scope.JAVA_FILE_SCOPE);

    private static final String HOSTNAME_VERIFIER = "javax.net.ssl.HostnameVerifier";

    public static final Issue BAD_HOSTNAME_VERIFIER =
            Issue.create(
                            "BadHostnameVerifier",
                            "Insecure HostnameVerifier",
                            "Implementations of `javax.net.ssl.HostnameVerifier` whose `verify` "
                                    + "method always returns true trust any hostname, which can allow "
                                    + "insecure network traffic caused by trusting arbitrary hostnames "
                                    + "in TLS/SSL certificates presented by peers.",
                            Category.SECURITY,
                            6,
                            Severity.WARNING,
                            IMPLEMENTATION)
                    .setAndroidSpecific(false);

    private final Set<UMethod> mReported =
            Collections.newSetFromMap(new IdentityHashMap<UMethod, Boolean>());

    public BadHostnameVerifierDetector() {}

    @Nullable
    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList(HOSTNAME_VERIFIER);
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        mReported.clear();
    }

    @Override
    public void visitReturnExpression(
            @NonNull JavaContext context, @NonNull UReturnExpression node) {
        UMethod method = UastUtils.getParentOfType(node, UMethod.class, true);
        if (method == null || !isVerifyMethod(method)) {
            return;
        }

        UExpression returnExpr = node.getReturnExpression();
        if (returnExpr == null) {
            return;
        }

        Object value = ConstantEvaluator.evaluate(context, returnExpr);
        if (Boolean.TRUE.equals(value) && isUnconditionalReturn(node, method)) {
            if (mReported.add(method)) {
                context.report(
                        BAD_HOSTNAME_VERIFIER,
                        method,
                        context.getNameLocation(method),
                        "`verify` unconditionally returns true, trusting any hostname");
            }
        }
    }

    @Override
    public void visitCallExpression(
            @NonNull JavaContext context, @NonNull UCallExpression node) {
        // Not needed for this check.
    }

    @Override
    public void visitThrowExpression(
            @NonNull JavaContext context, @NonNull UThrowExpression node) {
        // Not needed for this check.
    }

    private static boolean isVerifyMethod(@Nullable UMethod method) {
        if (method == null || !"verify".equals(method.getName())) {
            return false;
        }

        List<UParameter> parameters = method.getUastParameters();
        if (parameters.size() != 2) {
            return false;
        }

        PsiType first = parameters.get(0).getType();
        PsiType second = parameters.get(1).getType();
        if (first == null
                || second == null
                || !first.equalsToText("java.lang.String")
                || !second.equalsToText("javax.net.ssl.SSLSession")) {
            return false;
        }

        PsiType returnType = method.getReturnType();
        return returnType != null && returnType.equals(PsiTypes.booleanType());
    }

    private static boolean isUnconditionalReturn(
            @NonNull UReturnExpression node, @NonNull UMethod method) {
        UElement current = node.getUastParent();
        while (current != null && current != method) {
            if (current instanceof UIfExpression
                    || current instanceof USwitchExpression
                    || current instanceof UWhenExpression
                    || current instanceof UTryExpression
                    || current instanceof UCatchClause
                    || current instanceof ULoopExpression
                    || current instanceof ULambdaExpression) {
                return false;
            }
            current = current.getUastParent();
        }
        return true;
    }
}