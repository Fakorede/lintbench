package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;

import com.intellij.psi.PsiMethod;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.ULiteralExpression;
import org.jetbrains.uast.UastUtils;
import org.jetbrains.uast.visitor.AbstractUastVisitor;

import java.util.Collections;
import java.util.List;

public class BadHostnameVerifierDetector extends Detector implements SourceCodeScanner {

    public static final Issue ISSUE = Issue.create(
            "BadHostnameVerifier",
            "Insecure HostnameVerifier implementation",
            "This check looks for implementations of `HostnameVerifier` whose `verify` method always returns true (thus trusting any hostname) which could result in insecure network traffic caused by trusting arbitrary hostnames in TLS/SSL certificates presented by peers.",
            Category.SECURITY,
            6,
            Severity.ERROR,
            new Implementation(
                    BadHostnameVerifierDetector.class,
                    Scope.JAVA_FILE_SCOPE
            )
    );

    @Override
    public List<String> getApplicableMethodNames() {
        return Collections.singletonList("verify");
    }

    @Override
    public void visitMethodCall(JavaContext context, UCallExpression node, PsiMethod method) {
        if (method != null && "javax.net.ssl.HostnameVerifier".equals(method.getContainingClass().getQualifiedName())) {
            checkVerifyMethod(context, node);
        }
    }

    private void checkVerifyMethod(JavaContext context, UElement element) {
        AbstractUastVisitor visitor = new AbstractUastVisitor() {
            @Override
            public boolean visitLiteralExpression(ULiteralExpression literalExpr) {
                if (literalExpr.getValue() instanceof Boolean && ((Boolean) literalExpr.getValue())) {
                    context.report(ISSUE, element, context.getLocation(element), "The `verify` method always returns true which is insecure.");
                    return false;
                }
                return super.visitLiteralExpression(literalExpr);
            }
        };
        UastUtils.accept(element, visitor);
    }

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.emptyList();
    }

    @Override
    public AbstractUastVisitor createUastHandler(JavaContext context) {
        return null;
    }
}