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
import java.util.Collections;
import java.util.List;
import org.jetbrains.uast.UBlockExpression;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.ULiteralExpression;
import org.jetbrains.uast.UReturnExpression;
import org.jetbrains.uast.UThrowExpression;

public class BadHostnameVerifierDetector extends Detector implements SourceCodeScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(BadHostnameVerifierDetector.class, Scope.JAVA_FILE_SCOPE);

    private static final String HOSTNAME_VERIFIER = "javax.net.ssl.HostnameVerifier";

    public static final Issue ISSUE =
            Issue.create(
                    "BadHostnameVerifier",
                    "Insecure HostnameVerifier",
                    "This check looks for implementations of `HostnameVerifier` whose `verify` "
                            + "method always returns true (thus trusting any hostname), which "
                            + "could result in insecure network traffic caused by trusting "
                            + "arbitrary hostnames in TLS/SSL certificates presented by peers.",
                    Category.SECURITY,
                    6,
                    Severity.WARNING,
                    IMPLEMENTATION);

    public BadHostnameVerifierDetector() {}

    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList(HOSTNAME_VERIFIER);
    }

    @Override
    public void visitClass(JavaContext context, UClass declaration) {
        if (HOSTNAME_VERIFIER.equals(declaration.getQualifiedName())) {
            return;
        }

        for (PsiMethod method : declaration.getMethods()) {
            if ("verify".equals(method.getName())
                    && method.getParameterList().getParametersCount() == 2) {
                UExpression body = context.getUastContext().getMethodBody(method);
                if (body != null && isUnconditionalTrue(body)) {
                    context.report(
                            ISSUE,
                            method,
                            context.getNameLocation(method),
                            "Insecure `HostnameVerifier`: `verify` should not always return true");
                }
            }
        }
    }

    @Override
    public void visitReturnExpression(JavaContext context, UReturnExpression expression) {
        // Detection is performed by inspecting verify method bodies in visitClass.
    }

    @Override
    public void visitThrowExpression(JavaContext context, UThrowExpression expression) {
        // Not used for this check.
    }

    @Override
    public void visitCallExpression(JavaContext context, UCallExpression expression) {
        // Not used for this check.
    }

    private boolean isUnconditionalTrue(UExpression expression) {
        if (expression instanceof UReturnExpression) {
            UExpression value = ((UReturnExpression) expression).getReturnExpression();
            return value instanceof ULiteralExpression
                    && Boolean.TRUE.equals(((ULiteralExpression) value).getValue());
        }

        if (expression instanceof UBlockExpression) {
            List<UExpression> expressions = ((UBlockExpression) expression).getExpressions();
            if (expressions.size() == 1) {
                return isUnconditionalTrue(expressions.get(0));
            }
        }

        return false;
    }
}