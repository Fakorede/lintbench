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
import java.util.Collections;
import java.util.List;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
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
                    "This check looks for implementations of HostnameVerifier whose verify method always returns true " +
                    "(thus trusting any hostname) which could result in insecure network traffic caused by trusting " +
                    "arbitrary hostnames in TLS/SSL certificates presented by peers.",
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
        for (UMethod method : declaration.getMethods()) {
            if ("verify".equals(method.getName())) {
                UElement body = method.getUastBody();
                if (body != null) {
                    body.accept(new AbstractUastVisitor() {
                        @Override
                        public boolean visitReturnExpression(UReturnExpression node) {
                            BadHostnameVerifierDetector.this.visitReturnExpression(context, node);
                            return super.visitReturnExpression(node);
                        }

                        @Override
                        public boolean visitCallExpression(UCallExpression node) {
                            BadHostnameVerifierDetector.this.visitCallExpression(context, node);
                            return super.visitCallExpression(node);
                        }

                        @Override
                        public boolean visitThrowExpression(UThrowExpression node) {
                            BadHostnameVerifierDetector.this.visitThrowExpression(context, node);
                            return super.visitThrowExpression(node);
                        }
                    });
                }
            }
        }
    }

    public void visitThrowExpression(@NonNull JavaContext context, @NonNull UThrowExpression node) {
        // Throwing an exception breaks the unconditional trust pattern; no action needed.
    }

    public void visitCallExpression(@NonNull JavaContext context, @NonNull UCallExpression node) {
        // Method calls may contain verification logic; no action needed for this heuristic.
    }

    public void visitReturnExpression(@NonNull JavaContext context, @NonNull UReturnExpression node) {
        UExpression returnValue = node.getReturnExpression();
        if (returnValue != null && Boolean.TRUE.equals(returnValue.evaluate())) {
            context.report(ISSUE, node, context.getLocation(node),
                    "HostnameVerifier.verify() always returns true, which trusts all hostnames and disables SSL/TLS security.");
        }
    }
}