package com.android.tools.lint.checks;

import com.android.tools.lint.client.api.UElementHandler;
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
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiType;
import java.util.Collections;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.UReturnExpression;
import org.jetbrains.uast.visitor.AbstractUastVisitor;

public class BadHostnameVerifierDetector extends Detector implements SourceCodeScanner {

    private static final String HOSTNAME_VERIFIER = "javax.net.ssl.HostnameVerifier";
    private static final String VERIFY = "verify";
    private static final String STRING_TYPE = "java.lang.String";
    private static final String SSL_SESSION_TYPE = "javax.net.ssl.SSLSession";

    @Override
    public List<Class<? extends UElement>> getApplicableUElementTypes() {
        return Collections.singletonList(UMethod.class);
    }

    @Override
    public UElementHandler createUastHandler(@NotNull JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitMethod(@NotNull UMethod node) {
                checkMethod(context, node);
            }
        };
    }

    private void checkMethod(@NotNull JavaContext context, @NotNull UMethod method) {
        if (!VERIFY.equals(method.getName())) {
            return;
        }

        PsiClass containingClass = method.getContainingClass();
        if (containingClass == null) {
            return;
        }

        if (!context.getEvaluator().implementsInterface(containingClass, HOSTNAME_VERIFIER, true)) {
            return;
        }

        if (!PsiType.BOOLEAN.equals(method.getReturnType())) {
            return;
        }

        PsiParameter[] parameters = method.getParameterList().getParameters();
        if (parameters.length != 2) {
            return;
        }
        if (!STRING_TYPE.equals(parameters[0].getType().getCanonicalText(false))) {
            return;
        }
        if (!SSL_SESSION_TYPE.equals(parameters[1].getType().getCanonicalText(false))) {
            return;
        }

        if (allReturnsAreTrue(context, method)) {
            Location location = context.getLocation(method);
            context.report(
                    ISSUE,
                    location,
                    "Insecure HostnameVerifier: verify() always returns true, trusting any hostname");
        }
    }

    private boolean allReturnsAreTrue(@NotNull JavaContext context, @NotNull UMethod method) {
        final boolean[] foundReturn = new boolean[1];
        final boolean[] allTrue = new boolean[1];
        allTrue[0] = true;

        method.accept(new AbstractUastVisitor() {
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
        });

        return foundReturn[0] && allTrue[0];
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
                    new Implementation(BadHostnameVerifierDetector.class, Scope.JAVA_FILE_SCOPE),
                    "https://goo.gle/BadHostnameVerifier");
}