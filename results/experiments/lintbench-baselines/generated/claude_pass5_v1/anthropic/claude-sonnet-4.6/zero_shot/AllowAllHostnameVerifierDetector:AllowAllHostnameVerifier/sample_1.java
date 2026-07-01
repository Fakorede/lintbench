package com.android.tools.lint.checks;

import com.android.tools.lint.client.api.UElementHandler;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UReturnExpression;
import org.jetbrains.uast.UastLiteralUtils;
import org.jetbrains.uast.visitor.AbstractUastVisitor;

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
    private static final String SSL_SOCKET_FACTORY =
            "org.apache.http.conn.ssl.SSLSocketFactory";

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.singletonList(UClass.class);
    }

    @Override
    public UElementHandler createUastHandler(JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitClass(UClass node) {
                // Check if the class implements HostnameVerifier
                if (!implementsHostnameVerifier(context, node)) {
                    return;
                }

                // Find the verify method
                for (UMethod method : node.getMethods()) {
                    if (isVerifyMethod(method)) {
                        // Check if the method always returns true
                        if (alwaysReturnsTrue(method)) {
                            context.report(
                                    ISSUE,
                                    method,
                                    context.getLocation(method),
                                    "This `HostnameVerifier` implementation is insecure as it " +
                                    "always returns `true`, which could allow insecure network " +
                                    "traffic due to trusting arbitrary hostnames in TLS/SSL " +
                                    "certificates presented by peers"
                            );
                        }
                    }
                }
            }
        };
    }

    @Override
    public List<String> getApplicableMethodNames() {
        return Collections.singletonList("setHostnameVerifier");
    }

    @Override
    public void visitMethodCall(JavaContext context, UCallExpression node, PsiMethod method) {
        // Check for SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER usage
        List<UExpression> arguments = node.getValueArguments();
        if (arguments.isEmpty()) {
            return;
        }

        for (UExpression arg : arguments) {
            String argText = arg.asSourceString();
            if (argText != null && (argText.contains("ALLOW_ALL_HOSTNAME_VERIFIER") ||
                    argText.contains("AllowAllHostnameVerifier"))) {
                context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        "Using `ALLOW_ALL_HOSTNAME_VERIFIER` which is insecure as it " +
                        "always returns `true`"
                );
                return;
            }
        }
    }

    private boolean implementsHostnameVerifier(JavaContext context, UClass node) {
        // Check direct interface implementations
        for (PsiType iface : node.getPsi().getImplementsListTypes()) {
            String canonicalText = iface.getCanonicalText();
            if (HOSTNAME_VERIFIER.equals(canonicalText)) {
                return true;
            }
        }

        // Also check via type hierarchy using context's evaluator
        return context.getEvaluator().implementsInterface(
                node.getPsi(), HOSTNAME_VERIFIER, false);
    }

    private boolean isVerifyMethod(UMethod method) {
        if (!"verify".equals(method.getName())) {
            return false;
        }
        // verify(String hostname, SSLSession session)
        PsiMethod psiMethod = method.getPsi();
        if (psiMethod.getParameterList().getParametersCount() != 2) {
            return false;
        }
        PsiType returnType = psiMethod.getReturnType();
        return returnType != null && PsiType.BOOLEAN.equals(returnType);
    }

    private boolean alwaysReturnsTrue(UMethod method) {
        AlwaysReturnsTrueVisitor visitor = new AlwaysReturnsTrueVisitor();
        method.accept(visitor);
        return visitor.alwaysReturnsTrue();
    }

    private static class AlwaysReturnsTrueVisitor extends AbstractUastVisitor {
        private boolean hasReturnStatement = false;
        private boolean hasNonTrueReturn = false;

        @Override
        public boolean visitReturnExpression(UReturnExpression node) {
            hasReturnStatement = true;
            UExpression returnValue = node.getReturnExpression();
            if (returnValue == null) {
                hasNonTrueReturn = true;
            } else {
                Object value = returnValue.evaluate();
                if (!Boolean.TRUE.equals(value)) {
                    hasNonTrueReturn = true;
                }
            }
            return super.visitReturnExpression(node);
        }

        public boolean alwaysReturnsTrue() {
            return hasReturnStatement && !hasNonTrueReturn;
        }
    }
}