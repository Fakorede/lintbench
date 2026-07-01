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
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import java.util.Collections;
import java.util.List;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.UReferenceExpression;

public class AllowAllHostnameVerifierDetector extends Detector implements SourceCodeScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(AllowAllHostnameVerifierDetector.class, Scope.JAVA_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "AllowAllHostnameVerifier",
                    "Insecure `HostnameVerifier`",
                    "The `AllowAllHostnameVerifier` class, and the "
                            + "`ALLOW_ALL_HOSTNAME_VERIFIER` constant, trust any "
                            + "certificates and do not verify the host name. This makes "
                            + "the connection vulnerable to Man-In-The-Middle attacks.",
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
                "Using the `AllowAllHostnameVerifier` class, which tolerates all "
                        + "hostnames, is insecure because it exempts the host from verification");
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
        List<UExpression> arguments = node.getValueArguments();
        if (arguments.size() == 1) {
            UExpression argument = arguments.get(0);
            if (isAllowAllHostnameVerifier(argument)) {
                context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        "Using `ALLOW_ALL_HOSTNAME_VERIFIER` is insecure because it "
                                + "exempts the host from verification");
            }
        }
    }

    private static boolean isAllowAllHostnameVerifier(UExpression argument) {
        if (argument instanceof UReferenceExpression) {
            PsiElement resolved = ((UReferenceExpression) argument).resolve();
            if (resolved instanceof PsiField) {
                PsiField field = (PsiField) resolved;
                if ("ALLOW_ALL_HOSTNAME_VERIFIER".equals(field.getName())) {
                    return true;
                }
            }
        }
        return false;
    }
}