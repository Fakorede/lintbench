package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import java.util.Collections;
import java.util.List;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.UastUtils;

public class AllowAllHostnameVerifierDetector extends Detector implements SourceCodeScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(AllowAllHostnameVerifierDetector.class, Scope.JAVA_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                            "AllowAllHostnameVerifier",
                            "Insecure HostnameVerifier",
                            "This check looks for use of HostnameVerifier implementations "
                                    + "whose `verify` method always returns true (thus trusting any "
                                    + "hostname) which could result in insecure network traffic "
                                    + "caused by trusting arbitrary hostnames in TLS/SSL "
                                    + "certificates presented by peers.",
                            Category.SECURITY,
                            6,
                            Severity.WARNING,
                            IMPLEMENTATION)
                    .setAndroidSpecific(true)
                    .setMoreInfo("https://goo.gle/AllowAllHostnameVerifier");

    public AllowAllHostnameVerifierDetector() {}

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
                "Using the `AllowAllHostnameVerifier` class is insecure because it "
                        + "allows any TLS/SSL certificate to be accepted, "
                        + "which makes the network connection vulnerable to "
                        + "man-in-the-middle attacks.");
    }

    @Override
    public List<String> getApplicableMethodNames() {
        return java.util.Arrays.asList("setHostnameVerifier", "setDefaultHostnameVerifier");
    }

    @Override
    public void visitMethodCall(
            JavaContext context,
            UCallExpression node,
            PsiMethod method) {
        List<UExpression> args = node.getValueArguments();
        if (args.isEmpty()) {
            return;
        }
        UExpression argument = args.get(0);
        PsiElement resolved = UastUtils.tryResolve(argument);
        if (resolved instanceof PsiField) {
            PsiField field = (PsiField) resolved;
            if ("ALLOW_ALL_HOSTNAME_VERIFIER".equals(field.getName())) {
                PsiClass containingClass = field.getContainingClass();
                if (containingClass != null && "org.apache.http.conn.ssl.SSLSocketFactory".equals(containingClass.getQualifiedName())) {
                    context.report(
                            ISSUE,
                            node,
                            context.getLocation(node),
                            "Using `ALLOW_ALL_HOSTNAME_VERIFIER` is insecure because it "
                                    + "allows any TLS/SSL certificate to be accepted, "
                                    + "which makes the network connection vulnerable to "
                                    + "man-in-the-middle attacks.");
                }
            }
        }
    }
}