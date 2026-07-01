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
import java.util.Collections;
import java.util.List;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.ULambdaExpression;
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
                    "This check looks for implementations of `HostnameVerifier` whose `verify` "
                            + "method always returns true (thus trusting any hostname) which could "
                            + "result in insecure network traffic caused by trusting arbitrary "
                            + "hostnames in TLS/SSL certificates presented by peers.",
                    Category.SECURITY,
                    6,
                    Severity.WARNING,
                    IMPLEMENTATION);

    private boolean returnsTrue;
    private boolean returnsFalse;
    private boolean returnsOther;
    private boolean throwsException;

    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList("javax.net.ssl.HostnameVerifier");
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        UMethod verifyMethod = null;
        for (UMethod method : declaration.getMethods()) {
            if ("verify".equals(method.getName())) {
                verifyMethod = method;
                break;
            }
        }

        if (verifyMethod == null) {
            return;
        }

        this.returnsTrue = false;
        this.returnsFalse = false;
        this.returnsOther = false;
        this.throwsException = false;

        final UMethod finalVerifyMethod = verifyMethod;
        verifyMethod.accept(new org.jetbrains.uast.visitor.AbstractUastVisitor() {
            @Override
            public boolean visitMethod(UMethod node) {
                return !node.equals(finalVerifyMethod);
            }

            @Override
            public boolean visitClass(UClass node) {
                return true;
            }

            @Override
            public boolean visitLambdaExpression(ULambdaExpression node) {
                return true;
            }

            @Override
            public boolean visitReturnExpression(UReturnExpression node) {
                visitReturnExpression(node);
                return super.visitReturnExpression(node);
            }

            @Override
            public boolean visitThrowExpression(UThrowExpression node) {
                visitThrowExpression(node);
                return super.visitThrowExpression(node);
            }

            @Override
            public boolean visitCallExpression(UCallExpression node) {
                visitCallExpression(node);
                return super.visitCallExpression(node);
            }
        });

        if (returnsTrue && !returnsFalse && !returnsOther && !throwsException) {
            context.report(
                    ISSUE,
                    declaration,
                    context.getLocation(verifyMethod),
                    "This `HostnameVerifier` always returns `true` (trusting any hostname), which is insecure.");
        }
    }

    public void visitThrowExpression(UThrowExpression expression) {
        throwsException = true;
    }

    public void visitCallExpression(UCallExpression expression) {
        // Handled via return expression checks or general call analysis if needed
    }

    public void visitReturnExpression(UReturnExpression expression) {
        UExpression value = expression.getReturnExpression();
        if (value instanceof ULiteralExpression) {
            Object val = ((ULiteralExpression) value).getValue();
            if (Boolean.TRUE.equals(val)) {
                returnsTrue = true;
            } else if (Boolean.FALSE.equals(val)) {
                returnsFalse = true;
            } else {
                returnsOther = true;
            }
        } else if (value != null) {
            returnsOther = true;
        }
    }
}