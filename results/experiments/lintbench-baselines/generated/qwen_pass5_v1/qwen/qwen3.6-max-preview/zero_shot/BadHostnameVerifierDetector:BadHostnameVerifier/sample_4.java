package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.JavaEvaluator;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.intellij.psi.PsiType;
import org.jetbrains.uast.UBlockExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.ULambdaExpression;
import org.jetbrains.uast.ULiteralExpression;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UParameter;
import org.jetbrains.uast.UReturnExpression;
import org.jetbrains.uast.UastUtils;
import org.jetbrains.uast.visitor.AbstractUastVisitor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class BadHostnameVerifierDetector extends Detector implements Detector.UastScanner {

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
            new Implementation(BadHostnameVerifierDetector.class, Scope.JAVA_FILE_SCOPE)
    );

    @Override
    public List<String> getApplicableMethodNames() {
        return Collections.singletonList("verify");
    }

    @Override
    public void visitMethod(JavaContext context, UMethod method) {
        PsiType returnType = method.getReturnType();
        if (returnType == null || !PsiType.BOOLEAN.equals(returnType)) {
            return;
        }

        List<UParameter> parameters = method.getUastParameters();
        if (parameters.size() != 2) {
            return;
        }

        boolean isHostnameVerifier = false;
        JavaEvaluator evaluator = context.getEvaluator();

        UClass containingClass = UastUtils.getContainingUClass(method);
        if (containingClass != null) {
            isHostnameVerifier = evaluator.implementsInterface(containingClass, "javax.net.ssl.HostnameVerifier", false);
        }

        if (!isHostnameVerifier && method.getUastParent() instanceof ULambdaExpression) {
            ULambdaExpression lambda = (ULambdaExpression) method.getUastParent();
            PsiType functionalType = lambda.getFunctionalInterfaceType();
            if (functionalType != null) {
                isHostnameVerifier = "javax.net.ssl.HostnameVerifier".equals(functionalType.getCanonicalText());
            }
        }

        if (!isHostnameVerifier) {
            return;
        }

        if (alwaysReturnsTrue(method)) {
            context.report(ISSUE, method, context.getLocation(method),
                    "Insecure `HostnameVerifier`: `verify()` always returns `true`");
        }
    }

    private static boolean alwaysReturnsTrue(UMethod method) {
        UExpression body = method.getUastBody();
        if (body == null) {
            return false;
        }

        if (body instanceof ULiteralExpression) {
            return Boolean.TRUE.equals(((ULiteralExpression) body).getValue());
        }

        if (body instanceof UBlockExpression) {
            final List<UReturnExpression> returnExpressions = new ArrayList<>();
            body.accept(new AbstractUastVisitor() {
                @Override
                public boolean visitReturnExpression(UReturnExpression node) {
                    returnExpressions.add(node);
                    return true;
                }
            });

            if (returnExpressions.isEmpty()) {
                return false;
            }

            for (UReturnExpression ret : returnExpressions) {
                UExpression value = ret.getReturnExpression();
                if (!(value instanceof ULiteralExpression) ||
                        !Boolean.TRUE.equals(((ULiteralExpression) value).getValue())) {
                    return false;
                }
            }
            return true;
        }

        return false;
    }
}