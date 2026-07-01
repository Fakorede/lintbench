package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.jetbrains.uast.UBlockExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UReturnExpression;
import org.jetbrains.uast.visitor.AbstractUastVisitor;

public class AllowAllHostnameVerifierDetector extends Detector implements SourceCodeScanner {

    public static final Issue ISSUE = Issue.create(
            "AllowAllHostnameVerifier",
            "Insecure `HostnameVerifier`",
            "This check looks for use of HostnameVerifier implementations whose `verify` " +
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
    public void visitClass(JavaContext context, UClass declaration) {
        if (declaration.isInterface()) {
            return;
        }

        UMethod[] methods = declaration.getMethods();
        for (UMethod method : methods) {
            if ("verify".equals(method.getName())) {
                if (method.getUastParameters().size() == 2) {
                    checkVerifyMethod(context, method);
                }
            }
        }
    }

    private void checkVerifyMethod(JavaContext context, UMethod method) {
        UExpression body = method.getUastBody();
        if (body == null) {
            return;
        }

        List<UReturnExpression> returns = new ArrayList<>();
        body.accept(new AbstractUastVisitor() {
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

        boolean alwaysReturnsTrue = false;

        if (returns.isEmpty()) {
            if (!(body instanceof UBlockExpression) && isBooleanTrue(body)) {
                alwaysReturnsTrue = true;
            }
        } else {
            alwaysReturnsTrue = true;
            for (UReturnExpression ret : returns) {
                UExpression returnVal = ret.getReturnExpression();
                if (returnVal == null || !isBooleanTrue(returnVal)) {
                    alwaysReturnsTrue = false;
                    break;
                }
            }
        }

        if (alwaysReturnsTrue) {
            context.report(
                    ISSUE,
                    method,
                    context.getNameLocation(method),
                    "Using a `HostnameVerifier` that always returns true is insecure because " +
                    "it trusts any hostname in TLS/SSL certificates presented by peers."
            );
        }
    }

    private boolean isBooleanTrue(UExpression expression) {
        Object evaluated = expression.evaluate();
        if (evaluated instanceof Boolean) {
            return (Boolean) evaluated;
        }
        String text = expression.asSourceString();
        return "true".equals(text);
    }
}