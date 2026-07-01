package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.intellij.psi.PsiMethod;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UReturnExpression;
import org.jetbrains.uast.UastLiteralUtils;
import org.jetbrains.uast.visitor.AbstractUastVisitor;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class AllowAllHostnameVerifierDetector extends Detector implements Detector.UastScanner {

    public static final Issue ISSUE = Issue.create(
            "AllowAllHostnameVerifier",
            "Insecure `HostnameVerifier`",
            "This check looks for use of HostnameVerifier implementations " +
            "whose `verify` method always returns true (thus trusting any hostname) " +
            "which could result in insecure network traffic caused by trusting arbitrary " +
            "hostnames in TLS/SSL certificates presented by peers.",
            Category.SECURITY,
            6,
            Severity.WARNING,
            new Implementation(
                    AllowAllHostnameVerifierDetector.class,
                    Scope.JAVA_FILE_SCOPE
            )
    ).addMoreInfo("https://goo.gle/AllowAllHostnameVerifier");

    private static final String HOSTNAME_VERIFIER = "javax.net.ssl.HostnameVerifier";
    private static final String ALLOW_ALL_HOSTNAME_VERIFIER =
            "org.apache.http.conn.ssl.AllowAllHostnameVerifier";
    private static final String SSL_SOCKET_FACTORY = "org.apache.http.conn.ssl.SSLSocketFactory";
    private static final String ALLOW_ALL_HOSTNAME_VERIFIER_FIELD = "ALLOW_ALL_HOSTNAME_VERIFIER";
    private static final String STRICT_HOSTNAME_VERIFIER = "STRICT_HOSTNAME_VERIFIER";

    @Override
    public List<String> applicableConstructorTypes() {
        return Collections.singletonList(ALLOW_ALL_HOSTNAME_VERIFIER);
    }

    @Override
    public void visitConstructor(JavaContext context, UCallExpression call, PsiMethod method) {
        context.report(ISSUE, call, context.getLocation(call),
                "Using the `AllowAllHostnameVerifier` HostnameVerifier is unsafe " +
                "because it always returns true, which could cause insecure network " +
                "traffic due to trusting TLS/SSL server certificates for wrong hostnames.");
    }

    @Override
    public List<String> getApplicableMethodNames() {
        return Arrays.asList("setHostnameVerifier", "setDefaultHostnameVerifier");
    }

    @Override
    public void visitMethodCall(JavaContext context, UCallExpression call, PsiMethod method) {
        List<org.jetbrains.uast.UExpression> args = call.getValueArguments();
        if (args.isEmpty()) {
            return;
        }
        org.jetbrains.uast.UExpression arg = args.get(0);
        String argText = arg.asSourceString();
        if (argText != null && (argText.contains("ALLOW_ALL_HOSTNAME_VERIFIER") ||
                argText.contains("AllowAllHostnameVerifier"))) {
            context.report(ISSUE, call, context.getLocation(call),
                    "Using the `AllowAllHostnameVerifier` HostnameVerifier is unsafe " +
                    "because it always returns true, which could cause insecure network " +
                    "traffic due to trusting TLS/SSL server certificates for wrong hostnames.");
        }
    }

    @Override
    public List<Class<? extends org.jetbrains.uast.UElement>> getApplicableUastTypes() {
        return Collections.singletonList(UClass.class);
    }

    @Override
    public org.jetbrains.uast.visitor.UastVisitor createUastVisitor(JavaContext context) {
        return new HostnameVerifierVisitor(context);
    }

    private static class HostnameVerifierVisitor extends AbstractUastVisitor {
        private final JavaContext mContext;

        HostnameVerifierVisitor(JavaContext context) {
            mContext = context;
        }

        @Override
        public boolean visitClass(UClass node) {
            // Check if this class implements HostnameVerifier
            boolean implementsHostnameVerifier = false;
            for (String iface : node.getSuperClassType() != null ?
                    getInterfaceNames(node) : getInterfaceNames(node)) {
                if (HOSTNAME_VERIFIER.equals(iface)) {
                    implementsHostnameVerifier = true;
                    break;
                }
            }

            if (!implementsHostnameVerifier) {
                return false;
            }

            // Look for the verify method
            for (UMethod method : node.getMethods()) {
                if ("verify".equals(method.getName())) {
                    PsiMethod psiMethod = method.getJavaPsi();
                    if (psiMethod.getParameterList().getParametersCount() == 2) {
                        // Check if the method always returns true
                        if (alwaysReturnsTrue(method)) {
                            mContext.report(ISSUE, method,
                                    mContext.getLocation(method),
                                    "This `HostnameVerifier` implementation is unsafe because " +
                                    "its `verify` method always returns `true`, which could " +
                                    "cause insecure network traffic due to trusting TLS/SSL " +
                                    "server certificates for wrong hostnames.");
                        }
                    }
                }
            }

            return false;
        }

        private List<String> getInterfaceNames(UClass node) {
            java.util.List<String> names = new java.util.ArrayList<>();
            for (com.intellij.psi.PsiClassType type : node.getImplementsListTypes()) {
                names.add(type.getCanonicalText());
            }
            return names;
        }

        private boolean alwaysReturnsTrue(UMethod method) {
            org.jetbrains.uast.UBlockExpression body = method.getUastBody();
            if (body == null) {
                return false;
            }

            List<org.jetbrains.uast.UExpression> expressions = body.getExpressions();
            if (expressions == null || expressions.isEmpty()) {
                return false;
            }

            // Simple case: single return true statement
            if (expressions.size() == 1) {
                org.jetbrains.uast.UExpression expr = expressions.get(0);
                if (expr instanceof UReturnExpression) {
                    UReturnExpression ret = (UReturnExpression) expr;
                    org.jetbrains.uast.UExpression retVal = ret.getReturnExpression();
                    if (retVal != null && UastLiteralUtils.isTrue(retVal)) {
                        return true;
                    }
                }
            }

            // Check using visitor: if all return statements return true
            AlwaysReturnsTrueVisitor visitor = new AlwaysReturnsTrueVisitor();
            method.accept(visitor);
            return visitor.alwaysReturnsTrue();
        }
    }

    private static class AlwaysReturnsTrueVisitor extends AbstractUastVisitor {
        private boolean mHasReturn = false;
        private boolean mHasNonTrueReturn = false;

        @Override
        public boolean visitReturnExpression(UReturnExpression node) {
            mHasReturn = true;
            org.jetbrains.uast.UExpression retVal = node.getReturnExpression();
            if (retVal == null || !UastLiteralUtils.isTrue(retVal)) {
                mHasNonTrueReturn = true;
            }
            return false;
        }

        boolean alwaysReturnsTrue() {
            return mHasReturn && !mHasNonTrueReturn;
        }
    }
}