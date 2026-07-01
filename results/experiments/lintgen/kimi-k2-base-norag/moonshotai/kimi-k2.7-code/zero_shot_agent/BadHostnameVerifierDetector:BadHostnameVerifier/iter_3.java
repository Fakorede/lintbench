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
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import java.util.Collections;
import java.util.List;
import org.jetbrains.uast.UBlockExpression;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UReturnExpression;
import org.jetbrains.uast.visitor.UElementHandler;

public class BadHostnameVerifierDetector extends Detector implements SourceCodeScanner {

    private static final String HOSTNAME_VERIFIER = "javax.net.ssl.HostnameVerifier";

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
        return Collections.singletonList(UMethod.class);
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

                UExpression returnExpression = null;
                if (body instanceof UReturnExpression) {
                    returnExpression = ((UReturnExpression) body).getReturnExpression();
                } else if (body instanceof UBlockExpression) {
                    List<UExpression> expressions = ((UBlockExpression) body).getExpressions();
                    if (expressions.size() == 1 && expressions.get(0) instanceof UReturnExpression) {
                        returnExpression = ((UReturnExpression) expressions.get(0)).getReturnExpression();
                    }
                }

                if (returnExpression == null) {
                    return;
                }

                Object value = new ConstantEvaluator().evaluate(returnExpression);
                if (Boolean.TRUE.equals(value)) {
                    context.report(
                            ISSUE,
                            node,
                            context.getLocation(node),
                            "Insecure HostnameVerifier: `verify` always returns true, trusting any hostname. "
                                    + "This may allow insecure network traffic due to trusting arbitrary "
                                    + "hostnames in TLS/SSL certificates presented by peers."
                    );
                }
            }
        };
    }
}