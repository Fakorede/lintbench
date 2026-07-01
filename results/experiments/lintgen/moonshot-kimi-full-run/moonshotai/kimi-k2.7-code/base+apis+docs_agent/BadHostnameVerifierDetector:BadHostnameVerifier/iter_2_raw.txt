package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.ConstantEvaluator;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiType;
import java.util.Collections;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.ULambdaExpression;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UReturnExpression;
import org.jetbrains.uast.visitor.AbstractUastVisitor;

public class BadHostnameVerifierDetector extends Detector implements SourceCodeScanner {

    private static final String HOSTNAME_VERIFIER = "javax.net.ssl.HostnameVerifier";
    private static final String VERIFY = "verify";
    private static final String STRING_TYPE = "java.lang.String";
    private static final String SSL_SESSION_TYPE = "javax.net.ssl.SSLSession";

    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList(HOSTNAME_VERIFIER);
    }

    @Override
    public void visitClass(@NotNull JavaContext context, @NotNull UClass declaration) {
        for (UMethod method : declaration.getMethods()) {
            if (isVerifyMethod(method) && alwaysReturnsTrue(context, method.getUastBody())) {
                Location location = context.getLocation(method);
                context.report(
                        ISSUE,
                        location,
                        "Insecure HostnameVerifier: verify() always returns true, trusting any hostname");
            }
        }
    }

    @Override
    public void visitClass(@NotNull JavaContext context, @NotNull ULambdaExpression lambda) {
        UExpression body = lambda.getBody();
        if (body != null && alwaysReturnsTrue(context, body)) {
            Location location = context.getLocation(lambda);
            context.report(
                    ISSUE,
                    location,
                    "Insecure HostnameVerifier: verify() always returns true, trusting any hostname");
        }
    }

    private boolean isVerifyMethod(@NotNull UMethod method) {
        if (!VERIFY.equals(method.getName())) {
            return false;
        }
        if (!PsiType.BOOLEAN.equals(method.getReturnType())) {
            return false;
        }
        PsiParameter[] parameters = method.getParameterList().getParameters();
        if (parameters.length != 2) {
            return false;
        }
        return STRING_TYPE.equals(parameters[0].getType().getCanonicalText(false))
                && SSL_SESSION_TYPE.equals(parameters[1].getType().getCanonicalText(false));
    }

    private boolean alwaysReturnsTrue(@NotNull JavaContext context, @NotNull UExpression body) {
        final boolean[] foundReturn = new boolean[1];
        final boolean[] allTrue = new boolean[1];
        allTrue[0] = true;

        body.accept(new AbstractUastVisitor() {
            @Override
            public boolean visitReturnExpression(@NotNull UReturnExpression node) {
                foundReturn[0] = true;
                UExpression returnValue = node.getReturnExpression();
                if (returnValue != null) {
                    Object value = ConstantEvaluator.evaluate(context, returnValue);
                    if (!Boolean.TRUE.equals(value)) {
                        allTrue[0] = false;
                    }
                } else {
                    allTrue[0] = false;
                }
                return super.visitReturnExpression(node);
            }

            @Override
            public boolean visitClass(@NotNull UClass node) {
                return false;
            }

            @Override
            public boolean visitLambdaExpression(@NotNull ULambdaExpression node) {
                return false;
            }
        });

        if (!foundReturn[0]) {
            Object value = ConstantEvaluator.evaluate(context, body);
            return Boolean.TRUE.equals(value);
        }

        return allTrue[0];
    }

    public static final Issue ISSUE =
            Issue.create(
                    "BadHostnameVerifier",
                    "Insecure HostnameVerifier",
                    "A HostnameVerifier whose verify() method always returns true disables "
                            + "hostname verification in TLS/SSL connections. This allows an attacker "
                            + "to present a certificate for any hostname and have it accepted, "
                            + "resulting in insecure network traffic. Perform proper hostname "
                            + "verification instead of trusting arbitrary hostnames.",
                    Category.SECURITY,
                    9,
                    Severity.WARNING,
                    new Implementation(BadHostnameVerifierDetector.class, Scope.JAVA_FILE_SCOPE));
}