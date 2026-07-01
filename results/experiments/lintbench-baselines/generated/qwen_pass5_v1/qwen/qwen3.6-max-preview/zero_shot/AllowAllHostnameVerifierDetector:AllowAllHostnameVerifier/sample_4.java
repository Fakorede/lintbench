package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.intellij.psi.PsiType;
import org.jetbrains.uast.UBlockExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.ULiteralExpression;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UReturnExpression;

import java.util.Collections;
import java.util.List;

public class AllowAllHostnameVerifierDetector extends Detector implements Detector.UastScanner {
    public static final Issue ISSUE = Issue.create(
        "AllowAllHostnameVerifier",
        "Insecure HostnameVerifier",
        "This check looks for use of HostnameVerifier implementations whose `verify` method always returns true (thus trusting any hostname) which could result in insecure network traffic caused by trusting arbitrary hostnames in TLS/SSL certificates presented by peers.",
        Category.SECURITY,
        6,
        Severity.WARNING,
        new Implementation(AllowAllHostnameVerifierDetector.class, Scope.JAVA_FILE_SCOPE)
    );

    private static final String HOSTNAME_VERIFIER = "javax.net.ssl.HostnameVerifier";

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.singletonList(UMethod.class);
    }

    @Override
    public void visitMethod(JavaContext context, UMethod method) {
        if (!"verify".equals(method.getName())) {
            return;
        }

        if (method.getParameterList().getParametersCount() != 2) {
            return;
        }

        PsiType returnType = method.getReturnType();
        if (returnType == null || !returnType.equals(PsiType.BOOLEAN)) {
            return;
        }

        UClass containingClass = method.getContainingClass();
        if (containingClass == null) {
            return;
        }

        if (!context.getEvaluator().implementsInterface(containingClass, HOSTNAME_VERIFIER, false)) {
            return;
        }

        UExpression body = method.getUastBody();
        if (body instanceof UBlockExpression) {
            List<UExpression> statements = ((UBlockExpression) body).getExpressions();
            if (statements.size() == 1) {
                UExpression statement = statements.get(0);
                if (statement instanceof UReturnExpression) {
                    UExpression returnValue = ((UReturnExpression) statement).getReturnExpression();
                    if (returnValue instanceof ULiteralExpression) {
                        Object value = ((ULiteralExpression) returnValue).getValue();
                        if (Boolean.TRUE.equals(value)) {
                            context.report(ISSUE, context.getLocation(method),
                                "HostnameVerifier.verify() always returns true, which trusts all hostnames and disables SSL hostname verification.");
                        }
                    }
                }
            }
        }
    }
}