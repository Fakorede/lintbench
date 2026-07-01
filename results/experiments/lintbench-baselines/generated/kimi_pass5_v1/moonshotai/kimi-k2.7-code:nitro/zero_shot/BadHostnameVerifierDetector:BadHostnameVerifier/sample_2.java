package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.UElementHandler;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.ULambdaExpression;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UReturnExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.visitor.AbstractUastVisitor;

public class BadHostnameVerifierDetector extends Detector implements Detector.UastScanner {

    private static final String HOSTNAME_VERIFIER = "javax.net.ssl.HostnameVerifier";

    public static final Issue ISSUE = Issue.create(
            "BadHostnameVerifier",
            "Insecure HostnameVerifier",
            "This check looks for implementations of `HostnameVerifier` whose `verify` method always returns true. "
                    + "Trusting any hostname can result in insecure network traffic caused by trusting arbitrary "
                    + "hostnames in TLS/SSL certificates presented by peers.",
            Category.SECURITY,
            6,
            Severity.WARNING,
            new Implementation(BadHostnameVerifierDetector.class, Scope.JAVA_FILE_SCOPE),
            "https://goo.gle/BadHostnameVerifier"
    );

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Arrays.asList(UMethod.class, ULambdaExpression.class);
    }

    @Override
    public UElementHandler createUastHandler(JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitMethod(UMethod node) {
                if (!"verify".equals(node.getName())) {
                    return;
                }
                if (node.getUastParameters().size() != 2) {
                    return;
                }
                UClass containingClass = node.getContainingClass();
                if (containingClass == null) {
                    return;
                }
                if (!context.getEvaluator().implementsInterface(containingClass, HOSTNAME_VERIFIER, false)) {
                    return;
                }
                if (allReturnValuesTrue(node.getUastBody())) {
                    context.report(ISSUE, node, context.getLocation(node),
                            "Insecure HostnameVerifier: verify always returns true");
                }
            }

            @Override
            public void visitLambdaExpression(ULambdaExpression node) {
                PsiType functionalInterfaceType = node.getFunctionalInterfaceType();
                if (functionalInterfaceType == null) {
                    return;
                }
                PsiClass resolved = context.getEvaluator().getTypeClass(functionalInterfaceType);
                if (resolved == null) {
                    return;
                }
                if (!HOSTNAME_VERIFIER.equals(resolved.getQualifiedName())) {
                    return;
                }
                if (allReturnValuesTrue(node.getBody())) {
                    context.report(ISSUE, node, context.getLocation(node),
                            "Insecure HostnameVerifier: verify always returns true");
                }
            }
        };
    }

    private static boolean allReturnValuesTrue(UExpression body) {
        if (body == null) {
            return false;
        }
        List<UReturnExpression> returns = new ArrayList<>();
        body.accept(new AbstractUastVisitor() {
            @Override
            public boolean visitReturnExpression(UReturnExpression node) {
                returns.add(node);
                return super.visitReturnExpression(node);
            }
        });
        if (!returns.isEmpty()) {
            for (UReturnExpression returnExpr : returns) {
                if (!isTrue(returnExpr.getReturnExpression())) {
                    return false;
                }
            }
            return true;
        }
        return isTrue(body);
    }

    private static boolean isTrue(UExpression expression) {
        if (expression == null) {
            return false;
        }
        Object value = expression.evaluate();
        return Boolean.TRUE.equals(value);
    }
}