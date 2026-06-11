package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UReturnExpression;
import org.jetbrains.uast.USimpleNameReferenceExpression;

import java.util.Collections;
import java.util.List;

public class BadHostnameVerifierDetector extends Detector implements Detector.UastScanner {
    private static final String HOSTNAME_VERIFIER = "javax.net.ssl.HostnameVerifier";
    private static final String VERIFY_METHOD_NAME = "verify";

    @Override
    public List<String> getApplicableMethodNames() {
        return Collections.singletonList(VERIFY_METHOD_NAME);
    }

    @Override
    public void visitMethod(@NonNull JavaContext context, @NonNull UMethod method) {
        UClass containingClass = method.getContainingClass();
        if (containingClass != null && HOSTNAME_VERIFIER.equals(containingClass.getQualifiedName())) {

            final boolean[] alwaysReturnsTrue = {false};
            for (UReturnExpression returnExpr : method.getAllReturnExpressions()) {
                if (returnExpr.getValue() instanceof USimpleNameReferenceExpression &&
                        "true".equals(((USimpleNameReferenceExpression) returnExpr.getValue()).getIdentifier())) {
                    alwaysReturnsTrue[0] = true;
                    break;
                }
            }

            if (alwaysReturnsTrue[0]) {
                context.report(ISSUE, method, context.getLocation(method),
                        "Insecure HostnameVerifier implementation that always returns true");
            }
        }
    }

    private static final Issue ISSUE = Issue.create(
            "BadHostnameVerifier",
            "Insecure HostnameVerifier implementation that always returns true",
            "Implementations of `HostnameVerifier` whose `verify` method always returns true could result in insecure network traffic caused by trusting arbitrary hostnames in TLS/SSL certificates presented by peers.",
            Category.SECURITY,
            5,
            Severity.WARNING,
            new Implementation(
                    BadHostnameVerifierDetector.class,
                    Scope.JAVA_FILE_SCOPE
            )
    );
}