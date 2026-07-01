package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import java.util.Collections;
import java.util.List;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.ULiteralExpression;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UReturnExpression;
import org.jetbrains.uast.UThrowExpression;
import org.jetbrains.uast.UastUtils;

public class BadHostnameVerifierDetector extends Detector implements SourceCodeScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(BadHostnameVerifierDetector.class, Scope.JAVA_FILE_SCOPE);

    private static final String HOSTNAME_VERIFIER = "javax.net.ssl.HostnameVerifier";

    public static final Issue ISSUE = Issue.create(
            "BadHostnameVerifier",
            "Insecure HostnameVerifier",
            "This check looks for implementations of `HostnameVerifier` whose `verify` method "
                    + "always returns true (thus trusting any hostname) which could result in "
                    + "insecure network traffic caused by trusting arbitrary hostnames in TLS/SSL "
                    + "certificates presented by peers.",
            Category.SECURITY,
            6,
            Severity.WARNING,
            IMPLEMENTATION
    );

    @Nullable
    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList(HOSTNAME_VERIFIER);
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        // Classes implementing HostnameVerifier are filtered by applicableSuperClasses().
        // Expression visitors will analyze the verify method body.
    }

    @Override
    public void visitReturnExpression(@NonNull JavaContext context, @NonNull UReturnExpression node) {
        UMethod method = node.getContainingUMethod();
        if (method == null || !"verify".equals(method.getName())) {
            return;
        }

        UClass cls = UastUtils.getContainingUClass(node);
        if (cls == null || !context.getEvaluator().implementsInterface(cls, HOSTNAME_VERIFIER)) {
            return;
        }

        UExpression returnValue = node.getReturnExpression();
        if (returnValue instanceof ULiteralExpression) {
            ULiteralExpression literal = (ULiteralExpression) returnValue;
            if (Boolean.TRUE.equals(literal.getValue())) {
                context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        "Insecure HostnameVerifier: always returning true trusts all hostnames "
                                + "and disables SSL certificate hostname verification."
                );
            }
        }
    }

    @Override
    public void visitCallExpression(@NonNull JavaContext context, @NonNull UCallExpression node) {
        // Overridden as part of the scanner contract.
        // Presence of method calls inside verify() typically indicates validation logic,
        // so we do not flag trivial bypasses when calls are present.
    }

    @Override
    public void visitThrowExpression(@NonNull JavaContext context, @NonNull UThrowExpression node) {
        // Overridden as part of the scanner contract.
        // Throwing exceptions inside verify() indicates active validation/rejection logic,
        // distinguishing it from a trivial always-true bypass.
    }
}