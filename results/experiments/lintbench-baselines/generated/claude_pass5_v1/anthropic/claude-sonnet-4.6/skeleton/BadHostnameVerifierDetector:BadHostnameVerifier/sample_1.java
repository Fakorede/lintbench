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
                    "This check looks for implementations of `HostnameVerifier` whose `verify` method "
                            + "always returns true (thus trusting any hostname) which could result in insecure "
                            + "network traffic caused by trusting arbitrary hostnames in TLS/SSL certificates "
                            + "presented by peers.",
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
            UMethod uMethod = context.getUastContext().getMethod(method);
            if (uMethod == null) {
                continue;
            }
            // Check that it's the correct verify signature: boolean verify(String, SSLSession)
            PsiMethod psiMethod = uMethod.getJavaPsi();
            if (psiMethod.getParameterList().getParametersCount() != 2) {
                continue;
            }

            VerifyMethodVisitor visitor = new VerifyMethodVisitor(context);
            uMethod.accept(visitor);
            if (visitor.alwaysReturnsTrue()) {
                context.report(
                        ISSUE,
                        uMethod,
                        context.getLocation(uMethod),
                        "verify always returns `true`, which could cause insecure network "
                                + "traffic due to trusting TLS/SSL server certificates for wrong hostnames");
            }
        }
    }

    /**
     * Visitor that analyzes a verify() method body to determine if it always returns true.
     */
    private static class VerifyMethodVisitor extends AbstractUastVisitor {
        private final JavaContext mContext;
        private boolean mAlwaysReturnsTrue = false;
        private boolean mHasReturnTrue = false;
        private boolean mHasReturnFalse = false;
        private boolean mHasThrow = false;
        private boolean mHasOtherReturn = false;

        VerifyMethodVisitor(JavaContext context) {
            mContext = context;
        }

        boolean alwaysReturnsTrue() {
            return mHasReturnTrue && !mHasReturnFalse && !mHasOtherReturn;
        }

        @Override
        public boolean visitReturnExpression(@NonNull UReturnExpression node) {
            UExpression returnValue = node.getReturnExpression();
            if (returnValue instanceof ULiteralExpression) {
                ULiteralExpression literal = (ULiteralExpression) returnValue;
                Object value = literal.getValue();
                if (Boolean.TRUE.equals(value)) {
                    mHasReturnTrue = true;
                } else if (Boolean.FALSE.equals(value)) {
                    mHasReturnFalse = true;
                } else {
                    mHasOtherReturn = true;
                }
            } else if (returnValue != null) {
                // Check if the expression evaluates to a constant true
                Object evaluated = returnValue.evaluate();
                if (Boolean.TRUE.equals(evaluated)) {
                    mHasReturnTrue = true;
                } else if (Boolean.FALSE.equals(evaluated)) {
                    mHasReturnFalse = true;
                } else {
                    mHasOtherReturn = true;
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
            // If there's a call expression that could affect control flow (e.g., System.exit),
            // we conservatively note it as a non-trivially-true return
            return super.visitCallExpression(node);
        }
    }

    public void visitThrowExpression(@NonNull JavaContext context, @NonNull UThrowExpression node) {
        // handled in inner visitor
    }

    public void visitCallExpression(@NonNull JavaContext context, @NonNull UCallExpression node) {
        // handled in inner visitor
    }

    public void visitReturnExpression(@NonNull JavaContext context, @NonNull UReturnExpression node) {
        // handled in inner visitor
    }
}