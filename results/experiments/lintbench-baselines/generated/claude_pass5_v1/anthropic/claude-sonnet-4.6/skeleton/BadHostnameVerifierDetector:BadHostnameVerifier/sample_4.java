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
            UMethod uMethod = context.getUastContext().getMethod(method);
            if (uMethod == null) {
                continue;
            }
            VerifyMethodVisitor visitor = new VerifyMethodVisitor(context);
            uMethod.accept(visitor);
            if (visitor.alwaysReturnsTrue()) {
                context.report(
                        ISSUE,
                        uMethod,
                        context.getNameLocation(uMethod),
                        "`verify` always returns `true`, which could cause insecure network traffic "
                                + "due to trusting arbitrary hostnames in TLS/SSL certificates "
                                + "presented by peers");
            }
        }
    }

    public void visitThrowExpression(@NonNull JavaContext context,
            @NonNull UThrowExpression node) {
        // handled in visitor
    }

    public void visitCallExpression(@NonNull JavaContext context,
            @NonNull UCallExpression node) {
        // handled in visitor
    }

    public void visitReturnExpression(@NonNull JavaContext context,
            @NonNull UReturnExpression node) {
        // handled in visitor
    }

    /**
     * Visitor that checks whether a verify() method always returns true.
     * It tracks return statements and checks if any non-true returns or throws exist.
     */
    private static class VerifyMethodVisitor extends AbstractUastVisitor {
        private final JavaContext context;
        private boolean hasReturnTrue = false;
        private boolean hasReturnFalse = false;
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
            } else if (returnValue == null) {
                // void return - not relevant for boolean method
            } else {
                hasOtherReturn = true;
            }
            return super.visitReturnExpression(node);
        }

        @Override
        public boolean visitThrowExpression(@NonNull UThrowExpression node) {
            hasThrow = true;
            return super.visitThrowExpression(node);
        }

        boolean alwaysReturnsTrue() {
            // The method always returns true if:
            // - It has at least one "return true"
            // - It has no "return false"
            // - It has no other non-literal returns
            // Throws are acceptable (they don't represent a "true" return path that's insecure,
            // but a method that only returns true and possibly throws is still suspicious)
            return hasReturnTrue && !hasReturnFalse && !hasOtherReturn;
        }
    }
}