package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.ConstantEvaluator;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.UElementHandler;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import java.util.Arrays;
import java.util.List;
import org.jetbrains.uast.UBlockExpression;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.ULambdaExpression;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UReturnExpression;

public class BadHostnameVerifierDetector extends Detector implements SourceCodeScanner {

    private static final String HOSTNAME_VERIFIER = "javax.net.ssl.HostnameVerifier";

    private static final String MESSAGE =
            "Insecure HostnameVerifier: `verify` always returns true, trusting any hostname. "
                    + "This may allow insecure network traffic due to trusting arbitrary "
                    + "hostnames in TLS/SSL certificates presented by peers.";

    private static final Issue ISSUE = Issue.create(
            "BadHostnameVerifier",
            "Insecure HostnameVerifier",
            "This HostnameVerifier implementation always returns true, which trusts any hostname. "
                    + "This can result in insecure network traffic caused by trusting arbitrary "
                    + "hostnames in TLS/SSL certificates presented by peers.",
            Category.SECURITY,
            6,
            Severity.WARNING,
            new Implementation(BadHostnameVerifierDetector.class, Scope.JAVA_FILE_SCOPE)
    );

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Arrays.asList(UMethod.class, ULambdaExpression.class);
    }

    @Override
    public UElementHandler createUastHandler(JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitMethod(UMethod node) {
                if (!"verify".equals(node.getName())) {
                    return;
                }

                PsiMethod psiMethod = node.getJavaPsi();
                if (psiMethod == null) {
                    return;
                }

                if (psiMethod.getParameterList().getParametersCount() != 2) {
                    return;
                }

                PsiClass containingClass = psiMethod.getContainingClass();
                if (containingClass == null) {
                    return;
                }

                if (!context.getEvaluator().implementsInterface(containingClass, HOSTNAME_VERIFIER, false)) {
                    return;
                }

                UExpression body = node.getUastBody();
                if (body == null) {
                    return;
                }

                checkAlwaysReturnsTrue(context, body, node);
            }

            @Override
            public void visitLambdaExpression(ULambdaExpression node) {
                PsiType type = node.getFunctionalInterfaceType();
                if (type == null) {
                    type = node.getExpressionType();
                }
                if (type == null) {
                    return;
                }
                if (!HOSTNAME_VERIFIER.equals(type.getCanonicalText())) {
                    return;
                }

                UExpression body = node.getBody();
                if (body == null) {
                    return;
                }

                checkAlwaysReturnsTrue(context, body, node);
            }
        };
    }

    private static void checkAlwaysReturnsTrue(JavaContext context, UExpression body, UElement node) {
        UExpression returnExpression = getReturnExpression(body);
        if (returnExpression == null) {
            return;
        }

        Object value = ConstantEvaluator.evaluate(returnExpression);
        if (Boolean.TRUE.equals(value)) {
            context.report(ISSUE, node, context.getLocation(node), MESSAGE);
        }
    }

    private static UExpression getReturnExpression(UExpression body) {
        if (body instanceof UReturnExpression) {
            return ((UReturnExpression) body).getReturnExpression();
        }

        if (body instanceof UBlockExpression) {
            List<UExpression> expressions = ((UBlockExpression) body).getExpressions();
            if (expressions.size() == 1 && expressions.get(0) instanceof UReturnExpression) {
                return ((UReturnExpression) expressions.get(0)).getReturnExpression();
            }
        }

        return body;
    }
}