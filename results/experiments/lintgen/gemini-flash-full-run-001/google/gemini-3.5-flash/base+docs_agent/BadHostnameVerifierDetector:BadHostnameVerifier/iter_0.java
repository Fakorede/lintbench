package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.ConstantEvaluator;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
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
import org.jetbrains.uast.UastUtils;
import org.jetbrains.uast.visitor.AbstractUastVisitor;

public class BadHostnameVerifierDetector extends Detector implements SourceCodeScanner {

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
        new Implementation(
            BadHostnameVerifierDetector.class,
            Scope.JAVA_FILE_SCOPE
        )
    ).setMoreInfo("https://goo.gle/BadHostnameVerifier");

    @Override
    @Nullable
    public List<String> applicableSuperClasses() {
        return Collections.singletonList("javax.net.ssl.HostnameVerifier");
    }

    @Override
    public void visitClass(@NotNull JavaContext context, @NotNull UClass declaration) {
        if (declaration.isInterface()) {
            return;
        }

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

        ReturnVisitor visitor = new ReturnVisitor(context);
        body.accept(visitor);

        boolean isBad = false;
        if (visitor.hasReturns && visitor.allReturnsTrue) {
            isBad = true;
        } else if (!visitor.hasReturns) {
            Object constant = ConstantEvaluator.evaluate(context, body);
            if (Boolean.TRUE.equals(constant)) {
                isBad = true;
            }
        }

        if (isBad) {
            context.report(
                ISSUE,
                verifyMethod,
                context.getNameLocation(verifyMethod),
                "Using the default `HostnameVerifier` implementation or one that always returns true is insecure."
            );
        }
    }

    private static boolean isTrueLiteral(@NotNull JavaContext context, @NotNull UExpression expression) {
        UExpression cleared = UastUtils.skipParenthesizedExprDown(expression);
        if (cleared instanceof ULiteralExpression) {
            Object value = ((ULiteralExpression) cleared).getValue();
            return Boolean.TRUE.equals(value);
        }
        Object evaluated = ConstantEvaluator.evaluate(context, cleared);
        return Boolean.TRUE.equals(evaluated);
    }

    private static class ReturnVisitor extends AbstractUastVisitor {
        private final JavaContext context;
        boolean hasReturns = false;
        boolean allReturnsTrue = true;

        ReturnVisitor(JavaContext context) {
            this.context = context;
        }

        @Override
        public boolean visitReturnExpression(@NotNull UReturnExpression node) {
            hasReturns = true;
            UExpression returnExpression = node.getReturnExpression();
            if (returnExpression == null || !isTrueLiteral(context, returnExpression)) {
                allReturnsTrue = false;
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