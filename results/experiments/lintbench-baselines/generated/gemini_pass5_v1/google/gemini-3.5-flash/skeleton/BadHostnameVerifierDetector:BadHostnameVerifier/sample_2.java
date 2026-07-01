package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.intellij.psi.PsiModifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.jetbrains.uast.UBlockExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.ULambdaExpression;
import org.jetbrains.uast.ULiteralExpression;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UReturnExpression;
import org.jetbrains.uast.UThrowExpression;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.visitor.AbstractUastVisitor;

public class BadHostnameVerifierDetector extends Detector implements SourceCodeScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(BadHostnameVerifierDetector.class, Scope.JAVA_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "BadHostnameVerifier",
                    "Insecure HostnameVerifier",
                    "An implementation of `HostnameVerifier` that always returns true trusts "
                            + "any SSL certificate, making the connection vulnerable to "
                            + "Man-in-the-Middle (MitM) attacks.",
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
        if (declaration.isInterface()) {
            return;
        }
        if (declaration.hasModifierProperty(PsiModifier.ABSTRACT)) {
            return;
        }

        for (UMethod method : declaration.getMethods()) {
            if ("verify".equals(method.getName()) && method.getUastParameters().size() == 2) {
                if (alwaysReturnsTrue(method)) {
                    context.report(
                            ISSUE,
                            method,
                            context.getLocation(method),
                            "Insecure HostnameVerifier: `verify` always returns `true`, trusting all hostnames");
                }
            }
        }
    }

    private boolean alwaysReturnsTrue(UMethod method) {
        UExpression body = method.getUastBody();
        if (body == null) {
            return false;
        }

        if (!(body instanceof UBlockExpression)) {
            return isTrueLiteral(body);
        }

        List<UReturnExpression> returns = new ArrayList<>();
        body.accept(new AbstractUastVisitor() {
            @Override
            public boolean visitLambdaExpression(ULambdaExpression node) {
                return true;
            }

            @Override
            public boolean visitClass(UClass node) {
                return true;
            }

            @Override
            public boolean visitReturnExpression(UReturnExpression node) {
                returns.add(node);
                return super.visitReturnExpression(node);
            }
        });

        if (returns.isEmpty()) {
            return false;
        }

        for (UReturnExpression ret : returns) {
            UExpression expr = ret.getReturnExpression();
            if (expr == null || !isTrueLiteral(expr)) {
                return false;
            }
        }

        return true;
    }

    private boolean isTrueLiteral(UExpression expression) {
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