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
            if ("verify".equals(method.getName())) {
                UExpression body = method.getUastBody();
                if (body != null && alwaysReturnsTrue(body)) {
                    context.report(ISSUE, method, context.getLocation(method),
                            "Insecure HostnameVerifier: verify() always returns true");
                }
            }
        }
    }

    private boolean alwaysReturnsTrue(@NonNull UExpression body) {
        if (body instanceof UBlockExpression) {
            List<UExpression> statements = ((UBlockExpression) body).getExpressions();
            if (statements.size() == 1) {
                UExpression stmt = statements.get(0);
                if (stmt instanceof UReturnExpression) {
                    UExpression returnValue = ((UReturnExpression) stmt).getReturnExpression();
                    return isTrueLiteral(returnValue);
                }
            }
        }
        return false;
    }

    private boolean isTrueLiteral(UExpression expr) {
        if (expr instanceof ULiteralExpression) {
            Object value = ((ULiteralExpression) expr).getValue();
            return Boolean.TRUE.equals(value);
        }
        return false;
    }

    public void visitThrowExpression(@NonNull UThrowExpression expression) {
        // Reserved for future visitor-based traversal if needed
    }

    public void visitCallExpression(@NonNull UCallExpression expression) {
        // Reserved for future visitor-based traversal if needed
    }

    public void visitReturnExpression(@NonNull UReturnExpression expression) {
        // Reserved for future visitor-based traversal if needed
    }
}