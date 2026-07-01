package com.android.tools.lint.checks;

import com.android.tools.lint.client.api.UElementHandler;
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
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UParameter;
import org.jetbrains.uast.UReturnExpression;
import org.jetbrains.uast.util.UastUtils;

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
    ).addMoreInfo("https://goo.gle/BadHostnameVerifier");

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

                UClass containingClass = node.getContainingUClass();
                if (containingClass == null) {
                    return;
                }

                JavaEvaluator evaluator = context.getEvaluator();
                if (!evaluator.implementsInterface(containingClass, "javax.net.ssl.HostnameVerifier", true)) {
                    return;
                }

                List<UParameter> parameters = node.getUastParameters();
                if (parameters.size() != 2) {
                    return;
                }

                UExpression body = node.getUastBody();
                if (body == null) {
                    return;
                }

                if (alwaysReturnsTrue(context, body)) {
                    context.report(
                            ISSUE,
                            node,
                            context.getLocation(node),
                            "HostnameVerifier.verify() always returns true; this disables hostname verification and is insecure"
                    );
                }
            }
        };
    }

    private static boolean alwaysReturnsTrue(JavaContext context, UExpression body) {
        if (body instanceof UBlockExpression) {
            UBlockExpression block = (UBlockExpression) body;
            List<UReturnExpression> returns = UastUtils.findAllDescendants(block, UReturnExpression.class);
            if (returns.isEmpty()) {
                return false;
            }
            for (UReturnExpression ret : returns) {
                UExpression value = ret.getReturnExpression();
                if (value == null || !isLiteralTrue(context, value)) {
                    return false;
                }
            }
            return true;
        } else {
            return isLiteralTrue(context, body);
        }
    }

    private static boolean isLiteralTrue(JavaContext context, UExpression expr) {
        Object value = context.getEvaluator().evaluate(expr);
        return Boolean.TRUE.equals(value);
    }
}