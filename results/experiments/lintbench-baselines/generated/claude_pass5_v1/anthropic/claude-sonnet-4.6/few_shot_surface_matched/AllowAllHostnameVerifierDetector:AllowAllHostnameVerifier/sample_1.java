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
import org.jetbrains.uast.UCallExpression;

public class AllowAllHostnameVerifierDetector extends Detector implements SourceCodeScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(AllowAllHostnameVerifierDetector.class, Scope.JAVA_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                            "AllowAllHostnameVerifier",
                            "Insecure `HostnameVerifier`",
                            "This check looks for use of HostnameVerifier implementations whose "
                                    + "`verify` method always returns true (thus trusting any hostname) "
                                    + "which could result in insecure network traffic caused by trusting "
                                    + "arbitrary hostnames in TLS/SSL certificates presented by peers.",
                            Category.SECURITY,
                            6,
                            Severity.WARNING,
                            IMPLEMENTATION)
                    .addMoreInfo("https://goo.gle/AllowAllHostnameVerifier")
                    .setAndroidSpecific(true);

    private static final String ALLOW_ALL_HOSTNAME_VERIFIER =
            "org.apache.http.conn.ssl.AllowAllHostnameVerifier";

    private static final String SSL_SOCKET_FACTORY = "org.apache.http.conn.ssl.SSLSocketFactory";

    private static final String HOSTNAME_VERIFIER_FIELD = "ALLOW_ALL_HOSTNAME_VERIFIER";

    private static final String SET_HOSTNAME_VERIFIER = "setHostnameVerifier";

    private static final String BROWSER_COMPAT_HOSTNAME_VERIFIER =
            "org.apache.http.conn.ssl.BrowserCompatHostnameVerifier";

    private static final String STRICT_HOSTNAME_VERIFIER =
            "org.apache.http.conn.ssl.StrictHostnameVerifier";

    public AllowAllHostnameVerifierDetector() {}

    @Nullable
    @Override
    public List<String> getApplicableConstructorTypes() {
        return Collections.singletonList(ALLOW_ALL_HOSTNAME_VERIFIER);
    }

    @Override
    public void visitConstructor(
            @NonNull JavaContext context,
            @NonNull UCallExpression call,
            @NonNull PsiMethod method) {
        context.report(
                ISSUE,
                call,
                context.getLocation(call),
                "Using `AllowAllHostnameVerifier` to trust all hostnames is insecure and "
                        + "should not be used in production code");
    }

    @Nullable
    @Override
    public List<String> getApplicableMethodNames() {
        return Arrays.asList(SET_HOSTNAME_VERIFIER, "setDefaultHostnameVerifier");
    }

    @Override
    public void visitMethodCall(
            @NonNull JavaContext context,
            @NonNull UCallExpression call,
            @NonNull PsiMethod method) {
        List<org.jetbrains.uast.UExpression> arguments = call.getValueArguments();
        if (arguments.isEmpty()) {
            return;
        }

        org.jetbrains.uast.UExpression argument = arguments.get(0);
        String argumentType = null;

        com.intellij.psi.PsiType type = argument.getExpressionType();
        if (type != null) {
            argumentType = type.getCanonicalText();
        }

        if (ALLOW_ALL_HOSTNAME_VERIFIER.equals(argumentType)) {
            context.report(
                    ISSUE,
                    call,
                    context.getLocation(call),
                    "Using `AllowAllHostnameVerifier` to trust all hostnames is insecure and "
                            + "should not be used in production code");
            return;
        }

        // Check for SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER field reference
        if (argument instanceof org.jetbrains.uast.UReferenceExpression) {
            org.jetbrains.uast.UReferenceExpression ref =
                    (org.jetbrains.uast.UReferenceExpression) argument;
            String resolvedName = ref.getResolvedName();
            if (HOSTNAME_VERIFIER_FIELD.equals(resolvedName)) {
                com.intellij.psi.PsiElement resolved = ref.resolve();
                if (resolved instanceof com.intellij.psi.PsiField) {
                    com.intellij.psi.PsiField field = (com.intellij.psi.PsiField) resolved;
                    com.intellij.psi.PsiClass containingClass = field.getContainingClass();
                    if (containingClass != null
                            && SSL_SOCKET_FACTORY.equals(containingClass.getQualifiedName())) {
                        context.report(
                                ISSUE,
                                call,
                                context.getLocation(call),
                                "Using `SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER` to trust "
                                        + "all hostnames is insecure and should not be used in "
                                        + "production code");
                    }
                }
            }
        }
    }
}