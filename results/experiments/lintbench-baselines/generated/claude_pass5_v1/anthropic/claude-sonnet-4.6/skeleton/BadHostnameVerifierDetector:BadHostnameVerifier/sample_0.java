package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
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

    public static final Issue ISSUE =
            Issue.create(
                    "BadHostnameVerifier",
                    "Insecure HostnameVerifier",
                    "This check looks for implementations of `HostnameVerifier` whose `verify` "
                            + "method always returns true (thus trusting any hostname) which could "
                            + "result in insecure network traffic caused by trusting arbitrary "
                            + "hostnames in TLS/SSL certificates presented by peers.",
                    Category.SECURITY,
                    6,
                    Severity.WARNING,
                    IMPLEMENTATION)
                    .addMoreInfo("https://goo.gle/BadHostnameVerifier");

    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList("javax.net.ssl.HostnameVerifier");
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        for (PsiMethod method : declaration.findMethodsByName("verify", false)) {
            // Find the UAST method
            for (UMethod uMethod : declaration.getMethods()) {
                if (uMethod.getName().equals("verify") && uMethod.getPsiMethod().equals(method)) {
                    checkVerifyMethod(context, uMethod);
                }
            }
        }
    }

    private void checkVerifyMethod(@NonNull JavaContext context, @NonNull UMethod method) {
        UExpression body = method.getUastBody();
        if (body == null) {
            return;
        }

        VerifyMethodVisitor visitor = new VerifyMethodVisitor(context);
        body.accept(visitor);
    }

    private static class VerifyMethodVisitor extends AbstractUastVisitor {
        private final JavaContext mContext;
        private boolean mAlwaysReturnsTrue = false;
        private boolean mHasReturnFalse = false;
        private boolean mHasThrow = false;
        private int mReturnTrueCount = 0;
        private int mReturnFalseCount = 0;
        private UReturnExpression mReturnTrueExpression = null;

        VerifyMethodVisitor(JavaContext context) {
            mContext = context;
        }

        @Override
        public boolean visitReturnExpression(@NonNull UReturnExpression node) {
            UExpression returnValue = node.getReturnExpression();
            if (returnValue instanceof ULiteralExpression) {
                Object value = ((ULiteralExpression) returnValue).getValue();
                if (Boolean.TRUE.equals(value)) {
                    mReturnTrueCount++;
                    mReturnTrueExpression = node;
                } else if (Boolean.FALSE.equals(value)) {
                    mReturnFalseCount++;
                    mHasReturnFalse = true;
                }
            }
            return super.visitReturnExpression(node);
        }

        @Override
        public boolean visitThrowExpression(@NonNull UThrowExpression node) {
            mHasThrow = true;
            return super.visitThrowExpression(node);
        }

        @Override
        public boolean visitCallExpression(@NonNull UCallExpression node) {
            return super.visitCallExpression(node);
        }

        @Override
        public void afterVisitBlockExpression(@NonNull UBlockExpression node) {
            super.afterVisitBlockExpression(node);
        }

        public void checkAndReport() {
            // If there are return true statements but no return false statements and no throws,
            // then the method always returns true
            if (mReturnTrueCount > 0 && !mHasReturnFalse && !mHasThrow
                    && mReturnTrueExpression != null) {
                mContext.report(
                        ISSUE,
                        mReturnTrueExpression,
                        mContext.getLocation(mReturnTrueExpression),
                        "`verify` always returns `true`, which could cause insecure network "
                                + "traffic due to trusting TLS/SSL server certificates for wrong "
                                + "hostnames");
            }
        }
    }

    // Override visitClass to use a different approach for checking
    // We need to check if the verify method always returns true
    // Let's use a cleaner implementation

    private void checkVerifyMethodBody(@NonNull JavaContext context, @NonNull UMethod method) {
        UExpression body = method.getUastBody();
        if (body == null) {
            return;
        }

        final boolean[] hasReturnTrue = {false};
        final boolean[] hasReturnFalse = {false};
        final boolean[] hasThrow = {false};
        final UReturnExpression[] returnTrueExpr = {null};

        body.accept(new AbstractUastVisitor() {
            @Override
            public boolean visitReturnExpression(@NonNull UReturnExpression node) {
                UExpression returnValue = node.getReturnExpression();
                if (returnValue instanceof ULiteralExpression) {
                    Object value = ((ULiteralExpression) returnValue).getValue();
                    if (Boolean.TRUE.equals(value)) {
                        hasReturnTrue[0] = true;
                        returnTrueExpr[0] = node;
                    } else if (Boolean.FALSE.equals(value)) {
                        hasReturnFalse[0] = true;
                    }
                }
                return super.visitReturnExpression(node);
            }

            @Override
            public boolean visitThrowExpression(@NonNull UThrowExpression node) {
                hasThrow[0] = true;
                return super.visitThrowExpression(node);
            }
        });

        if (hasReturnTrue[0] && !hasReturnFalse[0] && !hasThrow[0] && returnTrueExpr[0] != null) {
            context.report(
                    ISSUE,
                    returnTrueExpr[0],
                    context.getLocation(returnTrueExpr[0]),
                    "`verify` always returns `true`, which could cause insecure network "
                            + "traffic due to trusting TLS/SSL server certificates for wrong "
                            + "hostnames");
        }
    }

    public void visitThrowExpression(@NonNull JavaContext context,
            @NonNull UThrowExpression node) {
        // handled inline in visitor
    }

    public void visitCallExpression(@NonNull JavaContext context,
            @NonNull UCallExpression node) {
        // handled inline in visitor
    }

    public void visitReturnExpression(@NonNull JavaContext context,
            @NonNull UReturnExpression node) {
        // handled inline in visitor
    }
}