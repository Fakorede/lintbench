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
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.ULiteralExpression;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UReturnExpression;
import org.jetbrains.uast.UastLiteralUtils;
import org.jetbrains.uast.visitor.AbstractUastVisitor;

public class AllowAllHostnameVerifierDetector extends Detector implements SourceCodeScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(AllowAllHostnameVerifierDetector.class, Scope.JAVA_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                            "AllowAllHostnameVerifier",
                            "Insecure `HostnameVerifier`",
                            "This check looks for use of `HostnameVerifier` implementations "
                                    + "whose `verify` method always returns `true` (thus trusting any hostname) "
                                    + "which could result in insecure network traffic caused by trusting arbitrary "
                                    + "hostnames in TLS/SSL certificates presented by peers.",
                            Category.SECURITY,
                            6,
                            Severity.WARNING,
                            IMPLEMENTATION)
                    .addMoreInfo("https://goo.gle/AllowAllHostnameVerifier")
                    .setAndroidSpecific(true);

    private static final String ALLOW_ALL_HOSTNAME_VERIFIER =
            "org.apache.http.conn.ssl.AllowAllHostnameVerifier";
    private static final String SSL_SOCKET_FACTORY = "org.apache.http.conn.ssl.SSLSocketFactory";
    private static final String HOSTNAME_VERIFIER = "javax.net.ssl.HostnameVerifier";

    private static final String MESSAGE =
            "Using `AllowAllHostnameVerifier` or `ALLOW_ALL_HOSTNAME_VERIFIER` is insecure "
                    + "because it always returns true, which could result in insecure network "
                    + "traffic due to trusting TLS/SSL server certificates for wrong hostnames.";

    public AllowAllHostnameVerifierDetector() {}

    // -------------------------------------------------------------------------
    // Constructor checks: new AllowAllHostnameVerifier()
    // -------------------------------------------------------------------------

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
        context.report(ISSUE, call, context.getLocation(call), MESSAGE);
    }

    // -------------------------------------------------------------------------
    // Method call checks: SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER field
    // access or setHostnameVerifier() / verify() calls
    // -------------------------------------------------------------------------

    @Nullable
    @Override
    public List<String> getApplicableMethodNames() {
        return Arrays.asList("setHostnameVerifier", "setDefaultHostnameVerifier");
    }

    @Override
    public void visitMethodCall(
            @NonNull JavaContext context,
            @NonNull UCallExpression call,
            @NonNull PsiMethod method) {
        List<UExpression> args = call.getValueArguments();
        if (args.isEmpty()) {
            return;
        }
        UExpression arg = args.get(0);
        String argText = arg.asSourceString();
        if (argText != null
                && (argText.contains("ALLOW_ALL_HOSTNAME_VERIFIER")
                        || argText.contains("AllowAllHostnameVerifier"))) {
            context.report(ISSUE, call, context.getLocation(call), MESSAGE);
        }
    }

    // -------------------------------------------------------------------------
    // Class visitor: detect anonymous / concrete HostnameVerifier whose
    // verify() always returns true
    // -------------------------------------------------------------------------

    @Nullable
    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList(HOSTNAME_VERIFIER);
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        for (PsiMethod psiMethod : declaration.getMethods()) {
            if ("verify".equals(psiMethod.getName())) {
                UMethod uMethod = context.getUastContext().getMethod(psiMethod);
                if (uMethod == null) {
                    continue;
                }
                if (alwaysReturnsTrue(uMethod)) {
                    context.report(
                            ISSUE,
                            uMethod,
                            context.getNameLocation(uMethod),
                            "This `HostnameVerifier.verify()` implementation always returns "
                                    + "`true`, which could result in insecure network traffic "
                                    + "due to trusting arbitrary hostnames in TLS/SSL certificates.");
                }
            }
        }
    }

    /**
     * Returns true if the given method body consists only of a single
     * {@code return true;} statement (a common pattern for insecure verifiers).
     */
    private static boolean alwaysReturnsTrue(@NonNull UMethod method) {
        if (method.getUastBody() == null) {
            return false;
        }
        ReturnsTrueVisitor visitor = new ReturnsTrueVisitor();
        method.getUastBody().accept(visitor);
        return visitor.alwaysReturnsTrue && !visitor.hasOtherReturn;
    }

    private static class ReturnsTrueVisitor extends AbstractUastVisitor {
        boolean alwaysReturnsTrue = false;
        boolean hasOtherReturn = false;

        @Override
        public boolean visitReturnExpression(@NonNull UReturnExpression node) {
            UExpression returnValue = node.getReturnExpression();
            if (returnValue instanceof ULiteralExpression) {
                Object value = ((ULiteralExpression) returnValue).getValue();
                if (Boolean.TRUE.equals(value)) {
                    alwaysReturnsTrue = true;
                    return super.visitReturnExpression(node);
                }
            }
            hasOtherReturn = true;
            return super.visitReturnExpression(node);
        }
    }
}