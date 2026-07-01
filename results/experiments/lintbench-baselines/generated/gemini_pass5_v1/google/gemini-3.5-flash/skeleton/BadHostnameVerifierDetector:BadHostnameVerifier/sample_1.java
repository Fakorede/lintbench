package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.ConstantEvaluator;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.intellij.psi.PsiModifier;
import java.util.Collections;
import java.util.List;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.ULambdaExpression;
import org.jetbrains.uast.ULiteralExpression;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UParameter;
import org.jetbrains.uast.UParenthesizedExpression;
import org.jetbrains.uast.UReturnExpression;
import org.jetbrains.uast.visitor.AbstractUastVisitor;

public class BadHostnameVerifierDetector extends Detector implements SourceCodeScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(BadHostnameVerifierDetector.class, Scope.JAVA_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "BadHostnameVerifier",
                    "Insecure HostnameVerifier",
                    "This check looks for implementations of `HostnameVerifier` whose "
                            + "`verify` method always returns true (thus trusting any hostname) "
                            + "which could result in insecure network traffic caused by trusting "
                            + "arbitrary hostnames in TLS/SSL certificates presented by peers.",
                    Category.SECURITY,
                    6,
                    Severity.WARNING,
                    IMPLEMENTATION);

    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList("javax.net.ssl.HostnameVerifier");
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        if (declaration.isInterface() || declaration.hasModifierProperty(PsiModifier.ABSTRACT)) {
            return;
        }

        UMethod verifyMethod = null;
        for (UMethod method : declaration.getMethods()) {
            if ("verify".equals(method.getName())) {
                List<UParameter> parameters = method.getUastParameters();
                if (parameters.size() == 2) {
                    verifyMethod = method;
                    break;
                }
            }
        }

        if (verifyMethod != null) {
            UExpression body = verifyMethod.getUastBody();
            if (body != null) {
                ReturnVisitor visitor = new ReturnVisitor();
                body.accept(visitor);
                if (visitor.hasReturns && visitor.allReturnsTrue) {
                    context.report(
                            ISSUE,
                            declaration,
                            context.getLocation(verifyMethod),
                            "This `HostnameVerifier` implementation always returns `true`, "
                                    + "accepting any TLS/SSL certificate. This makes the connection "
                                    + "vulnerable to man-in-the-middle attacks.");
                }
            }
        }
    }

    private static class ReturnVisitor extends AbstractUastVisitor {
        boolean hasReturns = false;
        boolean allReturnsTrue = true;

        @Override
        public boolean visitReturnExpression(UReturnExpression node) {
            hasReturns = true;
            UExpression returnExpression = node.getReturnExpression();
            if (returnExpression == null) {
                allReturnsTrue = false;
            } else {
                if (!isTrueLiteral(returnExpression)) {
                    allReturnsTrue = false;
                }
            }
            return super.visitReturnExpression(node);
        }

        @Override
        public boolean visitLambdaExpression(ULambdaExpression node) {
            return true;
        }

        @Override
        public boolean visitClass(UClass node) {
            return true;
        }

        private boolean isTrueLiteral(UExpression expression) {
            if (expression instanceof ULiteralExpression) {
                Object value = ((ULiteralExpression) expression).getValue();
                return Boolean.TRUE.equals(value);
            }
            if (expression instanceof UParenthesizedExpression) {
                return isTrueLiteral(((UParenthesizedExpression) expression).getExpression());
            }
            Object evaluated = ConstantEvaluator.evaluate(null, expression);
            return Boolean.TRUE.equals(evaluated);
        }
    }
}