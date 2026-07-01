package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import java.util.Collections;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.ULambdaExpression;
import org.jetbrains.uast.ULiteralExpression;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UReturnExpression;
import org.jetbrains.uast.visitor.AbstractUastVisitor;

public class BadHostnameVerifierDetector extends Detector implements Detector.UastScanner {

    public static final Issue ISSUE = Issue.create(
            "BadHostnameVerifier",
            "Insecure HostnameVerifier",
            "This check looks for implementations of `HostnameVerifier` whose " +
            "`verify` method always returns true (thus trusting any hostname) " +
            "which could result in insecure network traffic caused by trusting " +
            "arbitrary hostnames in TLS/SSL certificates presented by peers.",
            Category.SECURITY,
            6,
            Severity.WARNING,
            new Implementation(
                    BadHostnameVerifierDetector.class,
                    Scope.JAVA_FILE_SCOPE
            )
    );

    @Nullable
    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList("javax.net.ssl.HostnameVerifier");
    }

    @Override
    public void visitClass(@NotNull JavaContext context, @NotNull UClass declaration) {
        UMethod verifyMethod = null;
        for (UMethod method : declaration.getMethods()) {
            if ("verify".equals(method.getName())) {
                verifyMethod = method;
                break;
            }
        }

        if (verifyMethod == null) {
            return;
        }

        UExpression body = verifyMethod.getUastBody();
        if (body == null) {
            return;
        }

        ReturnVisitor visitor = new ReturnVisitor();
        body.accept(visitor);

        if (visitor.hasReturns() && visitor.allReturnsTrue()) {
            context.report(
                    ISSUE,
                    declaration,
                    context.getNameLocation(verifyMethod),
                    "Trusting all hostnames can lead to insecure network traffic"
            );
        }
    }

    private static class ReturnVisitor extends AbstractUastVisitor {
        private boolean hasReturns = false;
        private boolean allReturnsTrue = true;

        public boolean hasReturns() {
            return hasReturns;
        }

        public boolean allReturnsTrue() {
            return allReturnsTrue;
        }

        @Override
        public boolean visitReturnExpression(@NotNull UReturnExpression node) {
            hasReturns = true;
            UExpression returnExpression = node.getReturnExpression();
            if (returnExpression == null) {
                allReturnsTrue = false;
            } else {
                Object value = returnExpression.evaluate();
                if (!Boolean.TRUE.equals(value)) {
                    if (returnExpression instanceof ULiteralExpression) {
                        Object literalValue = ((ULiteralExpression) returnExpression).getValue();
                        if (!Boolean.TRUE.equals(literalValue)) {
                            allReturnsTrue = false;
                        }
                    } else {
                        allReturnsTrue = false;
                    }
                }
            }
            return super.visitReturnExpression(node);
        }

        @Override
        public boolean visitLambdaExpression(@NotNull ULambdaExpression node) {
            return true;
        }

        @Override
        public boolean visitClass(@NotNull UClass node) {
            return true;
        }
    }
}