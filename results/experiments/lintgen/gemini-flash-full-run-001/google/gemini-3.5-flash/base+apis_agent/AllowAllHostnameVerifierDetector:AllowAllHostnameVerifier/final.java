package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import java.util.Collections;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.UBlockExpression;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.ULambdaExpression;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UReferenceExpression;
import org.jetbrains.uast.UReturnExpression;
import org.jetbrains.uast.visitor.AbstractUastVisitor;

public class AllowAllHostnameVerifierDetector extends Detector implements SourceCodeScanner {

    public static final Issue ISSUE = Issue.create(
            "AllowAllHostnameVerifier",
            "Insecure `HostnameVerifier`",
            "This check looks for use of HostnameVerifier implementations " +
            "whose `verify` method always returns true (thus trusting any hostname) " +
            "which could result in insecure network traffic caused by trusting arbitrary " +
            "hostnames in TLS/SSL certificates presented by peers.",
            Category.SECURITY,
            6,
            Severity.WARNING,
            new Implementation(
                    AllowAllHostnameVerifierDetector.class,
                    Scope.JAVA_FILE_SCOPE
            )
    );

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

        for (UMethod method : declaration.getMethods()) {
            if ("verify".equals(method.getName())) {
                UExpression body = method.getUastBody();
                if (alwaysReturnsTrue(body)) {
                    context.report(
                            ISSUE,
                            method,
                            context.getNameLocation(method),
                            "Trusting all hostnames is insecure"
                    );
                }
            }
        }
    }

    @Override
    public void visitClass(@NotNull JavaContext context, @NotNull ULambdaExpression lambda) {
        UExpression body = lambda.getBody();
        if (alwaysReturnsTrue(body)) {
            context.report(
                    ISSUE,
                    lambda,
                    context.getNameLocation(lambda),
                    "Trusting all hostnames is insecure"
            );
        }
    }

    @Override
    @Nullable
    public List<String> getApplicableConstructorTypes() {
        return Collections.singletonList("org.apache.http.conn.ssl.AllowAllHostnameVerifier");
    }

    @Override
    public void visitConstructor(@NotNull JavaContext context, @NotNull UCallExpression node, @NotNull PsiMethod constructor) {
        context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Using `AllowAllHostnameVerifier` is insecure"
        );
    }

    @Override
    @Nullable
    public List<String> getApplicableReferenceNames() {
        return Collections.singletonList("ALLOW_ALL_HOSTNAME_VERIFIER");
    }

    @Override
    public void visitReference(@NotNull JavaContext context, @NotNull UReferenceExpression reference, @NotNull PsiElement referenced) {
        if (referenced instanceof PsiField) {
            PsiClass containingClass = ((PsiField) referenced).getContainingClass();
            if (containingClass != null) {
                String qualifiedName = containingClass.getQualifiedName();
                if ("org.apache.http.conn.ssl.SSLSocketFactory".equals(qualifiedName)
                        || "org.apache.http.conn.ssl.AllowAllHostnameVerifier".equals(qualifiedName)) {
                    context.report(
                            ISSUE,
                            reference,
                            context.getLocation(reference),
                            "Using `ALLOW_ALL_HOSTNAME_VERIFIER` is insecure"
                    );
                }
            }
        }
    }

    private boolean alwaysReturnsTrue(UExpression body) {
        if (body == null) {
            return false;
        }

        // 1. Check if the expression itself evaluates to true
        Object constant = body.evaluate();
        if (Boolean.TRUE.equals(constant)) {
            return true;
        }

        // 2. If it's a block, check the last expression
        if (body instanceof UBlockExpression) {
            List<UExpression> expressions = ((UBlockExpression) body).getExpressions();
            if (!expressions.isEmpty()) {
                UExpression last = expressions.get(expressions.size() - 1);
                if (last instanceof UReturnExpression) {
                    UExpression returnVal = ((UReturnExpression) last).getReturnExpression();
                    if (returnVal != null && Boolean.TRUE.equals(returnVal.evaluate())) {
                        ReturnVisitor visitor = new ReturnVisitor();
                        body.accept(visitor);
                        return visitor.hasReturns && visitor.allReturnsTrue;
                    }
                } else {
                    // Implicit return (Kotlin lambda)
                    Object lastVal = last.evaluate();
                    if (Boolean.TRUE.equals(lastVal)) {
                        ReturnVisitor visitor = new ReturnVisitor();
                        body.accept(visitor);
                        return !visitor.hasReturns || visitor.allReturnsTrue;
                    }
                }
            }
        }

        // 3. Fallback to visitor for explicit returns
        ReturnVisitor visitor = new ReturnVisitor();
        body.accept(visitor);
        return visitor.hasReturns && visitor.allReturnsTrue;
    }

    private static class ReturnVisitor extends AbstractUastVisitor {
        boolean hasReturns = false;
        boolean allReturnsTrue = true;

        @Override
        public boolean visitClass(@NotNull UClass node) {
            return true; // Do not descend into nested classes
        }

        @Override
        public boolean visitLambdaExpression(@NotNull ULambdaExpression node) {
            return true; // Do not descend into nested lambdas
        }

        @Override
        public boolean visitReturnExpression(@NotNull UReturnExpression node) {
            hasReturns = true;
            UExpression returnVal = node.getReturnExpression();
            if (returnVal == null) {
                allReturnsTrue = false;
            } else {
                Object value = returnVal.evaluate();
                if (!Boolean.TRUE.equals(value)) {
                    allReturnsTrue = false;
                }
            }
            return super.visitReturnExpression(node);
        }
    }
}