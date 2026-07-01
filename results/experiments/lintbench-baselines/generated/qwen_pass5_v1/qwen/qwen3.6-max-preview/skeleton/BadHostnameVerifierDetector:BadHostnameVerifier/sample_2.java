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
import org.jetbrains.uast.UBlockExpression;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.ULiteralExpression;
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
        return Collections.singletonList("javax.net.ssl.HostnameVerifier");
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        for (UMethod method : declaration.getMethods()) {
            if ("verify".equals(method.getName()) && method.getParameterList().getParametersCount() == 2) {
                UExpression body = method.getUastBody();
                if (body != null) {
                    if (body instanceof UBlockExpression) {
                        for (UExpression stmt : ((UBlockExpression) body).getExpressions()) {
                            dispatch(context, stmt);
                        }
                    } else {
                        dispatch(context, body);
                    }
                }
            }
        }
    }

    private void dispatch(@NonNull JavaContext context, @NonNull UExpression expr) {
        if (expr instanceof UReturnExpression) {
            visitReturnExpression(context, (UReturnExpression) expr);
        } else if (expr instanceof UThrowExpression) {
            visitThrowExpression(context, (UThrowExpression) expr);
        } else if (expr instanceof UCallExpression) {
            visitCallExpression(context, (UCallExpression) expr);
        }
    }

    public void visitThrowExpression(@NonNull JavaContext context, @NonNull UThrowExpression node) {
        // Throwing an exception does not constitute an insecure verifier
    }

    public void visitCallExpression(@NonNull JavaContext context, @NonNull UCallExpression node) {
        // Method calls are ignored for this specific check
    }

    public void visitReturnExpression(@NonNull JavaContext context, @NonNull UReturnExpression node) {
        UExpression returnExpr = node.getReturnExpression();
        if (returnExpr instanceof ULiteralExpression) {
            Object value = ((ULiteralExpression) returnExpr).getValue();
            if (Boolean.TRUE.equals(value)) {
                context.report(ISSUE, context.getLocation(node),
                        "Insecure HostnameVerifier: verify() always returns true");
            }
        }
    }
}