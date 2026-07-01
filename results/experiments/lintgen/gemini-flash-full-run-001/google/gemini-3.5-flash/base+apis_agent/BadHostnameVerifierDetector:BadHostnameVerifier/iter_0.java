package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
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
import org.jetbrains.uast.UParenthesizedExpression;
import org.jetbrains.uast.UReturnExpression;
import org.jetbrains.uast.visitor.AbstractUastVisitor;

public class BadHostnameVerifierDetector extends Detector implements SourceCodeScanner {

    public static final Issue ISSUE = Issue.create(
        "BadHostnameVerifier",
        "Insecure HostnameVerifier",
        "This check looks for implementations of `HostnameVerifier` whose " +
        "`verify` method always returns true (thus trusting any " +
        "hostname) which could result in insecure network traffic caused " +
        "by trusting arbitrary hostnames in TLS/SSL certificates " +
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
        UMethod verifyMethod = null;
        for (UMethod method : declaration.getMethods()) {
            if ("verify".equals(method.getName()) && method.getParameterList().getParametersCount() == 2) {
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

        if (isTrueLiteral(body)) {
            report(context, verifyMethod);
            return;
        }

        ReturnVisitor visitor = new ReturnVisitor();
        body.accept(visitor);
        if (visitor.hasReturns && visitor.allReturnsTrue) {
            report(context, verifyMethod);
        }
    }

    private void report(JavaContext context, UMethod method) {
        context.report(
            ISSUE,
            method,
            context.getNameLocation(method),
            "`verify` always returns `true`, which ignores hostname verification"
        );
    }

    private static boolean isTrueLiteral(UExpression expression) {
        if (expression instanceof UParenthesizedExpression) {
            return isTrueLiteral(((UParenthesizedExpression) expression).getExpression());
        }
        if (expression instanceof ULiteralExpression) {
            Object value = ((ULiteralExpression) expression).getValue();
            return Boolean.TRUE.equals(value);
        }
        return false;
    }

    private static class ReturnVisitor extends AbstractUastVisitor {
        boolean hasReturns = false;
        boolean allReturnsTrue = true;

        @Override
        public boolean visitReturnExpression(@NotNull UReturnExpression node) {
            hasReturns = true;
            UExpression returnVal = node.getReturnExpression();
            if (returnVal == null || !isTrueLiteral(returnVal)) {
                allReturnsTrue = false;
            }
            return super.visitReturnExpression(node);
        }

        @Override
        public boolean visitClass(@NotNull UClass node) {
            return true; // Skip nested classes
        }

        @Override
        public boolean visitLambdaExpression(@NotNull ULambdaExpression node) {
            return true; // Skip lambdas
        }
    }
}