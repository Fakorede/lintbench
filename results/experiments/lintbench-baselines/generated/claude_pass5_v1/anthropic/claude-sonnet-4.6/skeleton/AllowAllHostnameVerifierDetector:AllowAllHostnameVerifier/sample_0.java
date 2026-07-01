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

    private static final String ALLOW_ALL_VERIFIER_FIELD = "ALLOW_ALL_HOSTNAME_VERIFIER";

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
                        + "network traffic due to trusting TLS/SSL server certificates for "
                        + "wrong hostnames");
    }

    @Override
    public List<String> getApplicableMethodNames() {
        return Collections.singletonList(SET_HOSTNAME_VERIFIER);
    }

    @Override
    public void visitMethodCall(
            @NonNull JavaContext context,
            @NonNull UCallExpression node,
            @NonNull PsiMethod method) {
        // Check if the method is called on SSLSocketFactory or related classes
        List<org.jetbrains.uast.UExpression> arguments = node.getValueArguments();
        if (arguments.isEmpty()) {
            return;
        }

        org.jetbrains.uast.UExpression argument = arguments.get(0);
        String argumentText = argument.asSourceString();

        // Check if the argument references ALLOW_ALL_HOSTNAME_VERIFIER field or is an instance
        if (argumentText.contains(ALLOW_ALL_VERIFIER_FIELD)
                || argumentText.contains("AllowAllHostnameVerifier")) {
            context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    "Using the `AllowAllHostnameVerifier` HostnameVerifier is unsafe "
                            + "because it always returns true, which could cause insecure "
                            + "network traffic due to trusting TLS/SSL server certificates for "
                            + "wrong hostnames");
            return;
        }

        // Check the resolved type of the argument
        com.intellij.psi.PsiType type = null;
        if (argument instanceof org.jetbrains.uast.UExpression) {
            try {
                type = argument.getExpressionType();
            } catch (Exception ignore) {
                // ignore
            }
        }

        if (type != null) {
            String typeName = type.getCanonicalText();
            if (ALLOW_ALL_HOSTNAME_VERIFIER.equals(typeName)) {
                context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        "Using the `AllowAllHostnameVerifier` HostnameVerifier is unsafe "
                                + "because it always returns true, which could cause insecure "
                                + "network traffic due to trusting TLS/SSL server certificates for "
                                + "wrong hostnames");
            }
        }
    }
}