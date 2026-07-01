package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.*;

import java.util.Collections;
import java.util.List;

public class BadHostnameVerifierDetector extends Detector implements SourceCodeScanner {

    private static final Implementation IMPLEMENTATION = new Implementation(
            BadHostnameVerifierDetector.class,
            Scope.JAVA_FILE_SCOPE
    );

    public static final Issue ISSUE = Issue.create(
            "BadHostnameVerifier",
            "Insecure HostnameVerifier",
            "This check looks for implementations of `HostnameVerifier` whose `verify` method always returns true "
                    + "(thus trusting any hostname) which could result in insecure network traffic caused by trusting "
                    + "arbitrary hostnames in TLS/SSL certificates presented by peers.\n\n"
                    + "Reference: https://goo.gle/BadHostnameVerifier",
            Category.SECURITY,
            6,
            Severity.WARNING,
            IMPLEMENTATION
    );

    @Nullable
    @Override
    public List<String> applicableSuperclasses() {
        return Collections.singletonList("javax.net.ssl.HostnameVerifier");
    }

    @Override
    public void visitClass(@NotNull JavaContext context, @NotNull UClass declaration) {
        if (declaration.isInterface()) {
            return;
        }

        for (UMethod method : declaration.getMethods()) {
            if (!context.getEvaluator().methodMatches(
                    method,
                    "javax.net.ssl.HostnameVerifier",
                    false,
                    "java.lang.String",
                    "javax.net.ssl.SSLSession")) {
                continue;
            }

            if (alwaysReturnsTrue(method)) {
                context.report(
                        ISSUE,
                        method,
                        context.getNameLocation(method),
                        "This `HostnameVerifier` implementation always returns true from `verify`, "
                                + "trustingly accepting any hostname. This is insecure and allows "
                                + "man-in-the-middle attacks."
                );
                break;
            }
        }
    }

    private static boolean alwaysReturnsTrue(@NotNull UMethod method) {
        UExpression body = method.getUastBody();
        if (body == null) {
            return false;
        }

        Boolean directValue = ConstantEvaluator.evaluate(body, Boolean.class);
        if (directValue != null && directValue) {
            return true;
        }

        ReturnVisitor visitor = new ReturnVisitor();
        body.accept(visitor);

        return visitor.hasReturn && !visitor.hasNonTrueReturn;
    }

    private static class ReturnVisitor extends AbstractUastVisitor {
        boolean hasReturn = false;
        boolean hasNonTrueReturn = false;

        @Override
        public boolean visitReturnExpression(@NotNull UReturnExpression node) {
            hasReturn = true;
            UExpression value = node.getReturnExpression();
            if (value != null) {
                Boolean bool = ConstantEvaluator.evaluate(value, Boolean.class);
                if (bool == null || !bool) {
                    hasNonTrueReturn = true;
                }
            } else {
                hasNonTrueReturn = true;
            }
            return super.visitReturnExpression(node);
        }
    }
}