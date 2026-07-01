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
import com.intellij.psi.PsiMethod;
import java.util.Collections;
import java.util.List;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.ULiteralExpression;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UReturnExpression;
import org.jetbrains.uast.UThrowExpression;

public class BadHostnameVerifierDetector extends Detector implements SourceCodeScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(BadHostnameVerifierDetector.class, Scope.JAVA_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                            "BadHostnameVerifier",
                            "Insecure HostnameVerifier",
                            "This check looks for implementations of `HostnameVerifier` whose "
                                    + "`verify` method always returns true (thus trusting any "
                                    + "hostname) which could result in insecure network traffic caused "
                                    + "by trusting arbitrary hostnames in TLS/SSL certificates "
                                    + "presented by peers.",
                            Category.SECURITY,
                            6,
                            Severity.WARNING,
                            IMPLEMENTATION)
                    .setAndroidSpecific(true);

    private boolean mReturnsTrue;
    private boolean mReturnsFalse;

    public BadHostnameVerifierDetector() {}

    @Nullable
    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList("javax.net.ssl.HostnameVerifier");
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        if (declaration.isInterface()) {
            return;
        }
        if (context.getEvaluator().isAbstract(declaration)) {
            return;
        }

        for (PsiMethod method : declaration.getMethods()) {
            if ("verify".equals(method.getName()) && method.getParameterList().getParametersCount() == 2) {
                UMethod uMethod = context.getUastContext().getMethod(method);
                if (uMethod != null) {
                    mReturnsTrue = false;
                    mReturnsFalse = false;

                    uMethod.accept(new org.jetbrains.uast.visitor.AbstractUastVisitor() {
                        @Override
                        public boolean visitReturnExpression(@NonNull UReturnExpression node) {
                            BadHostnameVerifierDetector.this.visitReturnExpression(context, node);
                            return super.visitReturnExpression(node);
                        }

                        @Override
                        public boolean visitThrowExpression(@NonNull UThrowExpression node) {
                            BadHostnameVerifierDetector.this.visitThrowExpression(context, node);
                            return super.visitThrowExpression(node);
                        }

                        @Override
                        public boolean visitCallExpression(@NonNull UCallExpression node) {
                            BadHostnameVerifierDetector.this.visitCallExpression(context, node);
                            return super.visitCallExpression(node);
                        }
                    });

                    if (mReturnsTrue && !mReturnsFalse) {
                        context.report(
                                ISSUE,
                                method,
                                context.getNameLocation(method),
                                "Using the default `HostnameVerifier` implementation "
                                        + "that always returns true trusts all hostnames, "
                                        + "making the connection vulnerable to man-in-the-middle attacks. "
                                        + "See https://goo.gle/BadHostnameVerifier for more information.");
                    }
                }
            }
        }
    }

    public void visitThrowExpression(@NonNull JavaContext context, @NonNull UThrowExpression node) {
        mReturnsFalse = true;
    }

    public void visitCallExpression(@NonNull JavaContext context, @NonNull UCallExpression node) {
        // No-op, implemented for API specification compliance
    }

    public void visitReturnExpression(@NonNull JavaContext context, @NonNull UReturnExpression node) {
        UExpression expression = node.getReturnExpression();
        if (expression instanceof ULiteralExpression) {
            Object value = ((ULiteralExpression) expression).getValue();
            if (Boolean.TRUE.equals(value)) {
                mReturnsTrue = true;
            } else if (Boolean.FALSE.equals(value)) {
                mReturnsFalse = true;
            }
        } else if (expression != null) {
            mReturnsFalse = true;
        }
    }
}