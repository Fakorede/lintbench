package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;

import org.jetbrains.uast.UAnonymousClass;
import org.jetbrains.uast.UBlockExpression;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.ULiteralExpression;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UQualifiedReferenceExpression;
import org.jetbrains.uast.UReturnExpression;
import org.jetbrains.uast.UastUtils;
import org.jetbrains.uast.visitor.AbstractUastVisitor;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class AllowAllHostnameVerifierDetector extends Detector implements SourceCodeScanner {

    public static final Issue ISSUE = Issue.create(
            "AllowAllHostnameVerifier",
            "Insecure `HostnameVerifier`",
            "This check looks for use of HostnameVerifier implementations whose `verify` " +
            "method always returns true (thus trusting any hostname) which could result in " +
            "insecure network traffic caused by trusting arbitrary hostnames in TLS/SSL " +
            "certificates presented by peers.",
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
    private static final String SSL_SOCKET_FACTORY_ALLOW_ALL =
            "org.apache.http.conn.ssl.SSLSocketFactory";
    private static final String VERIFY_METHOD = "verify";

    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList(HOSTNAME_VERIFIER);
    }

    @Override
    public List<String> getApplicableMethodNames() {
        return Arrays.asList(
                "setHostnameVerifier",
                "setDefaultHostnameVerifier",
                "setSSLSocketFactory"
        );
    }

    @Override
    public void visitClass(JavaContext context, UClass declaration) {
        // Check anonymous or named classes implementing HostnameVerifier
        // where verify() always returns true
        for (UMethod method : declaration.getMethods()) {
            if (VERIFY_METHOD.equals(method.getName())
                    && isVerifySignature(context, method)
                    && alwaysReturnsTrue(method)) {
                context.report(
                        ISSUE,
                        method,
                        context.getLocation(method),
                        "verify` always returns `true`, which could cause insecure network " +
                        "traffic due to trusting TLS/SSL server certificates for wrong hostnames"
                );
                return;
            }
        }
    }

    @Override
    public void visitMethodCall(JavaContext context, UCallExpression call, PsiMethod method) {
        String methodName = call.getMethodName();
        if (methodName == null) {
            return;
        }

        List<UExpression> args = call.getValueArguments();

        if ("setHostnameVerifier".equals(methodName) || "setDefaultHostnameVerifier".equals(methodName)) {
            // Check if the argument is an AllowAllHostnameVerifier or ALLOW_ALL_HOSTNAME_VERIFIER field
            for (UExpression arg : args) {
                if (isAllowAllHostnameVerifier(context, arg)) {
                    context.report(
                            ISSUE,
                            call,
                            context.getLocation(call),
                            "Using `AllowAllHostnameVerifier` or `ALLOW_ALL_HOSTNAME_VERIFIER` " +
                            "is insecure because it always returns true"
                    );
                    return;
                }
            }
        } else if ("setSSLSocketFactory".equals(methodName)) {
            for (UExpression arg : args) {
                if (isAllowAllSSLSocketFactory(context, arg)) {
                    context.report(
                            ISSUE,
                            call,
                            context.getLocation(call),
                            "Using `SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER` is insecure"
                    );
                    return;
                }
            }
        }
    }

    private boolean isVerifySignature(JavaContext context, UMethod method) {
        // verify(String hostname, SSLSession session) returns boolean
        PsiMethod psiMethod = method.getJavaPsi();
        if (psiMethod == null) {
            return false;
        }
        com.intellij.psi.PsiParameter[] params = psiMethod.getParameterList().getParameters();
        if (params.length != 2) {
            return false;
        }
        PsiType returnType = psiMethod.getReturnType();
        return returnType != null && returnType.equals(PsiType.BOOLEAN);
    }

    private boolean alwaysReturnsTrue(UMethod method) {
        UExpression body = method.getUastBody();
        if (body == null) {
            return false;
        }

        // Check if body is a block with a single return true statement
        if (body instanceof UBlockExpression) {
            UBlockExpression block = (UBlockExpression) body;
            List<UExpression> expressions = block.getExpressions();
            // Filter out empty/null expressions
            if (expressions.size() == 1) {
                UExpression expr = expressions.get(0);
                if (expr instanceof UReturnExpression) {
                    UExpression returnValue = ((UReturnExpression) expr).getReturnExpression();
                    return isTrueLiteral(returnValue);
                }
            }
            // Check if all return statements return true
            AlwaysReturnsTrueVisitor visitor = new AlwaysReturnsTrueVisitor();
            body.accept(visitor);
            return visitor.alwaysReturnsTrue && visitor.hasReturn;
        }

        // Expression body
        if (body instanceof UReturnExpression) {
            UExpression returnValue = ((UReturnExpression) body).getReturnExpression();
            return isTrueLiteral(returnValue);
        }

        return isTrueLiteral(body);
    }

    private boolean isTrueLiteral(UExpression expr) {
        if (expr instanceof ULiteralExpression) {
            Object value = ((ULiteralExpression) expr).getValue();
            return Boolean.TRUE.equals(value);
        }
        return false;
    }

    private boolean isAllowAllHostnameVerifier(JavaContext context, UExpression arg) {
        // Check for new AllowAllHostnameVerifier()
        if (arg instanceof UCallExpression) {
            UCallExpression callExpr = (UCallExpression) arg;
            PsiMethod resolvedMethod = callExpr.resolve();
            if (resolvedMethod != null) {
                com.intellij.psi.PsiClass containingClass = resolvedMethod.getContainingClass();
                if (containingClass != null) {
                    String qualifiedName = containingClass.getQualifiedName();
                    if (ALLOW_ALL_HOSTNAME_VERIFIER.equals(qualifiedName)) {
                        return true;
                    }
                }
            }
        }

        // Check for field reference like SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER
        if (arg instanceof UQualifiedReferenceExpression) {
            UQualifiedReferenceExpression ref = (UQualifiedReferenceExpression) arg;
            String refStr = ref.asSourceString();
            if (refStr != null && refStr.contains("ALLOW_ALL_HOSTNAME_VERIFIER")) {
                return true;
            }
        }

        // Check resolved type
        PsiType type = arg.getExpressionType();
        if (type != null) {
            String typeName = type.getCanonicalText();
            if (ALLOW_ALL_HOSTNAME_VERIFIER.equals(typeName)) {
                return true;
            }
        }

        return false;
    }

    private boolean isAllowAllSSLSocketFactory(JavaContext context, UExpression arg) {
        if (arg instanceof UQualifiedReferenceExpression) {
            UQualifiedReferenceExpression ref = (UQualifiedReferenceExpression) arg;
            String refStr = ref.asSourceString();
            if (refStr != null && refStr.contains("ALLOW_ALL_HOSTNAME_VERIFIER")) {
                return true;
            }
        }
        return false;
    }

    private static class AlwaysReturnsTrueVisitor extends AbstractUastVisitor {
        boolean alwaysReturnsTrue = true;
        boolean hasReturn = false;

        @Override
        public boolean visitReturnExpression(UReturnExpression node) {
            hasReturn = true;
            UExpression returnValue = node.getReturnExpression();
            if (returnValue instanceof ULiteralExpression) {
                Object value = ((ULiteralExpression) returnValue).getValue();
                if (!Boolean.TRUE.equals(value)) {
                    alwaysReturnsTrue = false;
                }
            } else {
                alwaysReturnsTrue = false;
            }
            return super.visitReturnExpression(node);
        }
    }
}