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
                            + "hostnames in TLS/SSL certificates presented by peers.\n\n"
                            + "More info: https://goo.gle/AllowAllHostnameVerifier",
                    Category.SECURITY,
                    6,
                    Severity.WARNING,
                    IMPLEMENTATION);

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
                        + "network traffic due to trusting TLS/SSL server certificates for "
                        + "wrong hostnames");
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
        List<String> insecureVerifiers = Arrays.asList(
                "ALLOW_ALL_HOSTNAME_VERIFIER",
                "allowAllHostnameVerifier"
        );

        // Check if the method is called on SSLSocketFactory or HttpsURLConnection
        String containingClass = "";
        if (method.getContainingClass() != null) {
            containingClass = method.getContainingClass().getQualifiedName();
            if (containingClass == null) {
                containingClass = "";
            }
        }

        // Check arguments for insecure hostname verifiers
        List<org.jetbrains.uast.UExpression> arguments = node.getValueArguments();
        for (org.jetbrains.uast.UExpression argument : arguments) {
            String argText = argument.asSourceString();
            // Check if the argument references an allow-all hostname verifier
            for (String insecureVerifier : insecureVerifiers) {
                if (argText.contains(insecureVerifier)) {
                    context.report(
                            ISSUE,
                            node,
                            context.getLocation(node),
                            "Using the `ALLOW_ALL_HOSTNAME_VERIFIER` HostnameVerifier is "
                                    + "unsafe because it always returns true, which could cause "
                                    + "insecure network traffic due to trusting TLS/SSL server "
                                    + "certificates for wrong hostnames");
                    return;
                }
            }

            // Check resolved type of argument
            com.intellij.psi.PsiType type = argument.getExpressionType();
            if (type != null) {
                String typeName = type.getCanonicalText();
                if (ALLOW_ALL_HOSTNAME_VERIFIER.equals(typeName)) {
                    context.report(
                            ISSUE,
                            node,
                            context.getLocation(node),
                            "Using the `AllowAllHostnameVerifier` HostnameVerifier is "
                                    + "unsafe because it always returns true, which could cause "
                                    + "insecure network traffic due to trusting TLS/SSL server "
                                    + "certificates for wrong hostnames");
                    return;
                }
            }
        }
    }
}