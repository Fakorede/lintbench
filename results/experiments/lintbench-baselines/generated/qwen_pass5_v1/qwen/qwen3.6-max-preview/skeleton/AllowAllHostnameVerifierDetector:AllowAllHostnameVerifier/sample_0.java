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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.jetbrains.uast.UAnonymousClass;
import org.jetbrains.uast.UBlockExpression;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.ULiteralExpression;
import org.jetbrains.uast.UMethod;
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
        return Arrays.asList(
                "javax.net.ssl.HostnameVerifier",
                "org.apache.http.conn.ssl.HostnameVerifier");
    }

    @Override
    public void visitConstructor(
            @NonNull JavaContext context,
            @NonNull UCallExpression node,
            @NonNull PsiMethod constructor) {
        UElement parent = node.getUastParent();
        if (!(parent instanceof UAnonymousClass)) {
            return;
        }

        UAnonymousClass anonymousClass = (UAnonymousClass) parent;
        for (UMethod method : anonymousClass.getMethods()) {
            if ("verify".equals(method.getName())) {
                if (alwaysReturnsTrue(method)) {
                    context.report(ISSUE, node, context.getLocation(node),
                            "Insecure `HostnameVerifier`: `verify` method always returns `true`");
                    return;
                }
            }
        }
    }

    @Override
    public List<String> getApplicableMethodNames() {
        return Collections.emptyList();
    }

    @Override
    public void visitMethodCall(
            @NonNull JavaContext context,
            @NonNull UCallExpression node,
            @NonNull PsiMethod method) {
        // No method calls to check for this issue
    }

    private static boolean alwaysReturnsTrue(@NonNull UMethod method) {
        UExpression body = method.getUastBody();
        if (body == null) {
            return false;
        }

        UExpression returnExpression = null;
        if (body instanceof UBlockExpression) {
            List<UExpression> statements = ((UBlockExpression) body).getExpressions();
            if (statements.size() == 1 && statements.get(0) instanceof UReturnExpression) {
                returnExpression = ((UReturnExpression) statements.get(0)).getReturnExpression();
            }
        } else if (body instanceof UReturnExpression) {
            returnExpression = ((UReturnExpression) body).getReturnExpression();
        } else {
            returnExpression = body;
        }

        if (returnExpression instanceof ULiteralExpression) {
            return Boolean.TRUE.equals(((ULiteralExpression) returnExpression).getValue());
        }
        return false;
    }
}