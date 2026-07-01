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
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.UReturnExpression;
import org.jetbrains.uast.UThrowExpression;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.ULiteralExpression;
import org.jetbrains.uast.visitor.AbstractUastVisitor;

public class BadHostnameVerifierDetector extends Detector implements SourceCodeScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(BadHostnameVerifierDetector.class, Scope.JAVA_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                            "BadHostnameVerifier",
                            "Insecure HostnameVerifier",
                            "An implementation of `HostnameVerifier` that always returns true trusts any "
                                    + "hostname. This makes the application vulnerable to Man-in-the-Middle "
                                    + "attacks, as an attacker could present a certificate for any host "
                                    + "and the application would accept it.",
                            Category.SECURITY,
                            6,
                            Severity.WARNING,
                            IMPLEMENTATION)
                    .setAndroidSpecific(true);

    private boolean mReturnsTrue;
    private boolean mReturnsFalse;
    private boolean mReturnsOther;
    private boolean mThrowsException;

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

        for (PsiMethod method : declaration.getMethods()) {
            if ("verify".equals(method.getName()) && method.getParameterList().getParametersCount() == 2) {
                UElement uMethod = context.getUastContext().getMethod(method);
                if (uMethod != null) {
                    mReturnsTrue = false;
                    mReturnsFalse = false;
                    mReturnsOther = false;
                    mThrowsException = false;

                    uMethod.accept(new AbstractUastVisitor() {
                        @Override
                        public boolean visitThrowExpression(@NonNull UThrowExpression node) {
                            BadHostnameVerifierDetector.this.visitThrowExpression(node);
                            return super.visitThrowExpression(node);
                        }

                        @Override
                        public boolean visitCallExpression(@NonNull UCallExpression node) {
                            BadHostnameVerifierDetector.this.visitCallExpression(node);
                            return super.visitCallExpression(node);
                        }

                        @Override
                        public boolean visitReturnExpression(@NonNull UReturnExpression node) {
                            BadHostnameVerifierDetector.this.visitReturnExpression(node);
                            return super.visitReturnExpression(node);
                        }
                    });

                    if (mReturnsTrue && !mReturnsFalse && !mReturnsOther && !mThrowsException) {
                        context.report(
                                ISSUE,
                                declaration,
                                context.getNameLocation(declaration),
                                "This `HostnameVerifier` implementation always returns true, making it insecure.");
                    }
                }
            }
        }
    }

    public void visitThrowExpression(@NonNull UThrowExpression node) {
        mThrowsException = true;
    }

    public void visitCallExpression(@NonNull UCallExpression node) {
        // Method calls in verify could perform custom logic, but we still track them.
    }

    public void visitReturnExpression(@NonNull UReturnExpression node) {
        UExpression expression = node.getReturnExpression();
        if (expression != null) {
            if (isTrueLiteral(expression)) {
                mReturnsTrue = true;
            } else if (isFalseLiteral(expression)) {
                mReturnsFalse = true;
            } else {
                mReturnsOther = true;
            }
        }
    }

    private static boolean isTrueLiteral(@NonNull UExpression expression) {
        if (expression instanceof ULiteralExpression) {
            Object value = ((ULiteralExpression) expression).getValue();
            return Boolean.TRUE.equals(value);
        }
        return false;
    }

    private static boolean isFalseLiteral(@NonNull UExpression expression) {
        if (expression instanceof ULiteralExpression) {
            Object value = ((ULiteralExpression) expression).getValue();
            return Boolean.FALSE.equals(value);
        }
        return false;
    }
}