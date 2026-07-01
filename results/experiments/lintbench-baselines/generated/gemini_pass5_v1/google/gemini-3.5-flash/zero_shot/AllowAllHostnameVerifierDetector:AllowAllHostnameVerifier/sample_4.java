package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.ConstantEvaluator;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.intellij.psi.PsiType;
import java.util.Collections;
import java.util.List;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.ULiteralExpression;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UParameter;
import org.jetbrains.uast.UReturnExpression;
import org.jetbrains.uast.ULambdaExpression;
import org.jetbrains.uast.visitor.AbstractUastVisitor;

public class AllowAllHostnameVerifierDetector extends Detector implements SourceCodeScanner {

    public static final Issue ISSUE = Issue.create(
            "AllowAllHostnameVerifier",
            "Insecure HostnameVerifier",
            "This check looks for use of HostnameVerifier implementations whose `verify` " +
            "method always returns true (thus trusting any hostname) which could result " +
            "in insecure network traffic caused by trusting arbitrary hostnames in TLS/SSL " +
            "certificates presented by peers.",
            Category.SECURITY,
            6,
            Severity.WARNING,
            new Implementation(
                    AllowAllHostnameVerifierDetector.class,
                    Scope.JAVA_FILE_SCOPE
            )
    ).addMoreInfo("https://goo.gle/AllowAllHostnameVerifier");

    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList("javax.net.ssl.HostnameVerifier");
    }

    @Override
    public void visitClass(JavaContext context, UClass declaration) {
        for (UMethod method : declaration.getMethods()) {
            if (isVerifyMethod(method)) {
                if (returnsTrueUnconditionally(context, method)) {
                    context.report(
                            ISSUE,
                            method,
                            context.getNameLocation(method),
                            "Insecure `HostnameVerifier` trust strategy: `verify` always returns `true`"
                    );
                }
            }
        }
    }

    private boolean isVerifyMethod(UMethod method) {
        if (!"verify".equals(method.getName())) {
            return false;
        }
        List<UParameter> parameters = method.getUastParameters();
        if (parameters.size() != 2) {
            return false;
        }
        PsiType type1 = parameters.get(0).getType();
        PsiType type2 = parameters.get(1).getType();
        return type1.getCanonicalText().equals("java.lang.String")
                && type2.getCanonicalText().equals("javax.net.ssl.SSLSession");
    }

    private boolean returnsTrueUnconditionally(JavaContext context, UMethod method) {
        UExpression body = method.getUastBody();
        if (body == null) {
            return false;
        }

        ReturnVisitor visitor = new ReturnVisitor(context);
        body.accept(visitor);

        if (visitor.returnCount == 0) {
            return isAlwaysTrue(context, body);
        }

        return visitor.allReturnsAreTrue;
    }

    private boolean isAlwaysTrue(JavaContext context, UExpression expression) {
        if (expression instanceof ULiteralExpression) {
            return Boolean.TRUE.equals(((ULiteralExpression) expression).getValue());
        }
        try {
            Object value = ConstantEvaluator.evaluate(context, expression);
            return Boolean.TRUE.equals(value);
        } catch (Exception e) {
            return false;
        }
    }

    private class ReturnVisitor extends AbstractUastVisitor {
        private final JavaContext context;
        int returnCount = 0;
        boolean allReturnsAreTrue = true;

        ReturnVisitor(JavaContext context) {
            this.context = context;
        }

        @Override
        public boolean visitReturnExpression(UReturnExpression node) {
            returnCount++;
            UExpression returnVal = node.getReturnExpression();
            if (returnVal == null || !isAlwaysTrue(context, returnVal)) {
                allReturnsAreTrue = false;
            }
            return super.visitReturnExpression(node);
        }

        @Override
        public boolean visitClass(UClass node) {
            return true; // Do not descend into nested/inner classes
        }

        @Override
        public boolean visitLambdaExpression(ULambdaExpression node) {
            return true; // Do not descend into lambdas
        }
    }
}