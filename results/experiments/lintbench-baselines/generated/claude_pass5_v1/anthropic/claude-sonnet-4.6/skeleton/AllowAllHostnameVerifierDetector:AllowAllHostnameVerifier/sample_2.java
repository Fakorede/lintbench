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
import org.jetbrains.uast.UCallExpression;

public class AllowAllHostnameVerifierDetector extends Detector implements SourceCodeScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(AllowAllHostnameVerifierDetector.class, Scope.JAVA_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "AllowAllHostnameVerifier",
                    "Insecure `HostnameVerifier`",
                    "This check looks for use of HostnameVerifier implementations whose `verify` "
                            + "method always returns true (thus trusting any hostname) which could "
                            + "result in insecure network traffic caused by trusting arbitrary "
                            + "hostnames in TLS/SSL certificates presented by peers.",
                    Category.SECURITY,
                    6,
                    Severity.WARNING,
                    IMPLEMENTATION)
                    .addMoreInfo("https://goo.gle/AllowAllHostnameVerifier");

    private static final String ALLOW_ALL_HOSTNAME_VERIFIER =
            "org.apache.http.conn.ssl.AllowAllHostnameVerifier";
    private static final String SSL_SOCKET_FACTORY = "org.apache.http.conn.ssl.SSLSocketFactory";
    private static final String SET_HOSTNAME_VERIFIER = "setHostnameVerifier";
    private static final String ALLOW_ALL_HOSTNAME_VERIFIER_FIELD = "ALLOW_ALL_HOSTNAME_VERIFIER";

    @Override
    public List<String> getApplicableConstructorTypes() {
        return Collections.singletonList(ALLOW_ALL_HOSTNAME_VERIFIER);
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
                "Using `AllowAllHostnameVerifier` is unsafe because it always returns true, "
                        + "which could result in insecure network traffic due to trusting "
                        + "arbitrary hostnames in TLS/SSL certificates presented by peers");
    }

    @Override
    public List<String> getApplicableMethodNames() {
        return Arrays.asList(SET_HOSTNAME_VERIFIER, "setDefaultHostnameVerifier");
    }

    @Override
    public void visitMethodCall(
            @NonNull JavaContext context,
            @NonNull UCallExpression node,
            @NonNull PsiMethod method) {
        List<org.jetbrains.uast.UExpression> arguments = node.getValueArguments();
        if (arguments.isEmpty()) {
            return;
        }

        org.jetbrains.uast.UExpression argument = arguments.get(0);
        String argumentType = null;

        if (argument instanceof org.jetbrains.uast.UQualifiedReferenceExpression) {
            org.jetbrains.uast.UQualifiedReferenceExpression ref =
                    (org.jetbrains.uast.UQualifiedReferenceExpression) argument;
            String selector = ref.getSelector().toString();
            if (ALLOW_ALL_HOSTNAME_VERIFIER_FIELD.equals(selector)) {
                argumentType = ALLOW_ALL_HOSTNAME_VERIFIER_FIELD;
            }
        }

        if (argumentType == null) {
            com.intellij.psi.PsiType type = argument.getExpressionType();
            if (type != null) {
                String typeName = type.getCanonicalText();
                if (ALLOW_ALL_HOSTNAME_VERIFIER.equals(typeName)) {
                    argumentType = typeName;
                }
            }
        }

        if (argumentType == null) {
            // Check for SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER reference
            String argumentStr = argument.toString();
            if (argumentStr != null && argumentStr.contains(ALLOW_ALL_HOSTNAME_VERIFIER_FIELD)) {
                argumentType = ALLOW_ALL_HOSTNAME_VERIFIER_FIELD;
            }
        }

        if (argumentType != null) {
            context.report(
                    ISSUE,
                    node,
                    context.getLocation(argument),
                    "Using `AllowAllHostnameVerifier` is unsafe because it always returns true, "
                            + "which could result in insecure network traffic due to trusting "
                            + "arbitrary hostnames in TLS/SSL certificates presented by peers");
        }
    }
}