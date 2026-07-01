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
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UReturnExpression;
import org.jetbrains.uast.UThrowExpression;
import org.jetbrains.uast.UastLiteralUtils;
import org.jetbrains.uast.visitor.AbstractUastVisitor;

public class BadHostnameVerifierDetector extends Detector implements SourceCodeScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(BadHostnameVerifierDetector.class, Scope.JAVA_FILE_SCOPE);

    private static final String HOSTNAME_VERIFIER = "javax.net.ssl.HostnameVerifier";
    private static final String VERIFY_METHOD = "verify";

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
                    .addMoreInfo("https://goo.gle/BadHostnameVerifier")
                    .setAndroidSpecific(true);

    public BadHostnameVerifierDetector() {}

    @Nullable
    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList(HOSTNAME_VERIFIER);
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        for (PsiMethod method : declaration.getMethods()) {
            if (VERIFY_METHOD.equals(method.getName())) {
                UMethod uMethod = context.getUastContext().getMethod(method);
                if (uMethod != null) {
                    VerifyMethodVisitor visitor = new VerifyMethodVisitor(context, uMethod);
                    uMethod.accept(visitor);
                    if (visitor.alwaysReturnsTrue()) {
                        context.report(
                                ISSUE,
                                uMethod,
                                context.getNameLocation(uMethod),
                                "`verify` always returns `true`, which could cause insecure "
                                        + "network traffic due to trusting arbitrary hostnames");
                    }
                }
            }
        }
    }

    @Override
    public void visitThrowExpression(
            @NonNull JavaContext context, @NonNull UThrowExpression node) {
        // Not used at the top level; handled within VerifyMethodVisitor
    }

    @Override
    public void visitCallExpression(
            @NonNull JavaContext context, @NonNull UCallExpression node) {
        // Not used at the top level; handled within VerifyMethodVisitor
    }

    @Override
    public void visitReturnExpression(
            @NonNull JavaContext context, @NonNull UReturnExpression node) {
        // Not used at the top level; handled within VerifyMethodVisitor
    }

    /**
     * Visitor that analyzes the body of a verify() method to determine if it
     * unconditionally returns true without any possibility of returning false
     * or throwing an exception.
     */
    private static class VerifyMethodVisitor extends AbstractUastVisitor {
        private final JavaContext mContext;
        private final UMethod mMethod;
        private boolean mFoundReturnTrue = false;
        private boolean mFoundReturnFalse = false;
        private boolean mFoundThrow = false;

        VerifyMethodVisitor(@NonNull JavaContext context, @NonNull UMethod method) {
            mContext = context;
            mMethod = method;
        }

        @Override
        public boolean visitReturnExpression(@NonNull UReturnExpression node) {
            if (node.getReturnExpression() != null) {
                Object value = node.getReturnExpression().evaluate();
                if (Boolean.TRUE.equals(value)) {
                    mFoundReturnTrue = true;
                } else if (Boolean.FALSE.equals(value)) {
                    mFoundReturnFalse = true;
                } else {
                    // Non-literal or non-constant return value — treat as non-trivially-true
                    mFoundReturnFalse = true;
                }
            }
            return super.visitReturnExpression(node);
        }

        @Override
        public boolean visitThrowExpression(@NonNull UThrowExpression node) {
            mFoundThrow = true;
            return super.visitThrowExpression(node);
        }

        @Override
        public boolean visitCallExpression(@NonNull UCallExpression node) {
            return super.visitCallExpression(node);
        }

        boolean alwaysReturnsTrue() {
            return mFoundReturnTrue && !mFoundReturnFalse && !mFoundThrow;
        }
    }
}