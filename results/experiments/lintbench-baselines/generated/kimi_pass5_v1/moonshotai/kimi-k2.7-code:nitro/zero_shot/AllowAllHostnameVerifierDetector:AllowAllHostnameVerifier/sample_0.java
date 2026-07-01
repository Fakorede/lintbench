package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.intellij.psi.PsiMethod;
import java.util.Collections;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.uast.UBlockExpression;
import org.jetbrains.uast.UBooleanLiteral;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UReturnExpression;

public class AllowAllHostnameVerifierDetector extends Detector implements Detector.SourceCodeScanner {

    private static final Issue ISSUE = Issue.create(
            "AllowAllHostnameVerifier",
            "Insecure HostnameVerifier",
            "A HostnameVerifier whose verify(...) method always returns true trusts any hostname, "
                    + "which may allow attackers to intercept TLS/SSL traffic with arbitrary certificates.",
            Category.SECURITY,
            6,
            Severity.WARNING,
            new Implementation(AllowAllHostnameVerifierDetector.class, Scope.JAVA_FILE_SCOPE));

    @NotNull
    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList("javax.net.ssl.HostnameVerifier");
    }

    @Override
    public void visitClass(@NotNull JavaContext context, @NotNull UClass node) {
        for (PsiMethod psiMethod : node.getMethods()) {
            if (!(psiMethod instanceof UMethod)) {
                continue;
            }

            UMethod method = (UMethod) psiMethod;
            if (!"verify".equals(method.getName())) {
                continue;
            }
            if (method.getParameterList().getParametersCount() != 2) {
                continue;
            }

            UExpression body = method.getUastBody();
            if (body != null && returnsTrue(body)) {
                context.report(
                        ISSUE,
                        method,
                        context.getNameLocation(method),
                        "Insecure HostnameVerifier: verify() always returns true");
            }
        }
    }

    private static boolean returnsTrue(UExpression body) {
        if (body instanceof UReturnExpression) {
            UExpression value = ((UReturnExpression) body).getReturnExpression();
            return isTrue(value);
        }

        if (body instanceof UBlockExpression) {
            List<UExpression> expressions = ((UBlockExpression) body).getExpressions();
            if (expressions.size() == 1) {
                return returnsTrue(expressions.get(0));
            }
        }

        return false;
    }

    private static boolean isTrue(UExpression expression) {
        if (expression instanceof UBooleanLiteral) {
            return Boolean.TRUE.equals(((UBooleanLiteral) expression).getValue());
        }
        return false;
    }
}