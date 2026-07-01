package com.android.tools.lint.checks;

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
import java.util.Arrays;
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
                    "This check looks for use of HostnameVerifier implementations whose `verify` method "
                            + "always returns true (thus trusting any hostname) which could result in insecure "
                            + "network traffic caused by trusting arbitrary hostnames in TLS/SSL certificates "
                            + "presented by peers.",
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
            JavaContext context,
            UCallExpression node,
            PsiMethod constructor) {
        context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Using `AllowAllHostnameVerifier` is unsafe because it always returns true, which "
                        + "trusts any hostname and could result in insecure network traffic.");
    }

    @Override
    public List<String> getApplicableMethodNames() {
        return Arrays.asList("setHostnameVerifier", "setDefaultHostnameVerifier");
    }

    @Override
    public void visitMethodCall(
            JavaContext context,
            UCallExpression node,
            PsiMethod method) {
        for (UExpression argument : node.getValueArguments()) {
            boolean matches = false;
            if (argument instanceof UReferenceExpression) {
                PsiElement resolved = ((UReferenceExpression) argument).resolve();
                if (resolved instanceof PsiField) {
                    PsiField field = (PsiField) resolved;
                    if ("ALLOW_ALL_HOSTNAME_VERIFIER".equals(field.getName())) {
                        matches = true;
                    }
                }
            }
            if (!matches) {
                String source = argument.asSourceString();
                if (source != null && source.contains("ALLOW_ALL_HOSTNAME_VERIFIER")) {
                    matches = true;
                }
            }
            if (matches) {
                context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        "Using `ALLOW_ALL_HOSTNAME_VERIFIER` is unsafe because it always returns true, which "
                                + "trusts any hostname and could result in insecure network traffic.");
                break;
            }
        }
    }
}