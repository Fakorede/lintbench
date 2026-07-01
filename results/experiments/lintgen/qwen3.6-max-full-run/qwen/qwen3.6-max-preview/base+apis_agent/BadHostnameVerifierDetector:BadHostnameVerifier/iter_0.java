package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.JavaEvaluator;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.intellij.psi.PsiType;
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

public class BadHostnameVerifierDetector extends Detector implements SourceCodeScanner {

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
            new Implementation(BadHostnameVerifierDetector.class, Scope.JAVA_FILE)
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

                if (node.getUastParameters().size() != 2) {
                    return;
                }

                PsiType returnType = node.getReturnType();
                if (returnType == null || !returnType.equals(PsiType.BOOLEAN)) {
                    return;
                }

                UClass containingClass = UastUtils.getContainingUClass(node);
                if (containingClass == null) {
                    return;
                }

                JavaEvaluator evaluator = context.getEvaluator();
                boolean isHostnameVerifier = evaluator.implementsInterface(containingClass, "javax.net.ssl.HostnameVerifier", false)
                        || evaluator.implementsInterface(containingClass, "org.apache.http.conn.ssl.HostnameVerifier", false);

                if (!isHostnameVerifier) {
                    return;
                }

                UExpression body = node.getUastBody();
                if (body == null) {
                    return;
                }

                if (alwaysReturnsTrue(body)) {
                    context.report(ISSUE, node, context.getLocation(node),
                            "Insecure `HostnameVerifier` implementation: `verify()` always returns `true`");
                }
            }
        };
    }

    private static boolean alwaysReturnsTrue(UExpression body) {
        if (body instanceof UReturnExpression) {
            return isLiteralTrue(((UReturnExpression) body).getReturnExpression());
        }
        if (body instanceof UBlockExpression) {
            List<UExpression> statements = ((UBlockExpression) body).getExpressions();
            if (statements.size() == 1 && statements.get(0) instanceof UReturnExpression) {
                return isLiteralTrue(((UReturnExpression) statements.get(0)).getReturnExpression());
            }
        }
        return false;
    }

    private static boolean isLiteralTrue(UExpression expression) {
        if (expression instanceof ULiteralExpression) {
            Object value = ((ULiteralExpression) expression).getValue();
            return Boolean.TRUE.equals(value);
        }
        return false;
    }
}