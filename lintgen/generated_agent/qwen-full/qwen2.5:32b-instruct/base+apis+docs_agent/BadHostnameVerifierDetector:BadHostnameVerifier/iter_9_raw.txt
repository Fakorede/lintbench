package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;

import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UReturnExpression;
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
        if ("javax.net.ssl.HostnameVerifier".equals(method.getContainingClass().getQualifiedName())) {
            checkVerifyMethod(context);
        }
    }

    private void checkVerifyMethod(JavaContext context) {
        UClass clazz = UastUtils.findParent((UElement) context.getLocation().getElement(), UClass.class);
        if (clazz != null && "javax.net.ssl.HostnameVerifier".equals(clazz.getQualifiedName())) {
            clazz.accept(new AbstractUastVisitor() {
                @Override
                public boolean visitUMethod(UMethod method) {
                    if ("verify".equals(method.getName()) && method.getBody() != null) {
                        method.getBody().accept(new AbstractUastVisitor() {
                            @Override
                            public boolean visitUReturnExpression(UReturnExpression node) {
                                if (node.getValue() instanceof org.jetbrains.uast.UBooleanLiteralExpression) {
                                    org.jetbrains.uast.UBooleanLiteralExpression ref = (org.jetbrains.uast.UBooleanLiteralExpression) node.getValue();
                                    if (ref.getValue()) {
                                        context.report(ISSUE, node, context.getLocation(node), "Insecure HostnameVerifier implementation that always returns true");
                                    }
                                }
                                return super.visitUReturnExpression(node);
                            }
                        });
                    }
                    return super.visitUMethod(method);
                }
            });
        }
    }

    @Override
    public boolean appliesToResourceRefs() {
        return false;
    }
}