package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.intellij.psi.PsiType;
import java.util.Collections;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.uast.UBlockExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.ULambdaExpression;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UParameter;
import org.jetbrains.uast.UReturnExpression;

public class BadHostnameVerifierDetector extends Detector implements SourceCodeScanner {

    private static final String HOSTNAME_VERIFIER = "javax.net.ssl.HostnameVerifier";
    private static final String VERIFY_METHOD_NAME = "verify";

    @Override
    @NotNull
    public List<String> applicableSuperClasses() {
        return Collections.singletonList(HOSTNAME_VERIFIER);
    }

    @Override
    public void visitClass(@NotNull JavaContext context, @NotNull UClass declaration) {
        for (UMethod method : declaration.getMethods()) {
            if (VERIFY_METHOD_NAME.equals(method.getName())
                    && method.getUastParameters().size() == 2
                    && PsiType.BOOLEAN.equals(method.getReturnType())) {
                UExpression body = method.getUastBody();
                if (body != null && bodyReturnsTrue(body)) {
                    context.report(
                            ISSUE,
                            method,
                            context.getLocation(method),
                            "Returning `true` from `HostnameVerifier.verify` will trust any hostname, "
                                    + "which allows insecure TLS/SSL connections");
                }
            }
        }
    }

    @Override
    public void visitClass(@NotNull JavaContext context, @NotNull ULambdaExpression lambda) {
        UExpression body = lambda.getBody();
        if (body != null && bodyReturnsTrue(body)) {
            context.report(
                    ISSUE,
                    lambda,
                    context.getLocation(lambda),
                    "Returning `true` from `HostnameVerifier.verify` will trust any hostname, "
                            + "which allows insecure TLS/SSL connections");
        }
    }

    private static boolean bodyReturnsTrue(@NotNull UExpression body) {
        UExpression returnValue = getEffectiveReturnValue(body);
        if (returnValue == null) {
            return false;
        }
        Object value = returnValue.evaluate();
        return Boolean.TRUE.equals(value);
    }

    private static UExpression getEffectiveReturnValue(UExpression body) {
        if (body instanceof UReturnExpression) {
            return ((UReturnExpression) body).getReturnExpression();
        }
        if (body instanceof UBlockExpression) {
            List<UExpression> expressions = ((UBlockExpression) body).getExpressions();
            if (expressions.size() != 1) {
                return null;
            }
            return getEffectiveReturnValue(expressions.get(0));
        }
        return body;
    }

    public static final Issue ISSUE = Issue.create(
            "BadHostnameVerifier",
            "Insecure HostnameVerifier",
            "This `HostnameVerifier` implementation's `verify` method always returns true, "
                    + "which trusts any hostname presented in TLS/SSL certificates. "
                    + "This can allow man-in-the-middle attacks.",
            Category.SECURITY,
            6,
            Severity.WARNING,
            new Implementation(BadHostnameVerifierDetector.class, Scope.JAVA_FILE_SCOPE));
}