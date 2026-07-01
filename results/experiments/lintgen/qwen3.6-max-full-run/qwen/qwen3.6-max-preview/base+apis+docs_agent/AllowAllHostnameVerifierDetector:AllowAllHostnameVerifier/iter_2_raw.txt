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
import org.jetbrains.uast.UBlockExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.ULambdaExpression;
import org.jetbrains.uast.ULiteralExpression;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UParenthesizedExpression;
import org.jetbrains.uast.UReturnExpression;

import java.util.Arrays;
import java.util.List;

public class AllowAllHostnameVerifierDetector extends Detector implements SourceCodeScanner {

    public static final Issue ISSUE = Issue.create(
            "AllowAllHostnameVerifier",
            "Insecure HostnameVerifier",
            "This check looks for use of HostnameVerifier implementations whose `verify` method always returns true (thus trusting any hostname) which could result in insecure network traffic caused by trusting arbitrary hostnames in TLS/SSL certificates presented by peers.",
            Category.SECURITY,
            8,
            Severity.WARNING,
            new Implementation(AllowAllHostnameVerifierDetector.class, Scope.JAVA_FILE_SCOPE)
    );

    private static final String HOSTNAME_VERIFIER = "javax.net.ssl.HostnameVerifier";

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Arrays.asList(UMethod.class, ULambdaExpression.class);
    }

    @Override
    public UElementHandler createUastHandler(JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitMethod(UMethod node) {
                if (!"verify".equals(node.getName())) return;
                if (node.getUastParameters().size() != 2) return;

                UClass containingClass = node.getContainingUClass();
                if (containingClass == null) return;

                if (context.getEvaluator().implementsInterface(containingClass, HOSTNAME_VERIFIER, true)) {
                    if (alwaysReturnsTrue(node.getUastBody())) {
                        context.report(ISSUE, node, context.getLocation(node),
                                "HostnameVerifier always returns true, which trusts all hostnames and disables SSL hostname verification.");
                    }
                }
            }

            @Override
            public void visitLambdaExpression(ULambdaExpression node) {
                if (node.getValueParameters().size() != 2) return;

                PsiMethod functionalMethod = node.getFunctionalInterfaceMethod();
                if (functionalMethod != null && functionalMethod.getContainingClass() != null) {
                    if (HOSTNAME_VERIFIER.equals(functionalMethod.getContainingClass().getQualifiedName())) {
                        if (alwaysReturnsTrue(node.getBody())) {
                            context.report(ISSUE, node, context.getLocation(node),
                                    "HostnameVerifier always returns true, which trusts all hostnames and disables SSL hostname verification.");
                        }
                    }
                }
            }
        };
    }

    private static boolean alwaysReturnsTrue(UElement body) {
        if (body == null) return false;

        UExpression expr = null;
        if (body instanceof UExpression) {
            expr = (UExpression) body;
        } else if (body instanceof UBlockExpression) {
            List<UExpression> statements = ((UBlockExpression) body).getExpressions();
            for (int i = statements.size() - 1; i >= 0; i--) {
                UExpression stmt = statements.get(i);
                if (stmt instanceof UReturnExpression) {
                    expr = ((UReturnExpression) stmt).getReturnExpression();
                    break;
                }
            }
        }

        if (expr != null) {
            expr = unwrap(expr);
            if (expr instanceof ULiteralExpression) {
                return Boolean.TRUE.equals(((ULiteralExpression) expr).getValue());
            }
        }
        return false;
    }

    private static UExpression unwrap(UExpression expr) {
        while (expr instanceof UParenthesizedExpression) {
            expr = ((UParenthesizedExpression) expr).getExpression();
        }
        return expr;
    }
}