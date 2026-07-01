package com.android.tools.lint.checks;

import com.android.tools.lint.client.api.UElementHandler;
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
import org.jetbrains.uast.UReturnExpression;
import org.jetbrains.uast.visitor.AbstractUastVisitor;

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

    private static final String HOSTNAME_VERIFIER_INTERFACE = "javax.net.ssl.HostnameVerifier";
    private static final String ALLOW_ALL_HOSTNAME_VERIFIER =
            "org.apache.http.conn.ssl.AllowAllHostnameVerifier";
    private static final String ALLOW_ALL_HOSTNAME_VERIFIER2 =
            "org.apache.http.conn.ssl.SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER";
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
                // Check for anonymous classes or named classes implementing HostnameVerifier
                checkHostnameVerifierClass(context, node);
            }
        };
    }

    @Override
    public List<String> getApplicableMethodNames() {
        return Collections.singletonList("setHostnameVerifier");
    }

    @Override
    public void visitMethodCall(JavaContext context, UCallExpression call, PsiMethod method) {
        // Check if setHostnameVerifier is called with AllowAllHostnameVerifier
        List<UExpression> args = call.getValueArguments();
        if (args.isEmpty()) {
            return;
        }
        UExpression arg = args.get(0);
        PsiType argType = arg.getExpressionType();
        if (argType != null) {
            String typeName = argType.getCanonicalText();
            if (ALLOW_ALL_HOSTNAME_VERIFIER.equals(typeName)) {
                context.report(ISSUE, call, context.getLocation(arg),
                        "Using `AllowAllHostnameVerifier` is unsafe because it always " +
                        "returns true, which could allow man-in-the-middle attacks");
            }
        }
    }

    private void checkHostnameVerifierClass(JavaContext context, UClass cls) {
        // Check if this class implements HostnameVerifier
        boolean implementsHostnameVerifier = false;
        for (PsiType iface : cls.getPsi().getSuperTypes()) {
            if (HOSTNAME_VERIFIER_INTERFACE.equals(iface.getCanonicalText())) {
                implementsHostnameVerifier = true;
                break;
            }
        }

        if (!implementsHostnameVerifier) {
            // Also check via interface list
            for (String iface : getInterfaceNames(cls)) {
                if (HOSTNAME_VERIFIER_INTERFACE.equals(iface)) {
                    implementsHostnameVerifier = true;
                    break;
                }
            }
        }

        if (!implementsHostnameVerifier) {
            return;
        }

        // Find the verify method
        for (UMethod method : cls.getMethods()) {
            if ("verify".equals(method.getName())) {
                PsiMethod psiMethod = method.getPsi();
                // Check that it has the right signature: boolean verify(String, SSLSession)
                if (psiMethod.getParameterList().getParametersCount() == 2) {
                    PsiType returnType = psiMethod.getReturnType();
                    if (returnType != null &&
                            (PsiType.BOOLEAN.equals(returnType) ||
                             "boolean".equals(returnType.getCanonicalText()))) {
                        // Check if the method always returns true
                        if (alwaysReturnsTrue(method)) {
                            context.report(ISSUE, method,
                                    context.getLocation((UElement) method),
                                    "This `HostnameVerifier` always returns `true`, which " +
                                    "could cause insecure network traffic due to trusting " +
                                    "arbitrary hostnames in TLS/SSL certificates presented " +
                                    "by peers");
                        }
                    }
                }
            }
        }
    }

    private String[] getInterfaceNames(UClass cls) {
        try {
            com.intellij.psi.PsiClass psiClass = cls.getPsi();
            com.intellij.psi.PsiClassType[] interfaces = psiClass.getImplementsListTypes();
            String[] names = new String[interfaces.length];
            for (int i = 0; i < interfaces.length; i++) {
                names[i] = interfaces[i].getCanonicalText();
            }
            return names;
        } catch (Exception e) {
            return new String[0];
        }
    }

    private boolean alwaysReturnsTrue(UMethod method) {
        UExpression body = method.getUastBody();
        if (body == null) {
            return false;
        }

        AlwaysReturnsTrueVisitor visitor = new AlwaysReturnsTrueVisitor();
        body.accept(visitor);
        return visitor.alwaysReturnsTrue;
    }

    /**
     * Visitor that checks if a method body always returns true.
     * A simple heuristic: if the only return statements in the method return true literal,
     * and there's at least one return statement.
     */
    private static class AlwaysReturnsTrueVisitor extends AbstractUastVisitor {
        boolean alwaysReturnsTrue = false;
        boolean hasReturnTrue = false;
        boolean hasReturnFalse = false;
        boolean hasReturnOther = false;

        @Override
        public boolean visitReturnExpression(UReturnExpression node) {
            UExpression returnValue = node.getReturnExpression();
            if (returnValue instanceof ULiteralExpression) {
                Object value = ((ULiteralExpression) returnValue).getValue();
                if (Boolean.TRUE.equals(value)) {
                    hasReturnTrue = true;
                } else if (Boolean.FALSE.equals(value)) {
                    hasReturnFalse = true;
                } else {
                    hasReturnOther = true;
                }
            } else if (returnValue != null) {
                hasReturnOther = true;
            }
            return super.visitReturnExpression(node);
        }

        @Override
        public void afterVisitMethod(UMethod node) {
            // Don't recurse into nested methods/lambdas
        }

        public boolean isAlwaysReturnsTrue() {
            return hasReturnTrue && !hasReturnFalse && !hasReturnOther;
        }
    }

    // Override to use the visitor result properly
    private boolean alwaysReturnsTrueCheck(UMethod method) {
        UExpression body = method.getUastBody();
        if (body == null) {
            return false;
        }

        final boolean[] hasReturnTrue = {false};
        final boolean[] hasReturnFalse = {false};
        final boolean[] hasReturnOther = {false};

        body.accept(new AbstractUastVisitor() {
            @Override
            public boolean visitReturnExpression(UReturnExpression node) {
                UExpression returnValue = node.getReturnExpression();
                if (returnValue instanceof ULiteralExpression) {
                    Object value = ((ULiteralExpression) returnValue).getValue();
                    if (Boolean.TRUE.equals(value)) {
                        hasReturnTrue[0] = true;
                    } else if (Boolean.FALSE.equals(value)) {
                        hasReturnFalse[0] = true;
                    } else {
                        hasReturnOther[0] = true;
                    }
                } else if (returnValue != null) {
                    hasReturnOther[0] = true;
                }
                return false;
            }
        });

        return hasReturnTrue[0] && !hasReturnFalse[0] && !hasReturnOther[0];
    }
}