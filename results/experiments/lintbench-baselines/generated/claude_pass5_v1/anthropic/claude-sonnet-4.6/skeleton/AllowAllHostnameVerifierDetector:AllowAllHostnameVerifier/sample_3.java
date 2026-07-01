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
    private static final String BROWSER_COMPAT_HOSTNAME_VERIFIER =
            "org.apache.http.conn.ssl.BrowserCompatHostnameVerifier";

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
                "Using the `AllowAllHostnameVerifier` HostnameVerifier is unsafe "
                        + "because it always returns true, which could cause insecure "
                        + "network traffic due to trusting TLS/SSL server certificates "
                        + "for wrong hostnames");
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
        // Check if the method is called on SSLSocketFactory
        String containingClassName = method.getContainingClass() != null
                ? method.getContainingClass().getQualifiedName()
                : null;

        if (SSL_SOCKET_FACTORY.equals(containingClassName)) {
            List<org.jetbrains.uast.UExpression> arguments = node.getValueArguments();
            if (!arguments.isEmpty()) {
                org.jetbrains.uast.UExpression argument = arguments.get(0);
                String argumentType = null;
                com.intellij.psi.PsiType type = argument.getExpressionType();
                if (type != null) {
                    argumentType = type.getCanonicalText();
                }
                if (ALLOW_ALL_HOSTNAME_VERIFIER.equals(argumentType)
                        || SSL_SOCKET_FACTORY.equals(argumentType + "ALLOW_ALL_HOSTNAME_VERIFIER")) {
                    context.report(
                            ISSUE,
                            node,
                            context.getLocation(node),
                            "Using the `AllowAllHostnameVerifier` HostnameVerifier is unsafe "
                                    + "because it always returns true, which could cause insecure "
                                    + "network traffic due to trusting TLS/SSL server certificates "
                                    + "for wrong hostnames");
                    return;
                }

                // Check for field references like SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER
                if (argument instanceof org.jetbrains.uast.UReferenceExpression) {
                    org.jetbrains.uast.UReferenceExpression ref =
                            (org.jetbrains.uast.UReferenceExpression) argument;
                    String resolvedName = ref.getResolvedName();
                    if ("ALLOW_ALL_HOSTNAME_VERIFIER".equals(resolvedName)) {
                        context.report(
                                ISSUE,
                                node,
                                context.getLocation(node),
                                "Using the `ALLOW_ALL_HOSTNAME_VERIFIER` HostnameVerifier is "
                                        + "unsafe because it always returns true, which could "
                                        + "cause insecure network traffic due to trusting TLS/SSL "
                                        + "server certificates for wrong hostnames");
                    }
                }
            }
        }
    }
}