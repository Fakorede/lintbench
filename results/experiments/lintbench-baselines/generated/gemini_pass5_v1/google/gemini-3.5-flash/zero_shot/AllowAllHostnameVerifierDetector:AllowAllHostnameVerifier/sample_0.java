package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.ConstantEvaluator;
import com.intellij.psi.PsiModifier;
import java.util.Collections;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.ULambdaExpression;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UReturnExpression;
import org.jetbrains.uast.visitor.AbstractUastVisitor;

public class AllowAllHostnameVerifierDetector extends Detector implements SourceCodeScanner {

    public static final Issue ISSUE = Issue.create(
            "AllowAllHostnameVerifier",
            "Insecure HostnameVerifier",
            "This check looks for use of HostnameVerifier implementations whose `verify` " +
            "method always returns true (thus trusting any hostname) which could result " +
            "in insecure network traffic caused by trusting arbitrary hostnames in " +
            "TLS/SSL certificates presented by peers.",
            Category.SECURITY,
            6,
            Severity.WARNING,
            new Implementation(AllowAllHostnameVerifierDetector.class, Scope.JAVA_FILE_SCOPE)
    );

    @Nullable
    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList("javax.net.ssl.HostnameVerifier");
    }

    @Override
    public void visitClass(@NotNull JavaContext context, @NotNull UClass declaration) {
        if (declaration.hasModifierProperty(PsiModifier.ABSTRACT) || declaration.isInterface()) {
            return;
        }

        for (UMethod method : declaration.getMethods()) {
            if ("verify".equals(method.getName())) {
                if (alwaysReturnsTrue(context, method)) {
                    context.report(
                            ISSUE,
                            method,
                            context.getNameLocation(method),
                            "Trusting all hostnames can lead to insecure network traffic"
                    );
                }
            }
        }
    }

    private static boolean alwaysReturnsTrue(@NotNull JavaContext context, @NotNull UMethod method) {
        UExpression body = method.getUastBody();
        if (body == null) {
            return false;
        }

        ReturnVisitor visitor = new ReturnVisitor(context);
        body.accept(visitor);
        return visitor.hasReturns && !visitor.hasNonTrueReturns;
    }

    private static class ReturnVisitor extends AbstractUastVisitor {
        private final JavaContext context;
        boolean hasReturns = false;
        boolean hasNonTrueReturns = false;

        ReturnVisitor(JavaContext context) {
            this.context = context;
        }

        @Override
        public boolean visitReturnExpression(@NotNull UReturnExpression node) {
            hasReturns = true;
            UExpression returnVal = node.getReturnExpression();
            if (returnVal == null || !isTrue(returnVal)) {
                hasNonTrueReturns = true;
            }
            return super.visitReturnExpression(node);
        }

        @Override
        public boolean visitClass(@NotNull UClass node) {
            // Do not descend into local/anonymous classes defined inside the method
            return true;
        }

        @Override
        public boolean visitLambdaExpression(@NotNull ULambdaExpression node) {
            // Do not descend into lambdas defined inside the method
            return true;
        }

        private boolean isTrue(UExpression expression) {
            Object value = ConstantEvaluator.evaluate(context, expression);
            return Boolean.TRUE.equals(value);
        }
    }
}