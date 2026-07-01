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
import org.jetbrains.uast.UBlockExpression;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.ULambdaExpression;
import org.jetbrains.uast.ULiteralExpression;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UObjectLiteralExpression;
import org.jetbrains.uast.UReturnExpression;

import java.util.Arrays;
import java.util.List;

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
        return Arrays.asList(
                "org.apache.http.conn.ssl.AllowAllHostnameVerifier",
                "org.apache.http.conn.ssl.NoopHostnameVerifier"
        );
    }

    @Override
    public void visitConstructor(
            @NonNull JavaContext context,
            @NonNull UCallExpression node,
            @NonNull PsiMethod constructor) {
        context.report(ISSUE, context.getLocation(node),
                "Using an insecure hostname verifier that trusts all hostnames");
    }

    @Override
    public List<String> getApplicableMethodNames() {
        return Arrays.asList("setHostnameVerifier", "setDefaultHostnameVerifier");
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
        if (isInsecureVerifier(arg)) {
            context.report(ISSUE, context.getLocation(arg),
                    "Insecure HostnameVerifier implementation: verify() always returns true");
        }
    }

    private boolean isInsecureVerifier(UExpression arg) {
        if (arg instanceof ULambdaExpression) {
            return isAlwaysTrue(((ULambdaExpression) arg).getBody());
        } else if (arg instanceof UObjectLiteralExpression) {
            UObjectLiteralExpression literal = (UObjectLiteralExpression) arg;
            for (UMethod m : literal.getMethods()) {
                if ("verify".equals(m.getName())) {
                    return isAlwaysTrue(m.getUastBody());
                }
            }
        } else if (arg instanceof UClass) {
            UClass uClass = (UClass) arg;
            for (UMethod m : uClass.getMethods()) {
                if ("verify".equals(m.getName())) {
                    return isAlwaysTrue(m.getUastBody());
                }
            }
        }
        return false;
    }

    private boolean isAlwaysTrue(UExpression expr) {
        if (expr == null) {
            return false;
        }
        if (expr instanceof UBlockExpression) {
            List<UExpression> stmts = ((UBlockExpression) expr).getExpressions();
            for (int i = stmts.size() - 1; i >= 0; i--) {
                UExpression stmt = stmts.get(i);
                if (stmt instanceof UReturnExpression) {
                    return isAlwaysTrue(((UReturnExpression) stmt).getReturnExpression());
                }
            }
            return false;
        }
        if (expr instanceof ULiteralExpression) {
            return Boolean.TRUE.equals(((ULiteralExpression) expr).getValue());
        }
        return false;
    }
}