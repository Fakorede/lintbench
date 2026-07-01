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
        for (UMethod method : declaration.getMethods()) {
            if ("verify".equals(method.getName()) && method.getUastParameters().size() == 2) {
                if (alwaysReturnsTrue(method)) {
                    context.report(
                            ISSUE,
                            context.getLocation(method),
                            "This `HostnameVerifier` is insecure because it always returns true, trusting any hostname.");
                }
            }
        }
    }

    private boolean alwaysReturnsTrue(UMethod method) {
        UExpression body = method.getUastBody();
        if (body == null) {
            return false;
        }

        final boolean[] hasReturn = {false};
        final boolean[] returnsFalse = {false};
        final boolean[] returnsTrue = {false};
        final boolean[] returnsOther = {false};

        body.accept(new AbstractUastVisitor() {
            @Override
            public boolean visitReturnExpression(UReturnExpression node) {
                hasReturn[0] = true;
                UExpression returnExpression = node.getReturnExpression();
                if (returnExpression != null) {
                    Object value = returnExpression.evaluate();
                    if (Boolean.FALSE.equals(value)) {
                        returnsFalse[0] = true;
                    } else if (Boolean.TRUE.equals(value)) {
                        returnsTrue[0] = true;
                    } else {
                        returnsOther[0] = true;
                    }
                }
                return super.visitReturnExpression(node);
            }
        });

        if (!hasReturn[0]) {
            return false;
        }

        return returnsTrue[0] && !returnsFalse[0] && !returnsOther[0];
    }

    public void visitThrowExpression(JavaContext context, UThrowExpression node) {
        // Handled in visitClass
    }

    public void visitCallExpression(JavaContext context, UCallExpression node) {
        // Handled in visitClass
    }

    public void visitReturnExpression(JavaContext context, UReturnExpression node) {
        // Handled in visitClass
    }
}