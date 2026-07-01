package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.UastScanner;
import org.jetbrains.uast.UBlockExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UElementHandler;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.ULiteralExpression;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UReturnExpression;
import org.jetbrains.uast.UastUtils;

import java.util.Collections;
import java.util.List;

public class BadHostnameVerifierDetector extends Detector implements UastScanner {

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
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.singletonList(UMethod.class);
    }

    @Override
    public UElementHandler createUastHandler(final JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitMethod(UMethod method) {
                if (!"verify".equals(method.getName())) {
                    return;
                }
                if (method.getUastParameters().size() != 2) {
                    return;
                }

                UClass containingClass = UastUtils.getContainingUClass(method);
                if (containingClass == null) {
                    return;
                }

                if (!context.getEvaluator().implementsInterface(containingClass, "javax.net.ssl.HostnameVerifier", true)) {
                    return;
                }

                if (alwaysReturnsTrue(method)) {
                    context.report(ISSUE, context.getLocation(method),
                            "Insecure HostnameVerifier: verify() always returns true, trusting all hostnames.");
                }
            }
        };
    }

    private static boolean alwaysReturnsTrue(UMethod method) {
        UExpression body = method.getUastBody();
        if (body == null) {
            return false;
        }

        if (body instanceof UBlockExpression) {
            List<UExpression> statements = ((UBlockExpression) body).getExpressions();
            if (statements.size() == 1) {
                return isReturnTrue(statements.get(0));
            }
            return false;
        }

        return isReturnTrue(body);
    }

    private static boolean isReturnTrue(UExpression expression) {
        if (expression instanceof UReturnExpression) {
            UExpression returnValue = ((UReturnExpression) expression).getReturnExpression();
            return isLiteralTrue(returnValue);
        }
        return isLiteralTrue(expression);
    }

    private static boolean isLiteralTrue(UExpression expression) {
        if (expression instanceof ULiteralExpression) {
            Object value = ((ULiteralExpression) expression).getValue();
            return Boolean.TRUE.equals(value);
        }
        return false;
    }
}