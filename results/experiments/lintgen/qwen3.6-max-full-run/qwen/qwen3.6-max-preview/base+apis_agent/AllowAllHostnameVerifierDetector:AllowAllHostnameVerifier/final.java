package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.UBlockExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.ULambdaExpression;
import org.jetbrains.uast.ULiteralExpression;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UParenthesizedExpression;
import org.jetbrains.uast.UReturnExpression;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

public class AllowAllHostnameVerifierDetector extends Detector implements SourceCodeScanner {

    public static final Issue ISSUE = Issue.create(
        "AllowAllHostnameVerifier",
        "Insecure HostnameVerifier",
        "This check looks for use of HostnameVerifier implementations whose `verify` method always returns true (thus trusting any hostname) which could result in insecure network traffic caused by trusting arbitrary hostnames in TLS/SSL certificates presented by peers.",
        Category.SECURITY,
        6,
        Severity.WARNING,
        new Implementation(AllowAllHostnameVerifierDetector.class, EnumSet.of(Scope.JAVA_FILE))
    );

    @Nullable
    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList("javax.net.ssl.HostnameVerifier");
    }

    @Override
    public void visitClass(@NotNull JavaContext context, @NotNull UClass declaration) {
        for (UMethod method : declaration.getMethods()) {
            if ("verify".equals(method.getName()) && method.getUastParameters().size() == 2) {
                UExpression body = method.getUastBody();
                if (alwaysReturnsTrue(body)) {
                    context.report(ISSUE, method, context.getLocation(method),
                        "Insecure `HostnameVerifier`: `verify()` always returns `true`");
                }
            }
        }
    }

    @Override
    public void visitClass(@NotNull JavaContext context, @NotNull ULambdaExpression lambda) {
        if (alwaysReturnsTrue(lambda.getBody())) {
            context.report(ISSUE, lambda, context.getLocation(lambda),
                "Insecure `HostnameVerifier`: `verify()` always returns `true`");
        }
    }

    private boolean alwaysReturnsTrue(@Nullable UExpression body) {
        if (body == null) return false;

        if (body instanceof UBlockExpression) {
            List<UExpression> statements = ((UBlockExpression) body).getExpressions();
            if (statements.size() == 1) {
                UExpression stmt = statements.get(0);
                if (stmt instanceof UReturnExpression) {
                    return isTrueLiteral(((UReturnExpression) stmt).getReturnExpression());
                }
            }
        } else if (body instanceof UReturnExpression) {
            return isTrueLiteral(((UReturnExpression) body).getReturnExpression());
        } else {
            return isTrueLiteral(body);
        }
        return false;
    }

    private boolean isTrueLiteral(@Nullable UExpression expr) {
        if (expr == null) return false;
        while (expr instanceof UParenthesizedExpression) {
            expr = ((UParenthesizedExpression) expr).getExpression();
        }
        if (expr instanceof ULiteralExpression) {
            Object value = ((ULiteralExpression) expr).getValue();
            return Boolean.TRUE.equals(value);
        }
        return false;
    }
}