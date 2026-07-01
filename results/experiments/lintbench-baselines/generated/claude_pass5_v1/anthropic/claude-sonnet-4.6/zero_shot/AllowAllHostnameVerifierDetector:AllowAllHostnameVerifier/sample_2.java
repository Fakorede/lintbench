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
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.ULiteralExpression;
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
            ))
            .addMoreInfo("https://goo.gle/AllowAllHostnameVerifier");

    private static final String HOSTNAME_VERIFIER = "javax.net.ssl.HostnameVerifier";
    private static final String ALLOW_ALL_HOSTNAME_VERIFIER =
            "org.apache.http.conn.ssl.AllowAllHostnameVerifier";
    private static final String SSL_SOCKET_FACTORY = "org.apache.http.conn.ssl.SSLSocketFactory";

    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList(HOSTNAME_VERIFIER);
    }

    @Override
    public List<String> getApplicableMethodNames() {
        return Arrays.asList(
                "setHostnameVerifier",
                "setDefaultHostnameVerifier",
                "verify"
        );
    }

    @Override
    public void visitClass(JavaContext context, UClass declaration) {
        // Check if this class implements HostnameVerifier and has a verify method
        // that always returns true
        for (UMethod method : declaration.getMethods()) {
            if ("verify".equals(method.getName())) {
                PsiMethod psiMethod = method.getJavaPsi();
                String[] paramTypes = new String[psiMethod.getParameterList().getParametersCount()];
                for (int i = 0; i < paramTypes.length; i++) {
                    paramTypes[i] = psiMethod.getParameterList().getParameters()[i].getType().getCanonicalText();
                }
                // Check if it's the verify(String, SSLSession) method
                if (paramTypes.length == 2
                        && "java.lang.String".equals(paramTypes[0])
                        && "javax.net.ssl.SSLSession".equals(paramTypes[1])) {
                    if (alwaysReturnsTrue(method)) {
                        context.report(
                                ISSUE,
                                method,
                                context.getLocation(method),
                                "This `HostnameVerifier` always returns `true`, which could cause " +
                                "insecure network traffic due to trusting arbitrary hostnames in a " +
                                "TLS/SSL certificate presented by the server"
                        );
                    }
                }
            }
        }
    }

    @Override
    public void visitMethodCall(JavaContext context, UCallExpression call, PsiMethod method) {
        String methodName = call.getMethodName();
        if (methodName == null) {
            return;
        }

        if ("setHostnameVerifier".equals(methodName) || "setDefaultHostnameVerifier".equals(methodName)) {
            List<UExpression> args = call.getValueArguments();
            if (!args.isEmpty()) {
                UExpression arg = args.get(0);
                String argType = arg.getExpressionType() != null
                        ? arg.getExpressionType().getCanonicalText()
                        : null;
                if (argType != null && (
                        ALLOW_ALL_HOSTNAME_VERIFIER.equals(argType)
                        || argType.contains("AllowAllHostnameVerifier"))) {
                    context.report(
                            ISSUE,
                            call,
                            context.getLocation(call),
                            "Using `AllowAllHostnameVerifier` is unsafe because it always " +
                            "returns true, which could cause insecure network traffic"
                    );
                    return;
                }

                // Check for SSL_SOCKET_FACTORY.ALLOW_ALL_HOSTNAME_VERIFIER field reference
                String argText = arg.asSourceString();
                if (argText != null && argText.contains("ALLOW_ALL_HOSTNAME_VERIFIER")) {
                    context.report(
                            ISSUE,
                            call,
                            context.getLocation(call),
                            "Using `ALLOW_ALL_HOSTNAME_VERIFIER` is unsafe because it always " +
                            "returns true, which could cause insecure network traffic"
                    );
                }
            }
        }
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
            } else if (returnValue instanceof ULiteralExpression) {
                Object value = ((ULiteralExpression) returnValue).getValue();
                if (!Boolean.TRUE.equals(value)) {
                    alwaysReturnsTrue = false;
                }
            } else {
                // Not a literal - could be a variable or expression, be conservative
                alwaysReturnsTrue = false;
            }
            return super.visitReturnExpression(node);
        }
    }
}