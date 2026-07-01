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
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UExpression;
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
                    "A `HostnameVerifier` whose `verify` method always returns true will accept "
                            + "any hostname. This allows an attacker to redirect network traffic "
                            + "by presenting a certificate for an arbitrary hostname, bypassing "
                            + "TLS/SSL hostname verification.",
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
        for (UMethod method : declaration.getMethods()) {
            if (!"verify".equals(method.getName())) {
                continue;
            }
            if (method.getUastParameters().size() != 2) {
                continue;
            }

            UExpression body = method.getUastBody();
            if (body == null) {
                continue;
            }

            ReturnVisitor visitor = new ReturnVisitor(context);
            body.accept(visitor);

            if (visitor.seenReturn && visitor.allTrue) {
                context.report(
                        ISSUE,
                        method,
                        context.getLocation(method),
                        "`verify` always returns `true`, which trusts every TLS/SSL hostname "
                                + "and is insecure.");
            }
        }
    }

    private static class ReturnVisitor extends AbstractUastVisitor {
        private final JavaContext context;
        private boolean seenReturn = false;
        private boolean allTrue = true;

        ReturnVisitor(JavaContext context) {
            this.context = context;
        }

        @Override
        public boolean visitReturnExpression(UReturnExpression node) {
            seenReturn = true;

            UExpression returnExpression = node.getReturnExpression();
            if (returnExpression == null) {
                allTrue = false;
                return super.visitReturnExpression(node);
            }

            Object value = ConstantEvaluator.evaluate(context, returnExpression);
            if (!Boolean.TRUE.equals(value)) {
                allTrue = false;
            }

            return super.visitReturnExpression(node);
        }

        @Override
        public boolean visitThrowExpression(UThrowExpression node) {
            // A thrown exception is not a successful verification, so the method does not
            // unconditionally return true.
            allTrue = false;
            return super.visitThrowExpression(node);
        }

        @Override
        public boolean visitCallExpression(UCallExpression node) {
            // Calls do not affect whether every return statement is the literal true.
            return super.visitCallExpression(node);
        }
    }
}