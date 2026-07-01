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
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.ULiteralExpression;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UReturnExpression;
import org.jetbrains.uast.visitor.AbstractUastVisitor;

public class BadHostnameVerifierDetector extends Detector implements SourceCodeScanner {

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

    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList("javax.net.ssl.HostnameVerifier");
    }

    @Override
    public void visitClass(JavaContext context, UClass declaration) {
        if (declaration.isInterface()) {
            return;
        }

        UMethod verifyMethod = null;
        for (UMethod method : declaration.getMethods()) {
            if ("verify".equals(method.getName()) && method.getUastParameters().size() == 2) {
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

        if (checkAlwaysReturnsTrue(context, body, verifyMethod)) {
            context.report(
                ISSUE,
                verifyMethod,
                context.getNameLocation(verifyMethod),
                "Using a `HostnameVerifier` that unconditionally returns `true` is insecure."
            );
        }
    }

    private boolean checkAlwaysReturnsTrue(JavaContext context, UExpression body, UMethod method) {
        if (isTrueLiteral(context, body)) {
            return true;
        }
        if (body instanceof UReturnExpression) {
            UExpression returnValue = ((UReturnExpression) body).getReturnExpression();
            return returnValue != null && isTrueLiteral(context, returnValue);
        }
        AlwaysReturnsTrueVisitor visitor = new AlwaysReturnsTrueVisitor(context, method);
        body.accept(visitor);
        return visitor.alwaysReturnsTrue();
    }

    private static boolean isTrueLiteral(JavaContext context, UExpression expression) {
        if (expression instanceof ULiteralExpression) {
            Object value = ((ULiteralExpression) expression).getValue();
            return Boolean.TRUE.equals(value);
        }
        try {
            Object constant = ConstantEvaluator.evaluate(context, expression);
            return Boolean.TRUE.equals(constant);
        } catch (Exception e) {
            // ignore
        }
        return false;
    }

    private static class AlwaysReturnsTrueVisitor extends AbstractUastVisitor {
        private final JavaContext context;
        private final UMethod targetMethod;
        private boolean hasReturns = false;
        private boolean allReturnsTrue = true;

        AlwaysReturnsTrueVisitor(JavaContext context, UMethod targetMethod) {
            this.context = context;
            this.targetMethod = targetMethod;
        }

        @Override
        public boolean visitReturnExpression(UReturnExpression node) {
            if (belongsToMethod(node, targetMethod)) {
                hasReturns = true;
                UExpression returnExpression = node.getReturnExpression();
                if (returnExpression == null || !isTrueLiteral(context, returnExpression)) {
                    allReturnsTrue = false;
                }
            }
            return super.visitReturnExpression(node);
        }

        private boolean belongsToMethod(UReturnExpression returnExpr, UMethod method) {
            org.jetbrains.uast.UElement current = returnExpr.getUastParent();
            while (current != null) {
                if (current == method) {
                    return true;
                }
                if (current instanceof org.jetbrains.uast.ULambdaExpression || current instanceof UClass) {
                    return false;
                }
                current = current.getUastParent();
            }
            return false;
        }

        boolean alwaysReturnsTrue() {
            return hasReturns && allReturnsTrue;
        }
    }
}