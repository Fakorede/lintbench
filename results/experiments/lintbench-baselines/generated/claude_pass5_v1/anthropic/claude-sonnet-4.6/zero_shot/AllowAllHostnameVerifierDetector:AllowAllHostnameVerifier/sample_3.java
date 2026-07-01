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
import org.jetbrains.uast.UExpression;
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

    private static final String HOSTNAME_VERIFIER_INTERFACE = "javax.net.ssl.HostnameVerifier";
    private static final String ALLOW_ALL_HOSTNAME_VERIFIER =
            "org.apache.http.conn.ssl.AllowAllHostnameVerifier";
    private static final String ALLOW_ALL_HOSTNAME_VERIFIER2 =
            "org.apache.http.conn.ssl.SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER";
    private static final String SSL_SOCKET_FACTORY =
            "org.apache.http.conn.ssl.SSLSocketFactory";

    @Override
    public List<String> getApplicableMethodNames() {
        return Arrays.asList("setHostnameVerifier", "setDefaultHostnameVerifier");
    }

    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList(HOSTNAME_VERIFIER_INTERFACE);
    }

    @Override
    public void visitClass(JavaContext context, UClass declaration) {
        // Check if this class implements HostnameVerifier and has a verify method
        // that always returns true
        for (UMethod method : declaration.getMethods()) {
            if (isVerifyMethod(method)) {
                if (alwaysReturnsTrue(method)) {
                    context.report(
                            ISSUE,
                            method,
                            context.getLocation(method),
                            "This `HostnameVerifier` always returns `true`, which means it " +
                            "trusts any hostname. This could be a security risk."
                    );
                }
            }
        }
    }

    @Override
    public void visitMethodCall(JavaContext context, UCallExpression call, PsiMethod method) {
        List<UExpression> arguments = call.getValueArguments();
        if (arguments.isEmpty()) {
            return;
        }

        // Check if any argument is an AllowAllHostnameVerifier instance or
        // a reference to ALLOW_ALL_HOSTNAME_VERIFIER field
        for (UExpression argument : arguments) {
            String resolvedType = null;
            if (argument.getExpressionType() != null) {
                resolvedType = argument.getExpressionType().getCanonicalText();
            }

            if (resolvedType != null) {
                if (resolvedType.equals(ALLOW_ALL_HOSTNAME_VERIFIER) ||
                        resolvedType.contains("AllowAllHostnameVerifier")) {
                    context.report(
                            ISSUE,
                            call,
                            context.getLocation(call),
                            "Using `AllowAllHostnameVerifier` is insecure because it always " +
                            "returns `true`, trusting any hostname."
                    );
                    return;
                }
            }

            // Check for ALLOW_ALL_HOSTNAME_VERIFIER field reference
            String argumentText = argument.asSourceString();
            if (argumentText != null && argumentText.contains("ALLOW_ALL_HOSTNAME_VERIFIER")) {
                context.report(
                        ISSUE,
                        call,
                        context.getLocation(call),
                        "Using `ALLOW_ALL_HOSTNAME_VERIFIER` is insecure because it always " +
                        "returns `true`, trusting any hostname."
                );
                return;
            }
        }
    }

    private boolean isVerifyMethod(UMethod method) {
        if (!"verify".equals(method.getName())) {
            return false;
        }
        PsiMethod psiMethod = method.getJavaPsi();
        if (psiMethod.getParameterList().getParametersCount() != 2) {
            return false;
        }
        return true;
    }

    private boolean alwaysReturnsTrue(UMethod method) {
        if (method.getUastBody() == null) {
            return false;
        }

        AlwaysReturnsTrueVisitor visitor = new AlwaysReturnsTrueVisitor();
        method.getUastBody().accept(visitor);
        return visitor.alwaysReturnsTrue && visitor.hasReturnStatement;
    }

    private static class AlwaysReturnsTrueVisitor extends AbstractUastVisitor {
        boolean alwaysReturnsTrue = true;
        boolean hasReturnStatement = false;

        @Override
        public boolean visitReturnExpression(UReturnExpression node) {
            hasReturnStatement = true;
            UExpression returnValue = node.getReturnExpression();
            if (returnValue == null) {
                alwaysReturnsTrue = false;
            } else {
                Object value = returnValue.evaluate();
                if (!(value instanceof Boolean) || !(Boolean) value) {
                    alwaysReturnsTrue = false;
                }
            }
            return super.visitReturnExpression(node);
        }
    }
}