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
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.ULiteralExpression;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UReturnExpression;
import org.jetbrains.uast.visitor.AbstractUastVisitor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class BadHostnameVerifierDetector extends Detector implements SourceCodeScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(BadHostnameVerifierDetector.class, Scope.JAVA_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "BadHostnameVerifier",
                    "Insecure HostnameVerifier",
                    "This check looks for implementations of HostnameVerifier whose verify method " +
                    "always returns true (thus trusting any hostname) which could result in insecure " +
                    "network traffic caused by trusting arbitrary hostnames in TLS/SSL certificates " +
                    "presented by peers.",
                    Category.SECURITY,
                    6,
                    Severity.WARNING,
                    IMPLEMENTATION);

    @Override
    public List<String> applicableSuperClasses() {
        return Arrays.asList("javax.net.ssl.HostnameVerifier");
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        for (UMethod method : declaration.getMethods()) {
            if ("verify".equals(method.getName())) {
                checkVerifyMethod(context, method);
            }
        }
    }

    private void checkVerifyMethod(@NonNull JavaContext context, @NonNull UMethod method) {
        UExpression body = method.getUastBody();
        if (body == null) {
            return;
        }

        // Handle expression-bodied methods/lambdas: e.g., (hostname, session) -> true
        if (isTrueLiteral(body)) {
            context.report(ISSUE, method, context.getLocation(method),
                    "Insecure HostnameVerifier: verify() always returns true");
            return;
        }

        final List<UReturnExpression> returns = new ArrayList<>();
        body.accept(new AbstractUastVisitor() {
            @Override
            public boolean visitReturnExpression(@NonNull UReturnExpression node) {
                returns.add(node);
                return true;
            }
        });

        if (returns.isEmpty()) {
            return;
        }

        boolean allTrue = true;
        for (UReturnExpression ret : returns) {
            UExpression value = ret.getReturnExpression();
            if (value == null || !isTrueLiteral(value)) {
                allTrue = false;
                break;
            }
        }

        if (allTrue) {
            context.report(ISSUE, method, context.getLocation(method),
                    "Insecure HostnameVerifier: verify() always returns true");
        }
    }

    private boolean isTrueLiteral(@NonNull UExpression expr) {
        if (expr instanceof ULiteralExpression) {
            Object value = ((ULiteralExpression) expr).getValue();
            return Boolean.TRUE.equals(value);
        }
        return false;
    }
}