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
import org.jetbrains.uast.UBlockExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.ULambdaExpression;
import org.jetbrains.uast.ULiteralExpression;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UParenthesizedExpression;
import org.jetbrains.uast.UReturnExpression;
import org.jetbrains.uast.visitor.AbstractUastVisitor;

public class AllowAllHostnameVerifierDetector extends Detector implements SourceCodeScanner {

    public static final Issue ISSUE = Issue.create(
            "AllowAllHostnameVerifier",
            "Insecure HostnameVerifier",
            "This check looks for use of HostnameVerifier implementations whose `verify` " +
            "method always returns true (thus trusting any hostname) which could result " +
            "in insecure network traffic caused by trusting arbitrary hostnames in " +
            "TLS/SSL certificates presented by peers.",
            Category.SECURITY,
            6,
            Severity.WARNING,
            new Implementation(
                    AllowAllHostnameVerifierDetector.class,
                    Scope.JAVA_FILE_SCOPE
            )
    );

    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList("javax.net.ssl.HostnameVerifier");
    }

    @Override
    public void visitClass(JavaContext context, UClass declaration) {
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

        if (alwaysReturnsTrue(body)) {
            context.report(
                    ISSUE,
                    declaration,
                    context.getNameLocation(verifyMethod),
                    "Using a `HostnameVerifier` that trusts all hostnames is insecure"
            );
        }
    }

    @Override
    public void visitClass(JavaContext context, ULambdaExpression lambda) {
        UExpression body = lambda.getBody();
        if (body == null) {
            return;
        }

        if (alwaysReturnsTrue(body)) {
            context.report(
                    ISSUE,
                    lambda,
                    context.getLocation(lambda),
                    "Using a `HostnameVerifier` that trusts all hostnames is insecure"
            );
        }
    }

    private static boolean alwaysReturnsTrue(UExpression body) {
        if (body == null) {
            return false;
        }
        UExpression unwrapped = skipParentheses(body);
        if (unwrapped instanceof ULiteralExpression) {
            return Boolean.TRUE.equals(((ULiteralExpression) unwrapped).getValue());
        }
        if (unwrapped instanceof UBlockExpression) {
            List<UExpression> expressions = ((UBlockExpression) unwrapped).getExpressions();
            if (expressions.size() == 1) {
                return alwaysReturnsTrue(expressions.get(0));
            }
        }
        if (unwrapped instanceof UReturnExpression) {
            return alwaysReturnsTrue(((UReturnExpression) unwrapped).getReturnExpression());
        }

        ReturnVisitor visitor = new ReturnVisitor();
        unwrapped.accept(visitor);

        if (visitor.hasReturns) {
            return visitor.allReturnsTrue;
        }

        if (unwrapped instanceof UBlockExpression) {
            List<UExpression> expressions = ((UBlockExpression) unwrapped).getExpressions();
            if (!expressions.isEmpty()) {
                UExpression last = expressions.get(expressions.size() - 1);
                return alwaysReturnsTrue(last);
            }
        }

        return false;
    }

    private static UExpression skipParentheses(UExpression expression) {
        while (expression instanceof UParenthesizedExpression) {
            expression = ((UParenthesizedExpression) expression).getExpression();
        }
        return expression;
    }

    private static class ReturnVisitor extends AbstractUastVisitor {
        boolean hasReturns = false;
        boolean allReturnsTrue = true;

        @Override
        public boolean visitClass(UClass node) {
            return true;
        }

        @Override
        public boolean visitLambdaExpression(ULambdaExpression node) {
            return true;
        }

        @Override
        public boolean visitMethod(UMethod node) {
            return true;
        }

        @Override
        public boolean visitReturnExpression(UReturnExpression node) {
            hasReturns = true;
            UExpression returnVal = node.getReturnExpression();
            if (returnVal == null || !isTrueLiteral(skipParentheses(returnVal))) {
                allReturnsTrue = false;
            }
            return super.visitReturnExpression(node);
        }

        private static boolean isTrueLiteral(UExpression expression) {
            if (expression instanceof ULiteralExpression) {
                Object value = ((ULiteralExpression) expression).getValue();
                return Boolean.TRUE.equals(value);
            }
            return false;
        }
    }
}