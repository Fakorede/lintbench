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
import com.intellij.psi.PsiClass;
import org.jetbrains.uast.UBlockExpression;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.ULiteralExpression;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UReturnExpression;

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
        new Implementation(BadHostnameVerifierDetector.class, Scope.JAVA_FILE_SCOPE));

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.singletonList(UMethod.class);
    }

    @Override
    public UElementHandler createUastHandler(JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitMethod(UMethod method) {
                if (!"verify".equals(method.getName())) {
                    return;
                }

                if (method.getUastParameters().size() != 2) {
                    return;
                }

                PsiClass containingClass = method.getContainingClass();
                if (containingClass == null) {
                    return;
                }

                if (!context.getEvaluator().implementsInterface(containingClass, "javax.net.ssl.HostnameVerifier", false)) {
                    return;
                }

                UExpression body = method.getUastBody();
                if (body == null) {
                    return;
                }

                if (isReturningTrue(body)) {
                    context.report(ISSUE, method, context.getLocation(method),
                        "Insecure `HostnameVerifier` implementation: `verify()` always returns `true`");
                }
            }

            private boolean isReturningTrue(UExpression expr) {
                if (expr instanceof ULiteralExpression) {
                    return Boolean.TRUE.equals(((ULiteralExpression) expr).getValue());
                }
                if (expr instanceof UReturnExpression) {
                    UExpression returnExpr = ((UReturnExpression) expr).getReturnExpression();
                    return returnExpr != null && isReturningTrue(returnExpr);
                }
                if (expr instanceof UBlockExpression) {
                    List<UExpression> statements = ((UBlockExpression) expr).getExpressions();
                    if (statements.size() == 1) {
                        return isReturningTrue(statements.get(0));
                    }
                }
                return false;
            }
        };
    }
}