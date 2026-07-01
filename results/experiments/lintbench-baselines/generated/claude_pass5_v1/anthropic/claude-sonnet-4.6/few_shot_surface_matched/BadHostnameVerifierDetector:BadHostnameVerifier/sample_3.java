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
import com.intellij.psi.PsiType;
import java.util.Collections;
import java.util.List;
import org.jetbrains.uast.UBlockExpression;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
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
        for (PsiMethod method : declaration.getMethods()) {
            if ("verify".equals(method.getName())) {
                UMethod uMethod = context.getUastContext().getMethod(method);
                if (uMethod == null) {
                    continue;
                }
                VerifyMethodVisitor visitor = new VerifyMethodVisitor(context);
                uMethod.accept(visitor);
                if (visitor.alwaysReturnsTrue()) {
                    context.report(
                            ISSUE,
                            declaration,
                            context.getNameLocation(declaration),
                            "`verify` always returns `true`, which could cause insecure network "
                                    + "traffic due to trusting arbitrary hostnames in TLS/SSL "
                                    + "certificates presented by peers");
                }
            }
        }
    }

    @Override
    public void visitThrowExpression(
            @NonNull JavaContext context, @NonNull UThrowExpression expression) {
        // handled via visitClass/VerifyMethodVisitor
    }

    @Override
    public void visitCallExpression(
            @NonNull JavaContext context, @NonNull UCallExpression expression) {
        // handled via visitClass/VerifyMethodVisitor
    }

    @Override
    public void visitReturnExpression(
            @NonNull JavaContext context, @NonNull UReturnExpression expression) {
        // handled via visitClass/VerifyMethodVisitor
    }

    /**
     * Visitor that analyses a verify() method body to determine if it unconditionally returns true
     * without ever returning false or throwing an exception.
     */
    private static class VerifyMethodVisitor extends AbstractUastVisitor {

        private final JavaContext mContext;
        private boolean mHasReturnTrue = false;
        private boolean mHasReturnFalse = false;
        private boolean mHasThrow = false;

        VerifyMethodVisitor(JavaContext context) {
            mContext = context;
        }

        boolean alwaysReturnsTrue() {
            return mHasReturnTrue && !mHasReturnFalse && !mHasThrow;
        }

        @Override
        public boolean visitReturnExpression(@NonNull UReturnExpression node) {
            UExpression returnValue = node.getReturnExpression();
            if (returnValue instanceof ULiteralExpression) {
                Object value = ((ULiteralExpression) returnValue).getValue();
                if (Boolean.TRUE.equals(value)) {
                    mHasReturnTrue = true;
                } else if (Boolean.FALSE.equals(value)) {
                    mHasReturnFalse = true;
                }
            } else if (returnValue != null) {
                // Non-literal return — could be false, so be conservative
                Object evaluated = returnValue.evaluate();
                if (evaluated instanceof Boolean) {
                    if (Boolean.TRUE.equals(evaluated)) {
                        mHasReturnTrue = true;
                    } else {
                        mHasReturnFalse = true;
                    }
                } else {
                    // Unknown value — assume it might return false
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
    }
}