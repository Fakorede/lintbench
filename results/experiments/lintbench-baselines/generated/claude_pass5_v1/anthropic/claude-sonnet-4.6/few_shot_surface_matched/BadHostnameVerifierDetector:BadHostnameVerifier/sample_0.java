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
import org.jetbrains.uast.UBlockExpression;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.ULiteralExpression;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UReturnExpression;
import org.jetbrains.uast.UThrowExpression;
import org.jetbrains.uast.visitor.AbstractUastVisitor;

public class BadHostnameVerifierDetector extends Detector implements SourceCodeScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(BadHostnameVerifierDetector.class, Scope.JAVA_FILE_SCOPE);

    private static final String HOSTNAME_VERIFIER = "javax.net.ssl.HostnameVerifier";

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
        for (PsiMethod psiMethod : declaration.getMethods()) {
            if ("verify".equals(psiMethod.getName())) {
                UMethod uMethod = context.getUastContext().getMethod(psiMethod);
                if (uMethod != null) {
                    VerifyMethodVisitor visitor = new VerifyMethodVisitor(context, uMethod);
                    uMethod.accept(visitor);
                }
            }
        }
    }

    @Override
    public void visitThrowExpression(
            @NonNull JavaContext context, @NonNull UThrowExpression node) {
        // handled via class visitor
    }

    @Override
    public void visitCallExpression(
            @NonNull JavaContext context, @NonNull UCallExpression node) {
        // handled via class visitor
    }

    @Override
    public void visitReturnExpression(
            @NonNull JavaContext context, @NonNull UReturnExpression node) {
        // handled via class visitor
    }

    private static class VerifyMethodVisitor extends AbstractUastVisitor {
        private final JavaContext mContext;
        private final UMethod mMethod;
        private boolean mAlwaysReturnsTrue = false;
        private boolean mHasReturnFalse = false;
        private boolean mHasThrow = false;
        private boolean mHasReturnTrue = false;

        VerifyMethodVisitor(JavaContext context, UMethod method) {
            mContext = context;
            mMethod = method;
        }

        @Override
        public boolean visitMethod(UMethod node) {
            // Only visit the top-level verify method, not nested methods/lambdas
            return node != mMethod;
        }

        @Override
        public boolean visitReturnExpression(UReturnExpression node) {
            UExpression returnValue = node.getReturnExpression();
            if (returnValue instanceof ULiteralExpression) {
                Object value = ((ULiteralExpression) returnValue).getValue();
                if (Boolean.TRUE.equals(value)) {
                    mHasReturnTrue = true;
                } else if (Boolean.FALSE.equals(value)) {
                    mHasReturnFalse = true;
                }
            } else if (returnValue != null) {
                // Non-literal return - could be conditional, treat conservatively
                // We still flag if there's also a return true
            }
            return super.visitReturnExpression(node);
        }

        @Override
        public boolean visitThrowExpression(UThrowExpression node) {
            mHasThrow = true;
            return super.visitThrowExpression(node);
        }

        public void check() {
            // Analyze the method body
            UExpression body = mMethod.getUastBody();
            if (body == null) {
                return;
            }

            // Reset state and re-analyze
            mHasReturnTrue = false;
            mHasReturnFalse = false;
            mHasThrow = false;

            body.accept(this);

            // If we found a return true and no return false and no throw, flag it
            if (mHasReturnTrue && !mHasReturnFalse && !mHasThrow) {
                mContext.report(
                        ISSUE,
                        mMethod,
                        mContext.getNameLocation(mMethod),
                        "`verify` always returns `true`, which could cause insecure network "
                                + "traffic due to trusting arbitrary hostnames in TLS/SSL "
                                + "certificates presented by peers");
            }
        }
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        for (PsiMethod psiMethod : declaration.getMethods()) {
            if ("verify".equals(psiMethod.getName())
                    && psiMethod.getParameterList().getParametersCount() == 2) {
                UMethod uMethod = context.getUastContext().getMethod(psiMethod);
                if (uMethod != null) {
                    VerifyMethodVisitor visitor = new VerifyMethodVisitor(context, uMethod);
                    visitor.check();
                }
            }
        }
    }
}