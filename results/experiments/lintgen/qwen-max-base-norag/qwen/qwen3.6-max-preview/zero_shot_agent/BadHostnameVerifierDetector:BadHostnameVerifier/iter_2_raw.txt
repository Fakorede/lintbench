package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.UElementHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.UBlockExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.ULiteralExpression;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UReturnExpression;
import org.jetbrains.uast.UastUtils;

import java.util.Collections;
import java.util.List;

public class BadHostnameVerifierDetector extends Detector implements Detector.UastScanner {

    public static final Issue ISSUE = Issue.create(
        "BadHostnameVerifier",
        "Insecure HostnameVerifier",
        "This check looks for implementations of `HostnameVerifier` whose `verify` method " +
        "always returns true (thus trusting any hostname) which could result in insecure " +
        "network traffic caused by trusting arbitrary hostnames in TLS/SSL certificates " +
        "presented by peers.",
        Category.SECURITY,
        6,
        Severity.WARNING,
        new Implementation(BadHostnameVerifierDetector.class, Scope.JAVA_FILE_SCOPE)
    );

    @Nullable
    @Override
    public List<Class<? extends org.jetbrains.uast.UElement>> getApplicableUastTypes() {
        return Collections.singletonList(UMethod.class);
    }

    @Nullable
    @Override
    public UElementHandler createUastHandler(@NotNull JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitMethod(@NotNull UMethod node) {
                if (!"verify".equals(node.getName())) {
                    return;
                }

                if (node.getUastParameters().size() != 2) {
                    return;
                }

                UClass uClass = UastUtils.getContainingUClass(node);
                if (uClass == null) {
                    return;
                }

                if (!context.getEvaluator().implementsInterface(uClass, "javax.net.ssl.HostnameVerifier", true)) {
                    return;
                }

                UExpression body = node.getUastBody();
                if (body != null && isReturningTrue(body)) {
                    context.report(ISSUE, node, context.getLocation(node),
                        "Insecure `HostnameVerifier` implementation: `verify()` always returns `true`");
                }
            }
        };
    }

    private static boolean isReturningTrue(@NotNull UExpression body) {
        if (body instanceof UBlockExpression) {
            List<UExpression> expressions = ((UBlockExpression) body).getExpressions();
            if (expressions.size() == 1 && expressions.get(0) instanceof UReturnExpression) {
                UExpression returnExpr = ((UReturnExpression) expressions.get(0)).getReturnExpression();
                return isTrueLiteral(returnExpr);
            }
        } else if (body instanceof UReturnExpression) {
            return isTrueLiteral(((UReturnExpression) body).getReturnExpression());
        } else if (body instanceof ULiteralExpression) {
            return isTrueLiteral(body);
        }
        return false;
    }

    private static boolean isTrueLiteral(@Nullable UExpression expr) {
        if (expr instanceof ULiteralExpression) {
            Object value = ((ULiteralExpression) expr).getValue();
            return Boolean.TRUE.equals(value);
        }
        return false;
    }
}