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
        for (PsiMethod psiMethod : declaration.findMethodsByName("verify", false)) {
            UMethod method = (UMethod) context.getUastContext().getMethod(psiMethod);
            if (method == null) {
                continue;
            }

            // Check that this is the verify(String, SSLSession) method
            if (psiMethod.getParameterList().getParametersCount() != 2) {
                continue;
            }

            VerifyMethodVisitor visitor = new VerifyMethodVisitor(context);
            method.accept(visitor);

            if (visitor.alwaysReturnsTrue) {
                context.report(
                        ISSUE,
                        method,
                        context.getNameLocation(method),
                        "`verify` always returns `true`, which could cause insecure network traffic "
                                + "due to trusting TLS/SSL server certificates for wrong hostnames");
            }
        }
    }

    public void visitThrowExpression(@NonNull JavaContext context, @NonNull UThrowExpression expression) {
        // Not used directly; handled in visitor
    }

    public void visitCallExpression(@NonNull JavaContext context, @NonNull UCallExpression expression) {
        // Not used directly; handled in visitor
    }

    public void visitReturnExpression(@NonNull JavaContext context, @NonNull UReturnExpression expression) {
        // Not used directly; handled in visitor
    }

    /**
     * Visitor that checks whether a verify() method always returns true.
     */
    private static class VerifyMethodVisitor extends AbstractUastVisitor {
        private final JavaContext context;
        boolean alwaysReturnsTrue = false;
        private boolean hasReturnFalse = false;
        private boolean hasReturnTrue = false;
        private boolean hasThrow = false;
        private boolean hasOtherReturn = false;

        VerifyMethodVisitor(JavaContext context) {
            this.context = context;
        }

        @Override
        public boolean visitReturnExpression(@NonNull UReturnExpression node) {
            UExpression returnValue = node.getReturnExpression();
            if (returnValue instanceof ULiteralExpression) {
                Object value = ((ULiteralExpression) returnValue).getValue();
                if (Boolean.TRUE.equals(value)) {
                    hasReturnTrue = true;
                } else if (Boolean.FALSE.equals(value)) {
                    hasReturnFalse = true;
                } else {
                    hasOtherReturn = true;
                }
            } else if (returnValue != null) {
                hasOtherReturn = true;
            }
            return super.visitReturnExpression(node);
        }

        @Override
        public boolean visitThrowExpression(@NonNull UThrowExpression node) {
            hasThrow = true;
            return super.visitThrowExpression(node);
        }

        @Override
        public boolean visitMethod(@NonNull UMethod node) {
            // Only visit the top-level method body, not nested anonymous classes
            return false;
        }

        @Override
        public boolean visitClass(@NonNull UClass node) {
            // Don't descend into nested/anonymous classes
            return true;
        }

        @Override
        public void afterVisitMethod(@NonNull UMethod node) {
            // After visiting, determine if this always returns true
            alwaysReturnsTrue = hasReturnTrue && !hasReturnFalse && !hasOtherReturn;
        }
    }
}