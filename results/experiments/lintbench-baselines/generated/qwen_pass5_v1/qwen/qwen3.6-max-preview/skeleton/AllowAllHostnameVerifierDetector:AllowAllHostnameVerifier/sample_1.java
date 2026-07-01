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
import com.intellij.psi.PsiMethod;
import java.util.Collections;
import java.util.List;
import org.jetbrains.uast.UBlockExpression;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.ULambdaExpression;
import org.jetbrains.uast.ULiteralExpression;
import org.jetbrains.uast.UReturnExpression;

public class AllowAllHostnameVerifierDetector extends Detector implements SourceCodeScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(AllowAllHostnameVerifierDetector.class, Scope.JAVA_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "AllowAllHostnameVerifier",
                    "Insecure `HostnameVerifier`",
                    "This check looks for use of HostnameVerifier implementations whose `verify` method always returns true (thus trusting any hostname) which could result in insecure network traffic caused by trusting arbitrary hostnames in TLS/SSL certificates presented by peers.",
                    Category.SECURITY,
                    6,
                    Severity.WARNING,
                    IMPLEMENTATION);

    @Override
    public List<String> getApplicableConstructorTypes() {
        return Collections.singletonList("org.apache.http.conn.ssl.AllowAllHostnameVerifier");
    }

    @Override
    public void visitConstructor(
            @NonNull JavaContext context,
            @NonNull UCallExpression node,
            @NonNull PsiMethod constructor) {
        context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Using `AllowAllHostnameVerifier` is insecure and defeats the purpose of hostname verification in TLS/SSL.");
    }

    @Override
    public List<String> getApplicableMethodNames() {
        return Collections.singletonList("setHostnameVerifier");
    }

    @Override
    public void visitMethodCall(
            @NonNull JavaContext context,
            @NonNull UCallExpression node,
            @NonNull PsiMethod method) {
        List<UExpression> args = node.getValueArguments();
        if (args.isEmpty()) {
            return;
        }

        UExpression arg = args.get(0);
        if (arg instanceof ULambdaExpression) {
            ULambdaExpression lambda = (ULambdaExpression) arg;
            if (isAlwaysTrue(lambda.getBody())) {
                context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        "HostnameVerifier that always returns true is insecure and defeats TLS/SSL hostname verification.");
            }
        }
    }

    private boolean isAlwaysTrue(UExpression expr) {
        if (expr == null) {
            return false;
        }
        if (expr instanceof ULiteralExpression) {
            return Boolean.TRUE.equals(((ULiteralExpression) expr).getValue());
        }
        if (expr instanceof UReturnExpression) {
            return isAlwaysTrue(((UReturnExpression) expr).getReturnExpression());
        }
        if (expr instanceof UBlockExpression) {
            List<UExpression> statements = ((UBlockExpression) expr).getExpressions();
            if (statements.size() == 1) {
                return isAlwaysTrue(statements.get(0));
            }
        }
        return false;
    }
}