package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.JavaEvaluator;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.UastScanner;

import org.jetbrains.annotations.NonNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.UBlockExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.ULiteralExpression;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UReturnExpression;
import org.jetbrains.uast.util.UastUtils;

import java.util.Collections;
import java.util.List;

public class AllowAllHostnameVerifierDetector extends Detector implements UastScanner {

    public static final Issue ISSUE = Issue.create(
        "AllowAllHostnameVerifier",
        "Insecure HostnameVerifier",
        "This check looks for use of HostnameVerifier implementations whose `verify` method " +
        "always returns true (thus trusting any hostname) which could result in insecure network " +
        "traffic caused by trusting arbitrary hostnames in TLS/SSL certificates presented by peers.",
        Category.SECURITY,
        6,
        Severity.WARNING,
        new Implementation(AllowAllHostnameVerifierDetector.class, Scope.JAVA_FILE_SCOPE)
    );

    @Nullable
    @Override
    public List<String> getApplicableMethodNames() {
        return Collections.singletonList("verify");
    }

    @Override
    public void visitMethod(@NonNull JavaContext context, @NonNull UMethod node) {
        JavaEvaluator evaluator = context.getEvaluator();
        if (!evaluator.isBoolean(node.getReturnType())) {
            return;
        }
        if (node.getParameterList().getParametersCount() != 2) {
            return;
        }

        UClass containingClass = UastUtils.getContainingUClass(node);
        if (containingClass == null) {
            return;
        }

        if (!evaluator.implementsInterface(containingClass, "javax.net.ssl.HostnameVerifier", false)) {
            return;
        }

        UExpression body = node.getUastBody();
        if (alwaysReturnsTrue(body)) {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Insecure `HostnameVerifier` implementation: `verify` always returns `true`"
            );
        }
    }

    private static boolean alwaysReturnsTrue(@Nullable UExpression body) {
        if (body == null) {
            return false;
        }
        if (body instanceof ULiteralExpression) {
            return Boolean.TRUE.equals(((ULiteralExpression) body).getValue());
        }
        if (body instanceof UReturnExpression) {
            UExpression returned = ((UReturnExpression) body).getReturnExpression();
            return returned instanceof ULiteralExpression &&
                   Boolean.TRUE.equals(((ULiteralExpression) returned).getValue());
        }
        if (body instanceof UBlockExpression) {
            List<UExpression> expressions = ((UBlockExpression) body).getExpressions();
            if (expressions.size() == 1) {
                return alwaysReturnsTrue(expressions.get(0));
            }
        }
        return false;
    }
}