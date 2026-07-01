package com.android.tools.lint.checks;

import com.android.tools.lint.client.api.JavaEvaluator;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiType;
import java.util.Collections;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.uast.UBlockExpression;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UElementHandler;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UParameter;
import org.jetbrains.uast.UReturnExpression;

public class BadHostnameVerifierDetector extends Detector implements SourceCodeScanner {

    private static final String HOSTNAME_VERIFIER = "javax.net.ssl.HostnameVerifier";
    private static final String VERIFY_METHOD_NAME = "verify";

    @Override
    @NotNull
    public List<Class<? extends UElement>> getApplicableUElementTypes() {
        return Collections.<Class<? extends UElement>>singletonList(UMethod.class);
    }

    @Override
    @NotNull
    public UElementHandler createUElementHandler(@NotNull final JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitMethod(@NotNull UMethod node) {
                if (!VERIFY_METHOD_NAME.equals(node.getName())) {
                    return;
                }

                PsiClass containingClass = node.getContainingClass();
                if (containingClass == null) {
                    return;
                }

                JavaEvaluator evaluator = context.getEvaluator();
                if (!evaluator.implementsInterface(containingClass, HOSTNAME_VERIFIER, false)) {
                    return;
                }

                List<UParameter> parameters = node.getUastParameters();
                if (parameters.size() != 2) {
                    return;
                }

                if (!PsiType.BOOLEAN.equals(node.getReturnType())) {
                    return;
                }

                UExpression body = node.getUastBody();
                UReturnExpression returnExpression = getOnlyReturnExpression(body);
                if (returnExpression == null) {
                    return;
                }

                UExpression returnValue = returnExpression.getReturnExpression();
                if (returnValue != null && Boolean.TRUE.equals(returnValue.evaluate())) {
                    context.report(
                            ISSUE,
                            node,
                            context.getLocation(node),
                            "Returning `true` from `HostnameVerifier.verify` will trust any hostname, "
                                    + "which allows insecure TLS/SSL connections");
                }
            }
        };
    }

    private static UReturnExpression getOnlyReturnExpression(UExpression body) {
        if (body instanceof UReturnExpression) {
            return (UReturnExpression) body;
        }
        if (body instanceof UBlockExpression) {
            List<UExpression> expressions = ((UBlockExpression) body).getExpressions();
            if (expressions.size() == 1 && expressions.get(0) instanceof UReturnExpression) {
                return (UReturnExpression) expressions.get(0);
            }
        }
        return null;
    }

    public static final Issue ISSUE = Issue.create(
            "BadHostnameVerifier",
            "Insecure HostnameVerifier",
            "This `HostnameVerifier` implementation's `verify` method always returns true, "
                    + "which trusts any hostname presented in TLS/SSL certificates. "
                    + "This can allow man-in-the-middle attacks.",
            Category.SECURITY,
            6,
            Severity.WARNING,
            new Implementation(BadHostnameVerifierDetector.class, Scope.JAVA_FILE_SCOPE));
}