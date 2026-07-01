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
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UObjectLiteralExpression;
import org.jetbrains.uast.UReferenceExpression;
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
                context.getLocation(node),
                "Using `AllowAllHostnameVerifier` is insecure because it trusts all hostnames, making TLS/SSL vulnerable to man-in-the-middle attacks.");
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
        if (isAllowAllVerifier(arg)) {
            context.report(
                    ISSUE,
                    context.getLocation(arg),
                    "Using an allow-all `HostnameVerifier` is insecure because it trusts all hostnames, making TLS/SSL vulnerable to man-in-the-middle attacks.");
        }
    }

    private boolean isAllowAllVerifier(@NonNull UExpression arg) {
        if (arg instanceof UReferenceExpression) {
            String name = ((UReferenceExpression) arg).getResolvedName();
            if ("ALLOW_ALL_HOSTNAME_VERIFIER".equals(name)) {
                return true;
            }
        }
        if (arg instanceof ULambdaExpression) {
            return returnsTrue(((ULambdaExpression) arg).getBody());
        }
        if (arg instanceof UObjectLiteralExpression) {
            UObjectLiteralExpression literal = (UObjectLiteralExpression) arg;
            for (UMethod m : literal.getMethods()) {
                if ("verify".equals(m.getName())) {
                    UExpression body = m.getUastBody();
                    return body != null && returnsTrue(body);
                }
            }
        }
        return false;
    }

    private boolean returnsTrue(@NonNull UExpression expr) {
        if (expr instanceof UReturnExpression) {
            UExpression ret = ((UReturnExpression) expr).getReturnExpression();
            return ret != null && returnsTrue(ret);
        }
        if (expr instanceof UBlockExpression) {
            List<UExpression> stmts = ((UBlockExpression) expr).getExpressions();
            if (stmts.size() == 1) {
                return returnsTrue(stmts.get(0));
            }
            return false;
        }
        if (expr instanceof ULiteralExpression) {
            return Boolean.TRUE.equals(((ULiteralExpression) expr).getValue());
        }
        return false;
    }
}