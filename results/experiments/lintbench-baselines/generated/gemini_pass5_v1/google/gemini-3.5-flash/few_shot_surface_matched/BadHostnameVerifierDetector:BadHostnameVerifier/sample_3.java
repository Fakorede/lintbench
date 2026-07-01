package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.intellij.psi.PsiMethod;
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
                    "This check looks for implementations of `HostnameVerifier` whose `verify` "
                            + "method always returns true (thus trusting any hostname) which could "
                            + "result in insecure network traffic caused by trusting arbitrary "
                            + "hostnames in TLS/SSL certificates presented by peers.",
                    Category.SECURITY,
                    6,
                    Severity.WARNING,
                    IMPLEMENTATION)
                    .setAndroidSpecific(true);

    public BadHostnameVerifierDetector() {}

    @com.android.annotations.Nullable
    @Override
    public java.util.List<String> applicableSuperClasses() {
        return java.util.Collections.singletonList("javax.net.ssl.HostnameVerifier");
    }

    @Override
    public void visitClass(@com.android.annotations.NonNull JavaContext context, @com.android.annotations.NonNull UClass declaration) {
        if (declaration.isInterface() || context.getEvaluator().isAbstract(declaration)) {
            return;
        }

        PsiMethod[] methods = declaration.findMethodsByName("verify", false);
        for (PsiMethod method : methods) {
            if (context.getEvaluator().getParameterCount(method) == 2) {
                UMethod uMethod = (UMethod) context.getUastContext().getMethod(method);
                if (uMethod != null) {
                    UExpression body = uMethod.getUastBody();
                    if (body != null) {
                        VerifierVisitor visitor = new VerifierVisitor();
                        body.accept(visitor);
                        if (visitor.alwaysReturnsTrue()) {
                            context.report(
                                    ISSUE,
                                    declaration,
                                    context.getNameLocation(declaration),
                                    "This `HostnameVerifier` implementation always returns `true`, "
                                            + "which means it trusts any hostname and is insecure. "
                                            + "See https://goo.gle/BadHostnameVerifier for more information.");
                        }
                    }
                }
            }
        }
    }

    @Override
    public void visitThrowExpression(@com.android.annotations.NonNull JavaContext context, @com.android.annotations.NonNull UThrowExpression node) {
    }

    @Override
    public void visitCallExpression(@com.android.annotations.NonNull JavaContext context, @com.android.annotations.NonNull UCallExpression node) {
    }

    @Override
    public void visitReturnExpression(@com.android.annotations.NonNull JavaContext context, @com.android.annotations.NonNull UReturnExpression node) {
    }

    private static class VerifierVisitor extends AbstractUastVisitor {
        private boolean returnsTrue = false;
        private boolean returnsFalse = false;
        private boolean throwsException = false;
        private boolean hasOtherReturns = false;
        private boolean hasCalls = false;

        public boolean alwaysReturnsTrue() {
            return returnsTrue && !returnsFalse && !throwsException && !hasOtherReturns && !hasCalls;
        }

        @Override
        public boolean visitReturnExpression(@com.android.annotations.NonNull UReturnExpression node) {
            UExpression expression = node.getReturnExpression();
            if (expression != null) {
                if (expression instanceof ULiteralExpression) {
                    Object val = ((ULiteralExpression) expression).getValue();
                    if (Boolean.TRUE.equals(val)) {
                        returnsTrue = true;
                    } else if (Boolean.FALSE.equals(val)) {
                        returnsFalse = true;
                    } else {
                        hasOtherReturns = true;
                    }
                } else {
                    String src = expression.asSourceString();
                    if ("true".equals(src)) {
                        returnsTrue = true;
                    } else if ("false".equals(src)) {
                        returnsFalse = true;
                    } else {
                        hasOtherReturns = true;
                    }
                }
            }
            return super.visitReturnExpression(node);
        }

        @Override
        public boolean visitThrowExpression(@com.android.annotations.NonNull UThrowExpression node) {
            throwsException = true;
            return super.visitThrowExpression(node);
        }

        @Override
        public boolean visitCallExpression(@com.android.annotations.NonNull UCallExpression node) {
            hasCalls = true;
            return super.visitCallExpression(node);
        }
    }
}