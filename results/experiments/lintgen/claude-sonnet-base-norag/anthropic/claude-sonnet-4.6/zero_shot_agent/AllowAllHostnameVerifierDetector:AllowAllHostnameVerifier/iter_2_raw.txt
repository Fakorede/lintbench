package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiMethod;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.ULiteralExpression;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UReturnExpression;
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

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.singletonList(UClass.class);
    }

    @Override
    public UastHandler createUastHandler(JavaContext context) {
        return new HostnameVerifierVisitor(context);
    }

    private static boolean isTrue(UExpression expression) {
        if (expression instanceof ULiteralExpression) {
            Object value = ((ULiteralExpression) expression).getValue();
            return Boolean.TRUE.equals(value);
        }
        return false;
    }

    private static class HostnameVerifierVisitor extends UastHandler {
        private final JavaContext mContext;

        HostnameVerifierVisitor(JavaContext context) {
            mContext = context;
        }

        @Override
        public void visitClass(UClass node) {
            // Check if this class implements HostnameVerifier
            boolean implementsHostnameVerifier = false;
            for (PsiClassType type : node.getImplementsListTypes()) {
                if (HOSTNAME_VERIFIER.equals(type.getCanonicalText())) {
                    implementsHostnameVerifier = true;
                    break;
                }
            }

            // Also check for AllowAllHostnameVerifier by class name
            String qualifiedName = node.getQualifiedName();
            if (ALLOW_ALL_HOSTNAME_VERIFIER.equals(qualifiedName)) {
                implementsHostnameVerifier = true;
            }

            if (!implementsHostnameVerifier) {
                return;
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
        }

        private boolean alwaysReturnsTrue(UMethod method) {
            UExpression body = method.getUastBody();
            if (body == null) {
                return false;
            }

            AlwaysReturnsTrueVisitor visitor = new AlwaysReturnsTrueVisitor();
            body.accept(visitor);
            return visitor.alwaysReturnsTrue();
        }
    }

    private static class AlwaysReturnsTrueVisitor extends AbstractUastVisitor {
        private boolean mHasReturn = false;
        private boolean mHasNonTrueReturn = false;

        @Override
        public boolean visitReturnExpression(UReturnExpression node) {
            mHasReturn = true;
            UExpression retVal = node.getReturnExpression();
            if (retVal == null || !isTrue(retVal)) {
                mHasNonTrueReturn = true;
            }
            return false;
        }

        boolean alwaysReturnsTrue() {
            return mHasReturn && !mHasNonTrueReturn;
        }
    }
}