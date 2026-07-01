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
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.ULiteralExpression;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UReturnExpression;
import org.jetbrains.uast.UThrowExpression;
import org.jetbrains.uast.visitor.AbstractUastVisitor;

public class BadHostnameVerifierDetector extends Detector implements SourceCodeScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(BadHostnameVerifierDetector.class, Scope.JAVA_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "BadHostnameVerifier",
                    "Insecure HostnameVerifier",
                    "This check looks for implementations of `HostnameVerifier` whose `verify` method always returns true (thus trusting any hostname) which could result in insecure network traffic caused by trusting arbitrary hostnames in TLS/SSL certificates presented by peers.",
                    Category.SECURITY,
                    6,
                    Severity.WARNING,
                    IMPLEMENTATION);

    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList("javax.net.ssl.HostnameVerifier");
    }

    @Override
    public void visitClass(JavaContext context, UClass declaration) {
        if (declaration.isInterface() || context.getEvaluator().isAbstract(declaration)) {
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

        final boolean[] alwaysReturnsTrue = {true};
        final boolean[] hasReturns = {false};

        body.accept(new AbstractUastVisitor() {
            @Override
            public boolean visitReturnExpression(UReturnExpression node) {
                hasReturns[0] = true;
                BadHostnameVerifierDetector.this.visitReturnExpression(node);
                UExpression expression = node.getReturnExpression();
                if (expression == null || !isTrueLiteral(expression)) {
                    alwaysReturnsTrue[0] = false;
                }
                return super.visitReturnExpression(node);
            }

            @Override
            public boolean visitThrowExpression(UThrowExpression node) {
                BadHostnameVerifierDetector.this.visitThrowExpression(node);
                return super.visitThrowExpression(node);
            }

            @Override
            public boolean visitCallExpression(UCallExpression node) {
                BadHostnameVerifierDetector.this.visitCallExpression(node);
                return super.visitCallExpression(node);
            }
        });

        if (hasReturns[0] && alwaysReturnsTrue[0]) {
            context.report(
                    ISSUE,
                    declaration,
                    context.getNameLocation(verifyMethod),
                    "Trusting all hostnames can lead to insecure network traffic");
        }
    }

    private static boolean isTrueLiteral(UExpression expression) {
        if (expression instanceof ULiteralExpression) {
            Object value = ((ULiteralExpression) expression).getValue();
            return Boolean.TRUE.equals(value);
        }
        return false;
    }

    public void visitThrowExpression(UThrowExpression node) {
    }

    public void visitCallExpression(UCallExpression node) {
    }

    public void visitReturnExpression(UReturnExpression node) {
    }
}