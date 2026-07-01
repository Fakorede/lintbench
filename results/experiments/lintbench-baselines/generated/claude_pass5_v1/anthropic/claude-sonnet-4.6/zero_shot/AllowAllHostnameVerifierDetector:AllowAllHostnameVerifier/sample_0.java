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

    private static final String HOSTNAME_VERIFIER_INTERFACE = "javax.net.ssl.HostnameVerifier";
    private static final String ALLOW_ALL_HOSTNAME_VERIFIER =
            "org.apache.http.conn.ssl.AllowAllHostnameVerifier";
    private static final String SSL_SOCKET_FACTORY = "org.apache.http.conn.ssl.SSLSocketFactory";
    private static final String ALLOW_ALL_HOSTNAME_VERIFIER_FIELD = "ALLOW_ALL_HOSTNAME_VERIFIER";
    private static final String VERIFY_METHOD = "verify";

    @Override
    public List<String> applicableMethodNames() {
        return Arrays.asList("setHostnameVerifier", "setDefaultHostnameVerifier");
    }

    @Override
    public List<String> getApplicableUastTypes() {
        return Collections.singletonList(UClass.class);
    }

    @Override
    public void visitMethodCall(JavaContext context, UCallExpression call, PsiMethod method) {
        // Check if the argument passed is an AllowAllHostnameVerifier instance or field
        List<org.jetbrains.uast.UExpression> args = call.getValueArguments();
        if (args.isEmpty()) {
            return;
        }

        org.jetbrains.uast.UExpression arg = args.get(0);
        String argType = null;

        if (arg instanceof org.jetbrains.uast.UObjectLiteralExpression) {
            org.jetbrains.uast.UObjectLiteralExpression objLiteral =
                    (org.jetbrains.uast.UObjectLiteralExpression) arg;
            // Check if it implements HostnameVerifier and always returns true
            if (isAlwaysTrueVerifier(objLiteral.getDeclaration())) {
                context.report(ISSUE, call, context.getLocation(arg),
                        "Using `AllowAllHostnameVerifier` or similar which trusts all hostnames " +
                        "is insecure as it can allow man-in-the-middle attacks");
                return;
            }
        }

        // Check for AllowAllHostnameVerifier direct instantiation or field reference
        com.intellij.psi.PsiType resolvedType = arg.getExpressionType();
        if (resolvedType != null) {
            argType = resolvedType.getCanonicalText();
            if (ALLOW_ALL_HOSTNAME_VERIFIER.equals(argType)) {
                context.report(ISSUE, call, context.getLocation(arg),
                        "Using `AllowAllHostnameVerifier` which trusts all hostnames is insecure " +
                        "as it can allow man-in-the-middle attacks");
                return;
            }
        }

        // Check for SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER field reference
        if (arg instanceof org.jetbrains.uast.UReferenceExpression) {
            org.jetbrains.uast.UReferenceExpression ref =
                    (org.jetbrains.uast.UReferenceExpression) arg;
            com.intellij.psi.PsiElement resolved = ref.resolve();
            if (resolved instanceof com.intellij.psi.PsiField) {
                com.intellij.psi.PsiField field = (com.intellij.psi.PsiField) resolved;
                String containingClass = "";
                if (field.getContainingClass() != null) {
                    containingClass = field.getContainingClass().getQualifiedName();
                    if (containingClass == null) containingClass = "";
                }
                if (ALLOW_ALL_HOSTNAME_VERIFIER_FIELD.equals(field.getName()) &&
                        SSL_SOCKET_FACTORY.equals(containingClass)) {
                    context.report(ISSUE, call, context.getLocation(arg),
                            "Using `SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER` which trusts " +
                            "all hostnames is insecure as it can allow man-in-the-middle attacks");
                }
            }
        }
    }

    @Override
    public org.jetbrains.uast.UElementHandler createUastHandler(JavaContext context) {
        return new org.jetbrains.uast.UElementHandler() {
            @Override
            public void visitClass(UClass node) {
                // Check if the class implements HostnameVerifier
                if (!implementsHostnameVerifier(context, node)) {
                    return;
                }

                // Look for the verify method
                for (UMethod method : node.getMethods()) {
                    if (VERIFY_METHOD.equals(method.getName())) {
                        PsiMethod psiMethod = method.getJavaPsi();
                        com.intellij.psi.PsiParameterList params = psiMethod.getParameterList();
                        if (params.getParametersCount() == 2) {
                            // Check if this method always returns true
                            if (methodAlwaysReturnsTrue(method)) {
                                context.report(ISSUE, method,
                                        context.getLocation(method),
                                        "This `HostnameVerifier.verify()` method always returns " +
                                        "`true`, which is insecure as it can allow " +
                                        "man-in-the-middle attacks");
                            }
                        }
                    }
                }
            }
        };
    }

    private boolean implementsHostnameVerifier(JavaContext context, UClass cls) {
        for (com.intellij.psi.PsiClassType iface : cls.getJavaPsi().getImplementsListTypes()) {
            String name = iface.getCanonicalText();
            if (HOSTNAME_VERIFIER_INTERFACE.equals(name)) {
                return true;
            }
        }
        return false;
    }

    private boolean isAlwaysTrueVerifier(UClass cls) {
        if (cls == null) return false;
        for (UMethod method : cls.getMethods()) {
            if (VERIFY_METHOD.equals(method.getName())) {
                PsiMethod psiMethod = method.getJavaPsi();
                com.intellij.psi.PsiParameterList params = psiMethod.getParameterList();
                if (params.getParametersCount() == 2) {
                    return methodAlwaysReturnsTrue(method);
                }
            }
        }
        return false;
    }

    private boolean methodAlwaysReturnsTrue(UMethod method) {
        org.jetbrains.uast.UBlockExpression body = method.getUastBody();
        if (body == null) {
            return false;
        }

        List<org.jetbrains.uast.UExpression> statements = body.getExpressions();
        if (statements == null || statements.isEmpty()) {
            return false;
        }

        // Simple case: single return true; statement
        if (statements.size() == 1) {
            org.jetbrains.uast.UExpression stmt = statements.get(0);
            if (stmt instanceof UReturnExpression) {
                UReturnExpression ret = (UReturnExpression) stmt;
                org.jetbrains.uast.UExpression returnValue = ret.getReturnExpression();
                if (returnValue != null && Boolean.TRUE.equals(
                        UastLiteralUtils.getValue(returnValue))) {
                    return true;
                }
            }
        }

        // More complex analysis: check if all return statements return true
        // and there are no conditional branches that could return false
        AlwaysReturnsTrueVisitor visitor = new AlwaysReturnsTrueVisitor();
        body.accept(visitor);
        return visitor.alwaysReturnsTrue();
    }

    private static class AlwaysReturnsTrueVisitor extends AbstractUastVisitor {
        private boolean hasReturnTrue = false;
        private boolean hasReturnFalse = false;
        private boolean hasOtherReturn = false;

        @Override
        public boolean visitReturnExpression(UReturnExpression node) {
            org.jetbrains.uast.UExpression returnValue = node.getReturnExpression();
            if (returnValue != null) {
                Object value = UastLiteralUtils.getValue(returnValue);
                if (Boolean.TRUE.equals(value)) {
                    hasReturnTrue = true;
                } else if (Boolean.FALSE.equals(value)) {
                    hasReturnFalse = true;
                } else {
                    hasOtherReturn = true;
                }
            } else {
                hasOtherReturn = true;
            }
            return super.visitReturnExpression(node);
        }

        public boolean alwaysReturnsTrue() {
            return hasReturnTrue && !hasReturnFalse && !hasOtherReturn;
        }
    }
}