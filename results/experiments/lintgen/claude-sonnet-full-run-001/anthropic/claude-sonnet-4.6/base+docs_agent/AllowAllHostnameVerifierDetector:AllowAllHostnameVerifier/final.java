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

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.singletonList(UCallExpression.class);
    }

    @Override
    public UElementHandler createUastHandler(JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitCallExpression(UCallExpression node) {
                // Check for instantiation of AllowAllHostnameVerifier
                if (node.getKind() == org.jetbrains.uast.UastCallKind.CONSTRUCTOR_CALL) {
                    PsiMethod resolved = node.resolve();
                    if (resolved != null) {
                        String qualifiedName = resolved.getContainingClass() != null
                                ? resolved.getContainingClass().getQualifiedName()
                                : null;
                        if (ALLOW_ALL_HOSTNAME_VERIFIER.equals(qualifiedName)) {
                            context.report(ISSUE, node, context.getLocation(node),
                                    "Using the `AllowAllHostnameVerifier` HostnameVerifier is unsafe " +
                                    "because it always returns true, which could cause insecure " +
                                    "network traffic due to trusting TLS/SSL server certificates for " +
                                    "wrong hostnames");
                        }
                    }
                }
            }
        };
    }

    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList(HOSTNAME_VERIFIER_INTERFACE);
    }

    @Override
    public void visitClass(JavaContext context, UClass declaration) {
        // Look for classes that implement HostnameVerifier
        // Check if the verify method always returns true
        for (UMethod method : declaration.getMethods()) {
            if (isVerifyMethod(method)) {
                if (alwaysReturnsTrue(method)) {
                    context.report(ISSUE, method, context.getLocation(method),
                            "This `HostnameVerifier` implementation is unsafe because it always " +
                            "returns true, which could cause insecure network traffic due to " +
                            "trusting TLS/SSL server certificates for wrong hostnames");
                }
            }
        }
    }

    private boolean isVerifyMethod(UMethod method) {
        if (!"verify".equals(method.getName())) {
            return false;
        }
        PsiMethod psiMethod = method.getJavaPsi();
        PsiType returnType = psiMethod.getReturnType();
        if (returnType == null || !returnType.equals(PsiType.BOOLEAN)) {
            return false;
        }
        // verify(String hostname, SSLSession session)
        if (psiMethod.getParameterList().getParametersCount() != 2) {
            return false;
        }
        return true;
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
     * It looks for return statements and checks if they all return true literals.
     */
    private static class AlwaysReturnsTrueVisitor extends AbstractUastVisitor {
        boolean alwaysReturnsTrue = false;
        boolean hasReturnTrue = false;
        boolean hasOtherReturn = false;

        @Override
        public boolean visitReturnExpression(UReturnExpression node) {
            UExpression returnValue = node.getReturnExpression();
            if (returnValue instanceof ULiteralExpression) {
                Object value = ((ULiteralExpression) returnValue).getValue();
                if (Boolean.TRUE.equals(value)) {
                    hasReturnTrue = true;
                } else {
                    hasOtherReturn = true;
                }
            } else {
                hasOtherReturn = true;
            }
            updateResult();
            return super.visitReturnExpression(node);
        }

        // Don't descend into nested anonymous classes
        @Override
        public boolean visitClass(UClass node) {
            return true; // skip
        }

        private void updateResult() {
            alwaysReturnsTrue = hasReturnTrue && !hasOtherReturn;
        }
    }
}