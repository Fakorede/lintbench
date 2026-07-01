package com.android.tools.lint.checks;

import com.android.tools.lint.client.api.JavaEvaluator;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.ConstantEvaluator;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import java.util.Collections;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UReturnExpression;
import org.jetbrains.uast.UastUtils;
import org.jetbrains.uast.visitor.AbstractUastVisitor;
import org.jetbrains.uast.visitor.UElementHandler;

public final class AllowAllHostnameVerifierDetector extends Detector implements SourceCodeScanner {

    private static final String HOSTNAME_VERIFIER = "javax.net.ssl.HostnameVerifier";
    private static final String X509_HOSTNAME_VERIFIER = "org.apache.http.conn.ssl.X509HostnameVerifier";

    private static final Issue ISSUE = Issue.create(
            "AllowAllHostnameVerifier",
            "Insecure HostnameVerifier",
            "This check looks for use of HostnameVerifier implementations whose verify method "
                    + "always returns true (thus trusting any hostname) which could result in "
                    + "insecure network traffic caused by trusting arbitrary hostnames in "
                    + "TLS/SSL certificates presented by peers.",
            Category.SECURITY,
            6,
            Severity.WARNING,
            new Implementation(AllowAllHostnameVerifierDetector.class, Scope.JAVA_FILE_SCOPE))
            .setAndroidSpecific(true)
            .setMoreInfo("https://goo.gle/AllowAllHostnameVerifier");

    @Override
    @NotNull
    public List<Class<? extends UElement>> getApplicableUastNodeTypes() {
        return Collections.singletonList(UMethod.class);
    }

    @Override
    @NotNull
    public UElementHandler createUastHandler(@NotNull JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitMethod(@NotNull UMethod node) {
                AllowAllHostnameVerifierDetector.this.visitMethod(context, node);
            }
        };
    }

    private void visitMethod(@NotNull JavaContext context, @NotNull UMethod method) {
        if (!"verify".equals(method.getName())) {
            return;
        }

        UClass containingClass = UastUtils.getParentOfType(method, UClass.class, true);
        if (containingClass == null) {
            return;
        }

        JavaEvaluator evaluator = context.getEvaluator();
        if (!evaluator.implementsInterface(containingClass, HOSTNAME_VERIFIER, false)
                && !evaluator.implementsInterface(containingClass, X509_HOSTNAME_VERIFIER, false)) {
            return;
        }

        if (alwaysReturnsTrue(context, method)) {
            context.report(
                    ISSUE,
                    method,
                    context.getLocation(method),
                    "Using a HostnameVerifier that accepts all hostnames is unsafe");
        }
    }

    private static boolean alwaysReturnsTrue(@NotNull JavaContext context, @NotNull UMethod method) {
        final boolean[] foundReturn = {false};
        final boolean[] allTrue = {true};

        method.accept(new AbstractUastVisitor() {
            @Override
            public boolean visitReturnExpression(@NotNull UReturnExpression node) {
                foundReturn[0] = true;
                UExpression returnValue = node.getReturnExpression();
                if (returnValue == null) {
                    allTrue[0] = false;
                } else {
                    Object value = ConstantEvaluator.evaluate(context, returnValue);
                    if (!Boolean.TRUE.equals(value)) {
                        allTrue[0] = false;
                    }
                }
                return super.visitReturnExpression(node);
            }
        });

        return foundReturn[0] && allTrue[0];
    }
}