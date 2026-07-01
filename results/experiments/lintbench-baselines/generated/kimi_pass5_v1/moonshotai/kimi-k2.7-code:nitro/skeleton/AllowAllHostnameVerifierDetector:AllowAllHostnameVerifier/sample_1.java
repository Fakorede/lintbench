package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import java.util.Collections;
import java.util.List;
import org.jetbrains.uast.UBlockExpression;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UElementHandler;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.ULiteralExpression;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UParenthesizedExpression;
import org.jetbrains.uast.UReturnExpression;

public class AllowAllHostnameVerifierDetector extends Detector implements SourceCodeScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(AllowAllHostnameVerifierDetector.class, Scope.JAVA_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "AllowAllHostnameVerifier",
                    "Insecure `HostnameVerifier`",
                    "This check looks for use of `HostnameVerifier` implementations whose "
                            + "`verify` method always returns true (thus trusting any hostname) "
                            + "which could result in insecure network traffic caused by trusting "
                            + "arbitrary hostnames in TLS/SSL certificates presented by peers.",
                    Category.SECURITY,
                    6,
                    Severity.WARNING,
                    IMPLEMENTATION);

    private static final String HOSTNAME_VERIFIER = "javax.net.ssl.HostnameVerifier";
    private static final String ALLOW_ALL_HOSTNAME_VERIFIER =
            "org.apache.http.conn.ssl.AllowAllHostnameVerifier";

    private static final String MESSAGE =
            "Using a `HostnameVerifier` that always returns true is insecure and can allow "
                    + "man-in-the-middle attacks.";

    @Override
    public List<String> getApplicableConstructorTypes() {
        return Collections.singletonList(ALLOW_ALL_HOSTNAME_VERIFIER);
    }

    @Override
    public void visitConstructor(
            JavaContext context,
            UCallExpression node,
            PsiMethod constructor) {
        reportIssue(context, node);
    }

    @Override
    public List<String> getApplicableMethodNames() {
        return Collections.emptyList();
    }

    @Override
    public void visitMethodCall(
            JavaContext context,
            UCallExpression node,
            PsiMethod method) {
        // No method-call based checks are required.
    }

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.singletonList(UMethod.class);
    }

    @Override
    public UElementHandler createUastHandler(JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitMethod(UMethod node) {
                AllowAllHostnameVerifierDetector.this.visitVerifyMethod(context, node);
            }
        };
    }

    private void visitVerifyMethod(JavaContext context, UMethod node) {
        PsiMethod psi = node.getJavaPsi();
        if (psi == null) {
            return;
        }

        if (!"verify".equals(psi.getName())) {
            return;
        }

        if (psi.getParameterList().getParametersCount() != 2) {
            return;
        }

        PsiType returnType = psi.getReturnType();
        if (returnType == null || !returnType.equalsToText("boolean")) {
            return;
        }

        boolean overridesHostnameVerifier = false;
        for (PsiMethod superMethod : psi.findSuperMethods()) {
            PsiClass superClass = superMethod.getContainingClass();
            if (superClass != null
                    && HOSTNAME_VERIFIER.equals(superClass.getQualifiedName())) {
                overridesHostnameVerifier = true;
                break;
            }
        }

        if (!overridesHostnameVerifier) {
            return;
        }

        UExpression body = node.getUastBody();
        if (body != null && returnsTrue(body)) {
            reportIssue(context, node);
        }
    }

    private static boolean returnsTrue(UExpression expression) {
        if (expression == null) {
            return false;
        }

        if (expression instanceof UParenthesizedExpression) {
            return returnsTrue(((UParenthesizedExpression) expression).getExpression());
        }

        if (expression instanceof UReturnExpression) {
            return returnsTrue(((UReturnExpression) expression).getReturnExpression());
        }

        if (expression instanceof UBlockExpression) {
            List<UExpression> expressions = ((UBlockExpression) expression).getExpressions();
            if (expressions.size() != 1) {
                return false;
            }
            return returnsTrue(expressions.get(0));
        }

        if (expression instanceof ULiteralExpression) {
            Object value = ((ULiteralExpression) expression).getValue();
            return Boolean.TRUE.equals(value);
        }

        return false;
    }

    private static void reportIssue(JavaContext context, UElement node) {
        context.report(ISSUE, node, context.getLocation(node), MESSAGE);
    }
}