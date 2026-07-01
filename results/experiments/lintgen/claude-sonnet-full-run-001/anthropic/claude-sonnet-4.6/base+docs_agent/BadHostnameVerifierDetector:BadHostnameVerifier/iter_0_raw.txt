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
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.ULiteralExpression;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UReturnExpression;
import org.jetbrains.uast.UastUtils;
import org.jetbrains.uast.visitor.AbstractUastVisitor;

import java.util.Collections;
import java.util.List;

public class BadHostnameVerifierDetector extends Detector implements SourceCodeScanner {

    public static final Issue ISSUE = Issue.create(
            "BadHostnameVerifier",
            "Insecure HostnameVerifier",
            "This check looks for implementations of `HostnameVerifier` whose `verify` method " +
            "always returns true (thus trusting any hostname) which could result in insecure " +
            "network traffic caused by trusting arbitrary hostnames in TLS/SSL certificates " +
            "presented by peers.",
            Category.SECURITY,
            6,
            Severity.WARNING,
            new Implementation(
                    BadHostnameVerifierDetector.class,
                    Scope.JAVA_FILE_SCOPE
            )
    ).addMoreInfo("https://goo.gle/BadHostnameVerifier");

    private static final String HOSTNAME_VERIFIER = "javax.net.ssl.HostnameVerifier";
    private static final String VERIFY_METHOD = "verify";

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.singletonList(UClass.class);
    }

    @Override
    public UElementHandler createUastHandler(JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitClass(UClass node) {
                // Check if this class implements HostnameVerifier
                if (!implementsHostnameVerifier(context, node)) {
                    return;
                }

                // Find the verify method
                for (UMethod method : node.getMethods()) {
                    if (isVerifyMethod(context, method)) {
                        checkVerifyMethod(context, method);
                    }
                }
            }
        };
    }

    private boolean implementsHostnameVerifier(JavaContext context, UClass node) {
        for (PsiType type : node.getUastSuperTypes()) {
            String canonicalText = type.getCanonicalText();
            if (HOSTNAME_VERIFIER.equals(canonicalText)) {
                return true;
            }
        }
        // Also check via PSI interfaces
        for (com.intellij.psi.PsiClassType iface : node.getJavaPsi().getImplementsListTypes()) {
            if (HOSTNAME_VERIFIER.equals(iface.getCanonicalText())) {
                return true;
            }
        }
        return false;
    }

    private boolean isVerifyMethod(JavaContext context, UMethod method) {
        if (!VERIFY_METHOD.equals(method.getName())) {
            return false;
        }
        PsiMethod psiMethod = method.getJavaPsi();
        // verify(String, SSLSession) returns boolean
        if (psiMethod.getParameterList().getParametersCount() != 2) {
            return false;
        }
        PsiType returnType = psiMethod.getReturnType();
        if (returnType == null) {
            return false;
        }
        return PsiType.BOOLEAN.equals(returnType);
    }

    private void checkVerifyMethod(JavaContext context, UMethod method) {
        // Check if the method always returns true
        AlwaysReturnsTrueVisitor visitor = new AlwaysReturnsTrueVisitor();
        method.accept(visitor);

        if (visitor.alwaysReturnsTrue()) {
            context.report(
                    ISSUE,
                    method,
                    context.getLocation(method),
                    "`verify` always returns `true`, which could cause insecure network traffic " +
                    "due to trusting arbitrary hostnames"
            );
        }
    }

    /**
     * Visitor that checks whether a verify method always returns true.
     * It looks for return statements and checks if all of them return true literal,
     * and that there is at least one return statement.
     */
    private static class AlwaysReturnsTrueVisitor extends AbstractUastVisitor {
        private boolean hasReturnStatement = false;
        private boolean hasNonTrueReturn = false;
        private int depth = 0;

        @Override
        public boolean visitMethod(UMethod node) {
            // Don't recurse into nested methods/lambdas
            depth++;
            return depth > 1; // skip body if nested
        }

        @Override
        public void afterVisitMethod(UMethod node) {
            depth--;
        }

        @Override
        public boolean visitClass(UClass node) {
            // Don't recurse into anonymous/inner classes
            return true; // skip
        }

        @Override
        public boolean visitReturnExpression(UReturnExpression node) {
            if (depth > 1) {
                return false;
            }
            UExpression returnValue = node.getReturnExpression();
            hasReturnStatement = true;
            if (!isTrueLiteral(returnValue)) {
                hasNonTrueReturn = true;
            }
            return false;
        }

        private boolean isTrueLiteral(UExpression expression) {
            if (expression instanceof ULiteralExpression) {
                Object value = ((ULiteralExpression) expression).getValue();
                return Boolean.TRUE.equals(value);
            }
            return false;
        }

        public boolean alwaysReturnsTrue() {
            return hasReturnStatement && !hasNonTrueReturn;
        }
    }
}