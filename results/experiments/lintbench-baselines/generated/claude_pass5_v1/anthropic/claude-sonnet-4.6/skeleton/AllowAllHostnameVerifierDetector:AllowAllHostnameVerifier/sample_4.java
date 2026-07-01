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
                            + "See https://goo.gle/AllowAllHostnameVerifier for more details.",
                    Category.SECURITY,
                    6,
                    Severity.WARNING,
                    IMPLEMENTATION);

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
                "Using `AllowAllHostnameVerifier` is unsafe because it always returns true, "
                        + "which could cause insecure network traffic due to trusting TLS/SSL "
                        + "server certificates for wrong hostnames");
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
        List<org.jetbrains.uast.UExpression> args = node.getValueArguments();
        if (args.isEmpty()) {
            return;
        }

        org.jetbrains.uast.UExpression arg = args.get(0);
        String argType = null;

        if (arg instanceof org.jetbrains.uast.UQualifiedReferenceExpression) {
            org.jetbrains.uast.UQualifiedReferenceExpression ref =
                    (org.jetbrains.uast.UQualifiedReferenceExpression) arg;
            String selector = ref.getSelector().asSourceString();
            String receiver = ref.getReceiver().asSourceString();
            if (ALLOW_ALL_VERIFIER_FIELD.equals(selector)
                    && (receiver.contains("SSLSocketFactory")
                            || receiver.contains("AllowAllHostnameVerifier"))) {
                reportIssue(context, node);
                return;
            }
        }

        if (arg instanceof org.jetbrains.uast.UReferenceExpression) {
            org.jetbrains.uast.UReferenceExpression ref =
                    (org.jetbrains.uast.UReferenceExpression) arg;
            com.intellij.psi.PsiElement resolved = ref.resolve();
            if (resolved instanceof com.intellij.psi.PsiField) {
                com.intellij.psi.PsiField field = (com.intellij.psi.PsiField) resolved;
                String fieldName = field.getName();
                com.intellij.psi.PsiClass containingClass = field.getContainingClass();
                if (ALLOW_ALL_VERIFIER_FIELD.equals(fieldName) && containingClass != null) {
                    String qualifiedName = containingClass.getQualifiedName();
                    if (SSL_SOCKET_FACTORY.equals(qualifiedName)
                            || ALLOW_ALL_HOSTNAME_VERIFIER.equals(qualifiedName)) {
                        reportIssue(context, node);
                        return;
                    }
                }
            }
        }

        com.intellij.psi.PsiType type = arg.getExpressionType();
        if (type != null) {
            String canonicalText = type.getCanonicalText();
            if (ALLOW_ALL_HOSTNAME_VERIFIER.equals(canonicalText)) {
                reportIssue(context, node);
            }
        }
    }

    private void reportIssue(
            @NonNull JavaContext context, @NonNull UCallExpression node) {
        context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Using `AllowAllHostnameVerifier` is unsafe because it always returns true, "
                        + "which could cause insecure network traffic due to trusting TLS/SSL "
                        + "server certificates for wrong hostnames");
    }
}