package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.intellij.psi.PsiMethod;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.jetbrains.uast.UAnonymousClass;
import org.jetbrains.uast.UBlockExpression;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.ULambdaExpression;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UQualifiedReferenceExpression;
import org.jetbrains.uast.UReturnExpression;

public class AllowAllHostnameVerifierDetector extends Detector implements SourceCodeScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(AllowAllHostnameVerifierDetector.class, Scope.JAVA_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "AllowAllHostnameVerifier",
                    "Insecure HostnameVerifier",
                    "This check looks for use of HostnameVerifier implementations whose `verify` method always returns true (thus trusting any hostname) which could result in insecure network traffic caused by trusting arbitrary hostnames in TLS/SSL certificates presented by peers.",
                    Category.SECURITY,
                    5,
                    Severity.WARNING,
                    IMPLEMENTATION);

    public AllowAllHostnameVerifierDetector() {}

    @Nullable
    @Override
    public List<String> getApplicableConstructorTypes() {
        return Collections.singletonList("org.apache.http.conn.ssl.AllowAllHostnameVerifier");
    }

    @Override
    public void visitConstructor(@NonNull JavaContext context, @NonNull UCallExpression node, @NonNull PsiMethod constructor) {
        context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Using AllowAllHostnameVerifier allows any hostname, which disables SSL hostname verification and is insecure.");
    }

    @Nullable
    @Override
    public List<String> getApplicableMethodNames() {
        return Arrays.asList("setHostnameVerifier", "setDefaultHostnameVerifier");
    }

    @Override
    public void visitMethodCall(@NonNull JavaContext context, @NonNull UCallExpression node, @NonNull PsiMethod method) {
        List<UExpression> args = node.getValueArguments();
        if (args.isEmpty()) {
            return;
        }
        UExpression arg = args.get(0);
        if (isAllowAllVerifier(arg)) {
            context.report(
                    ISSUE,
                    node,
                    context.getLocation(arg),
                    "Using an allow-all HostnameVerifier disables SSL hostname verification and is insecure.");
        }
    }

    private boolean isAllowAllVerifier(UExpression arg) {
        if (arg instanceof UQualifiedReferenceExpression) {
            UQualifiedReferenceExpression ref = (UQualifiedReferenceExpression) arg;
            String selector = ref.getSelector().asSourceString();
            String receiver = ref.getReceiver().asSourceString();
            if ("ALLOW_ALL_HOSTNAME_VERIFIER".equals(selector) && receiver.contains("SSLSocketFactory")) {
                return true;
            }
        }
        if (arg instanceof UAnonymousClass) {
            UAnonymousClass anon = (UAnonymousClass) arg;
            for (UMethod m : anon.getMethods()) {
                if ("verify".equals(m.getName())) {
                    return returnsTrue(m);
                }
            }
        }
        if (arg instanceof ULambdaExpression) {
            ULambdaExpression lambda = (ULambdaExpression) arg;
            UExpression body = lambda.getBody();
            if (body instanceof UReturnExpression) {
                return isTrueLiteral(((UReturnExpression) body).getReturnExpression());
            }
            return isTrueLiteral(body);
        }
        return false;
    }

    private boolean returnsTrue(UMethod method) {
        UExpression body = method.getUastBody();
        if (body == null) {
            return false;
        }
        if (body instanceof UReturnExpression) {
            return isTrueLiteral(((UReturnExpression) body).getReturnExpression());
        }
        if (body instanceof UBlockExpression) {
            List<UExpression> expressions = ((UBlockExpression) body).getExpressions();
            if (expressions.size() == 1 && expressions.get(0) instanceof UReturnExpression) {
                return isTrueLiteral(((UReturnExpression) expressions.get(0)).getReturnExpression());
            }
        }
        return false;
    }

    private boolean isTrueLiteral(@Nullable UExpression expr) {
        return expr != null && "true".equals(expr.asSourceString());
    }
}