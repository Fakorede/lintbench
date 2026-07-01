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
                            "This check looks for use of HostnameVerifier implementations whose `verify` "
                                    + "method always returns true (thus trusting any hostname) which could result "
                                    + "in insecure network traffic caused by trusting arbitrary hostnames in "
                                    + "TLS/SSL certificates presented by peers.",
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

    private static final String GET_DEFAULT = "getDefault";

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
                "Using `AllowAllHostnameVerifier` to trust all hostnames is insecure "
                        + "and should not be used in production code");
    }

    @Nullable
    @Override
    public List<String> getApplicableMethodNames() {
        return Arrays.asList(SET_HOSTNAME_VERIFIER, GET_DEFAULT);
    }

    @Override
    public void visitMethodCall(
            @NonNull JavaContext context,
            @NonNull UCallExpression call,
            @NonNull PsiMethod method) {
        String methodName = method.getName();

        if (SET_HOSTNAME_VERIFIER.equals(methodName)) {
            List<org.jetbrains.uast.UExpression> args = call.getValueArguments();
            if (!args.isEmpty()) {
                org.jetbrains.uast.UExpression arg = args.get(0);
                String argText = arg.asSourceString();
                if (argText != null
                        && (argText.contains(HOSTNAME_VERIFIER_FIELD)
                                || argText.contains("AllowAllHostnameVerifier"))) {
                    context.report(
                            ISSUE,
                            call,
                            context.getLocation(call),
                            "Using `ALLOW_ALL_HOSTNAME_VERIFIER` or `AllowAllHostnameVerifier` "
                                    + "to trust all hostnames is insecure and should not be used "
                                    + "in production code");
                    return;
                }
                com.intellij.psi.PsiType argType = arg.getExpressionType();
                if (argType != null
                        && argType.getCanonicalText().equals(ALLOW_ALL_HOSTNAME_VERIFIER)) {
                    context.report(
                            ISSUE,
                            call,
                            context.getLocation(call),
                            "Using `AllowAllHostnameVerifier` to trust all hostnames is insecure "
                                    + "and should not be used in production code");
                }
            }
        } else if (GET_DEFAULT.equals(methodName)) {
            com.intellij.psi.PsiClass containingClass = method.getContainingClass();
            if (containingClass != null
                    && SSL_SOCKET_FACTORY.equals(containingClass.getQualifiedName())) {
                context.report(
                        ISSUE,
                        call,
                        context.getLocation(call),
                        "Use of `SSLSocketFactory.getDefault()` with default hostname verification "
                                + "settings may be insecure; consider using a stricter `HostnameVerifier`");
            }
        }
    }
}