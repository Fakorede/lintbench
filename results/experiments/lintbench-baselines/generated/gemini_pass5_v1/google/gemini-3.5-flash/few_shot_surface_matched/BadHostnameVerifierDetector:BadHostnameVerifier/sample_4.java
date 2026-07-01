package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
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
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UReturnExpression;
import org.jetbrains.uast.UThrowExpression;

public class BadHostnameVerifierDetector extends Detector implements SourceCodeScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(BadHostnameVerifierDetector.class, Scope.JAVA_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                            "BadHostnameVerifier",
                            "Insecure HostnameVerifier",
                            "This check looks for implementations of `HostnameVerifier` whose "
                                    + "`verify` method always returns true (thus trusting any "
                                    + "hostname) which could result in insecure network traffic caused "
                                    + "by trusting arbitrary hostnames in TLS/SSL certificates "
                                    + "presented by peers.",
                            Category.SECURITY,
                            6,
                            Severity.WARNING,
                            IMPLEMENTATION)
                    .setAndroidSpecific(true);

    public BadHostnameVerifierDetector() {}

    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList("javax.net.ssl.HostnameVerifier");
    }

    @Override
    public void visitClass(JavaContext context, UClass declaration) {
        if (declaration.isInterface() || declaration.hasModifierProperty(PsiModifier.ABSTRACT)) {
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
        if (body != null) {
            VerifyMethodVisitor visitor = new VerifyMethodVisitor(context);
            body.accept(visitor);
            if (visitor.alwaysReturnsTrue()) {
                context.report(
                        ISSUE,
                        declaration,
                        context.getNameLocation(declaration),
                        "Using an implementation of `HostnameVerifier` that trusts all hostnames is insecure");
            }
        }
    }

    public void visitThrowExpression(JavaContext context, UThrowExpression node) {
        // Hook for subclass or custom analysis
    }

    public void visitCallExpression(JavaContext context, UCallExpression node) {
        // Hook for subclass or custom analysis
    }

    public void visitReturnExpression(JavaContext context, UReturnExpression node) {
        // Hook for subclass or custom analysis
    }

    private class VerifyMethodVisitor extends org.jetbrains.uast.visitor.AbstractUastVisitor {
        private final JavaContext context;
        private boolean hasReturn = false;
        private boolean alwaysReturnsTrue = true;
        private boolean hasThrows = false;

        VerifyMethodVisitor(JavaContext context) {
            this.context = context;
        }

        @Override
        public boolean visitReturnExpression(UReturnExpression node) {
            hasReturn = true;
            BadHostnameVerifierDetector.this.visitReturnExpression(context, node);
            UExpression value = node.getReturnExpression();
            if (value != null) {
                Object constant = value.evaluate();
                if (!Boolean.TRUE.equals(constant)) {
                    alwaysReturnsTrue = false;
                }
            } else {
                alwaysReturnsTrue = false;
            }
            return super.visitReturnExpression(node);
        }

        @Override
        public boolean visitThrowExpression(UThrowExpression node) {
            hasThrows = true;
            BadHostnameVerifierDetector.this.visitThrowExpression(context, node);
            alwaysReturnsTrue = false;
            return super.visitThrowExpression(node);
        }

        @Override
        public boolean visitCallExpression(UCallExpression node) {
            BadHostnameVerifierDetector.this.visitCallExpression(context, node);
            return super.visitCallExpression(node);
        }

        public boolean alwaysReturnsTrue() {
            return hasReturn && alwaysReturnsTrue && !hasThrows;
        }
    }
}