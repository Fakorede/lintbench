package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.jetbrains.annotations.NonNull;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.ULiteralExpression;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UReferenceExpression;
import org.jetbrains.uast.UReturnExpression;
import org.jetbrains.uast.UastUtils;
import org.jetbrains.uast.visitor.AbstractUastVisitor;

public class AllowAllHostnameVerifierDetector extends Detector implements SourceCodeScanner {

    public static final Issue ISSUE = Issue.create(
            "AllowAllHostnameVerifier",
            "Insecure `HostnameVerifier`",
            "This check looks for use of `HostnameVerifier` implementations whose `verify` " +
            "method always returns true (thus trusting any hostname) which could result in " +
            "insecure network traffic caused by trusting arbitrary hostnames in TLS/SSL " +
            "certificates presented by peers.",
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
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
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

        if (verifyMethod != null) {
            checkVerifyMethod(context, verifyMethod);
        }
    }

    private void checkVerifyMethod(JavaContext context, UMethod method) {
        UExpression body = method.getUastBody();
        if (body == null) {
            return;
        }

        final List<UReturnExpression> returns = new ArrayList<>();
        body.accept(new AbstractUastVisitor() {
            @Override
            public boolean visitReturnExpression(@NonNull UReturnExpression node) {
                returns.add(node);
                return super.visitReturnExpression(node);
            }
        });

        if (returns.isEmpty()) {
            return;
        }

        boolean alwaysReturnsTrue = true;
        for (UReturnExpression returnExpr : returns) {
            UExpression expr = returnExpr.getReturnExpression();
            if (expr == null || !isLiteralTrue(expr)) {
                alwaysReturnsTrue = false;
                break;
            }
        }

        if (alwaysReturnsTrue) {
            Location location = context.getNameLocation(method);
            context.report(
                    ISSUE,
                    method,
                    location,
                    "Using a `HostnameVerifier` that always returns true, which trusts any certificate and is insecure."
            );
        }
    }

    private static boolean isLiteralTrue(UExpression expression) {
        UExpression stripped = UastUtils.skipParenthesizedExprDown(expression);
        if (stripped instanceof ULiteralExpression) {
            Object value = ((ULiteralExpression) stripped).getValue();
            return Boolean.TRUE.equals(value);
        }
        return false;
    }

    @Override
    public List<String> getApplicableConstructorTypes() {
        return Collections.singletonList("org.apache.http.conn.ssl.AllowAllHostnameVerifier");
    }

    @Override
    public void visitConstructor(@NonNull JavaContext context, @NonNull UCallExpression node, @NonNull PsiMethod constructor) {
        context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Using `AllowAllHostnameVerifier` is insecure because it trusts any hostname"
        );
    }

    @Override
    public List<String> getApplicableReferenceNames() {
        return Collections.singletonList("ALLOW_ALL_HOSTNAME_VERIFIER");
    }

    @Override
    public void visitReference(@NonNull JavaContext context, @NonNull UReferenceExpression reference, @NonNull PsiElement referenced) {
        if (referenced instanceof PsiField) {
            PsiField field = (PsiField) referenced;
            PsiClass containingClass = field.getContainingClass();
            if (containingClass != null) {
                String qualifiedName = containingClass.getQualifiedName();
                if ("org.apache.http.conn.ssl.SSLSocketFactory".equals(qualifiedName) ||
                    "org.apache.http.conn.ssl.AllowAllHostnameVerifier".equals(qualifiedName)) {
                    context.report(
                            ISSUE,
                            reference,
                            context.getLocation(reference),
                            "Using `ALLOW_ALL_HOSTNAME_VERIFIER` is insecure because it trusts any hostname"
                    );
                }
            }
        }
    }
}