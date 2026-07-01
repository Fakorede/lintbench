package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.JavaEvaluator;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;

import org.jetbrains.uast.UBlockExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.ULiteralExpression;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UParameter;
import org.jetbrains.uast.UReturnExpression;

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
        UClass containingClass = method.getContainingUClass();
        if (containingClass == null) {
            return;
        }

        JavaEvaluator evaluator = context.getEvaluator();
        if (!evaluator.implementsInterface(containingClass, "javax.net.ssl.HostnameVerifier", false)) {
            return;
        }

        List<UParameter> parameters = method.getUastParameters();
        if (parameters.size() != 2) {
            return;
        }

        UExpression body = method.getUastBody();
        if (returnsTrue(body)) {
            context.report(ISSUE, method, context.getLocation(method),
                    "Insecure HostnameVerifier implementation: verify() always returns true");
        }
    }

    private static boolean returnsTrue(UExpression body) {
        if (body == null) {
            return false;
        }

        UExpression returnExpr = null;
        if (body instanceof UReturnExpression) {
            returnExpr = ((UReturnExpression) body).getReturnExpression();
        } else if (body instanceof UBlockExpression) {
            List<UExpression> statements = ((UBlockExpression) body).getExpressions();
            if (statements.size() == 1 && statements.get(0) instanceof UReturnExpression) {
                returnExpr = ((UReturnExpression) statements.get(0)).getReturnExpression();
            }
        }

        if (returnExpr instanceof ULiteralExpression) {
            Object value = ((ULiteralExpression) returnExpr).getValue();
            return Boolean.TRUE.equals(value);
        }

        return false;
    }
}