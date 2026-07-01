package com.android.tools.lint.checks;

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
import org.jetbrains.uast.ULiteralExpression;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UParameter;
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
                            + "method always returns true (thus trusting any hostname), which "
                            + "could result in insecure network traffic caused by trusting "
                            + "arbitrary hostnames in TLS/SSL certificates presented by peers.",
                    Category.SECURITY,
                    6,
                    Severity.WARNING,
                    IMPLEMENTATION);

    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList("javax.net.ssl.HostnameVerifier");
    }

    @Override
    public void visitClass(JavaContext context, UClass declaration) {
        for (UMethod method : declaration.getMethods()) {
            if (!"verify".equals(method.getName())) {
                continue;
            }

            List<UParameter> parameters = method.getUastParameters();
            if (parameters == null || parameters.size() != 2) {
                continue;
            }

            UExpression body = method.getUastBody();
            if (body == null) {
                continue;
            }

            ReturnTrueVisitor visitor = new ReturnTrueVisitor();
            body.accept(visitor);

            if (visitor.onlyReturnsTrue()) {
                context.report(
                        ISSUE,
                        method,
                        context.getLocation(method),
                        "Insecure `HostnameVerifier`: this implementation always returns `true`, "
                                + "trusting any hostname and making the app vulnerable to "
                                + "man-in-the-middle attacks");
            }
        }
    }

    private static class ReturnTrueVisitor extends AbstractUastVisitor {
        private boolean mHasReturnTrue;
        private boolean mHasReturnOther;
        private boolean mHasThrow;

        @Override
        public boolean visitReturnExpression(UReturnExpression node) {
            UExpression returnValue = node.getReturnExpression();
            if (returnValue instanceof ULiteralExpression
                    && Boolean.TRUE.equals(((ULiteralExpression) returnValue).getValue())) {
                mHasReturnTrue = true;
            } else {
                mHasReturnOther = true;
            }
            return super.visitReturnExpression(node);
        }

        @Override
        public boolean visitThrowExpression(UThrowExpression node) {
            mHasThrow = true;
            return super.visitThrowExpression(node);
        }

        @Override
        public boolean visitCallExpression(UCallExpression node) {
            mHasReturnOther = true;
            return super.visitCallExpression(node);
        }

        boolean onlyReturnsTrue() {
            return mHasReturnTrue && !mHasReturnOther && !mHasThrow;
        }
    }
}