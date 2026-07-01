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
        for (PsiType iface : node.getPsi().getSuperTypes()) {
            String canonicalText = iface.getCanonicalText();
            if (HOSTNAME_VERIFIER.equals(canonicalText)) {
                return true;
            }
        }
        // Also check via evaluator for broader coverage
        return context.getEvaluator().implementsInterface(node, HOSTNAME_VERIFIER, false);
    }

    private boolean isVerifyMethod(JavaContext context, UMethod method) {
        if (!VERIFY_METHOD.equals(method.getName())) {
            return false;
        }
        PsiMethod psiMethod = method.getPsi();
        PsiType returnType = psiMethod.getReturnType();
        if (returnType == null || !returnType.equals(PsiType.BOOLEAN)) {
            return false;
        }
        // verify(String, SSLSession) has 2 parameters
        return psiMethod.getParameterList().getParametersCount() == 2;
    }

    private void checkVerifyMethod(JavaContext context, UMethod method) {
        UExpression body = method.getUastBody();
        if (body == null) {
            return;
        }

        AlwaysReturnsTrueVisitor visitor = new AlwaysReturnsTrueVisitor();
        body.accept(visitor);

        if (visitor.alwaysReturnsTrue && !visitor.hasReturnFalse && !visitor.hasComplexReturn) {
            context.report(
                    ISSUE,
                    method,
                    context.getNameLocation(method),
                    "`verify` always returns `true`, which could cause insecure network traffic " +
                    "due to trusting arbitrary hostnames"
            );
        }
    }

    private static class AlwaysReturnsTrueVisitor extends AbstractUastVisitor {
        boolean alwaysReturnsTrue = false;
        boolean hasReturnFalse = false;
        boolean hasComplexReturn = false;

        @Override
        public boolean visitReturnExpression(UReturnExpression node) {
            UExpression returnValue = node.getReturnExpression();
            if (returnValue instanceof ULiteralExpression) {
                ULiteralExpression literal = (ULiteralExpression) returnValue;
                Object value = literal.getValue();
                if (Boolean.TRUE.equals(value)) {
                    alwaysReturnsTrue = true;
                } else if (Boolean.FALSE.equals(value)) {
                    hasReturnFalse = true;
                } else {
                    hasComplexReturn = true;
                }
            } else if (returnValue != null) {
                hasComplexReturn = true;
            }
            return super.visitReturnExpression(node);
        }
    }
}