package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.*;
import org.jetbrains.uast.visitor.UElementHandler;

import java.util.Collections;
import java.util.List;

public class AllowAllHostnameVerifierDetector extends Detector implements Detector.UastScanner {

    public static final Issue ISSUE = Issue.create(
            "AllowAllHostnameVerifier",
            "Insecure HostnameVerifier",
            "This check looks for use of HostnameVerifier implementations whose `verify` method " +
            "always returns true (thus trusting any hostname) which could result in insecure " +
            "network traffic caused by trusting arbitrary hostnames in TLS/SSL certificates " +
            "presented by peers.",
            Category.SECURITY,
            6,
            Severity.WARNING,
            new Implementation(AllowAllHostnameVerifierDetector.class, Scope.JAVA_FILE_SCOPE)
    );

    @Nullable
    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.singletonList(UMethod.class);
    }

    @Nullable
    @Override
    public UElementHandler createUastHandler(@NotNull JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitMethod(@NotNull UMethod node) {
                if (!"verify".equals(node.getName())) {
                    return;
                }

                JavaEvaluator evaluator = context.getEvaluator();
                if (!evaluator.overrides(node, "javax.net.ssl.HostnameVerifier", "verify", false)) {
                    return;
                }

                UExpression body = node.getUastBody();
                if (body == null) {
                    return;
                }

                UExpression returnValue = null;
                if (body instanceof UBlockExpression) {
                    List<UExpression> statements = ((UBlockExpression) body).getExpressions();
                    if (statements.size() == 1 && statements.get(0) instanceof UReturnExpression) {
                        returnValue = ((UReturnExpression) statements.get(0)).getReturnExpression();
                    }
                } else if (body instanceof UReturnExpression) {
                    returnValue = ((UReturnExpression) body).getReturnExpression();
                } else if (body instanceof ULiteralExpression) {
                    returnValue = body;
                }

                if (returnValue instanceof ULiteralExpression &&
                        Boolean.TRUE.equals(((ULiteralExpression) returnValue).getValue())) {
                    context.report(ISSUE, node, context.getLocation(node),
                            "HostnameVerifier verify() always returns true");
                }
            }
        };
    }
}