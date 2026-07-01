package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.UElementHandler;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.uast.UBlockExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.ULambdaExpression;
import org.jetbrains.uast.ULiteralExpression;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UReturnExpression;

import java.util.Arrays;
import java.util.List;

public class AllowAllHostnameVerifierDetector extends Detector implements SourceCodeScanner {
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
        return Arrays.asList(UMethod.class, ULambdaExpression.class);
    }

    @Override
    public UElementHandler createUastHandler(@NotNull JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitMethod(UMethod method) {
                if (!"verify".equals(method.getName())) return;
                if (method.getUastParameters().size() != 2) return;

                UClass containingClass = method.getContainingClass();
                if (containingClass == null) return;

                if (!context.getEvaluator().implementsInterface(containingClass, HOSTNAME_VERIFIER, false)) {
                    return;
                }

                if (returnsTrue(method.getUastBody())) {
                    context.report(ISSUE, method, context.getNameLocation(method),
                        "Insecure HostnameVerifier implementation: verify() always returns true");
                }
            }

            @Override
            public void visitLambdaExpression(ULambdaExpression node) {
                if (node.getFunctionalInterfaceType() == null) return;
                String canonicalText = node.getFunctionalInterfaceType().getCanonicalText();
                if (!HOSTNAME_VERIFIER.equals(canonicalText)) return;

                if (returnsTrue(node.getBody())) {
                    context.report(ISSUE, node, context.getLocation(node),
                        "Insecure HostnameVerifier implementation: verify() always returns true");
                }
            }
        };
    }

    private static boolean returnsTrue(UExpression body) {
        if (body == null) return false;
        UExpression expr = body;
        if (body instanceof UBlockExpression) {
            List<UExpression> statements = ((UBlockExpression) body).getExpressions();
            if (statements.size() == 1 && statements.get(0) instanceof UReturnExpression) {
                expr = ((UReturnExpression) statements.get(0)).getReturnExpression();
            } else {
                return false;
            }
        } else if (body instanceof UReturnExpression) {
            expr = ((UReturnExpression) body).getReturnExpression();
        }

        if (expr instanceof ULiteralExpression) {
            Object value = ((ULiteralExpression) expr).getValue();
            return Boolean.TRUE.equals(value);
        }
        return false;
    }
}