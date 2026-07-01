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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.ULambdaExpression;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UParameter;
import org.jetbrains.uast.UReturnExpression;
import org.jetbrains.uast.UTypeReceiver;
import org.jetbrains.uast.visitor.AbstractUastVisitor;

import java.util.Collections;
import java.util.List;

public class BadHostnameVerifierDetector extends Detector implements SourceCodeScanner {

    public static final Issue ISSUE = Issue.create(
            "BadHostnameVerifier",
            "Insecure HostnameVerifier",
            "This check looks for implementations of `HostnameVerifier` whose " +
                    "`verify` method always returns true (thus trusting any " +
                    "hostname) which could result in insecure network traffic caused " +
                    "by trusting arbitrary hostnames in TLS/SSL certificates " +
                    "presented by peers.",
            Category.SECURITY,
            6,
            Severity.WARNING,
            new Implementation(
                    BadHostnameVerifierDetector.class,
                    Scope.JAVA_FILE_SCOPE
            )
    ).addMoreInfo("https://goo.gle/BadHostnameVerifier");

    @Nullable
    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList("javax.net.ssl.HostnameVerifier");
    }

    @Override
    public void visitClass(@NotNull JavaContext context, @NotNull UClass declaration) {
        if (declaration.isInterface()) {
            return;
        }

        UMethod verifyMethod = null;
        for (UMethod method : declaration.getMethods()) {
            if ("verify".equals(method.getName())) {
                List<UParameter> parameters = method.getUastParameters();
                if (parameters.size() == 2) {
                    verifyMethod = method;
                    break;
                }
            }
        }

        if (verifyMethod == null || verifyMethod.getUastBody() == null) {
            return;
        }

        ReturnVisitor visitor = new ReturnVisitor(context);
        verifyMethod.getUastBody().accept(visitor);

        if (visitor.trueCount > 0 && visitor.falseCount == 0 && visitor.otherCount == 0) {
            context.report(
                    ISSUE,
                    verifyMethod,
                    context.getNameLocation(verifyMethod),
                    "Using the default `HostnameVerifier` implementation which always returns true."
            );
        }
    }

    private static class ReturnVisitor extends AbstractUastVisitor {
        private final JavaContext context;
        int trueCount = 0;
        int falseCount = 0;
        int otherCount = 0;

        ReturnVisitor(JavaContext context) {
            this.context = context;
        }

        @Override
        public boolean visitTypeReceiver(@NotNull UTypeReceiver expression) {
            return true;
        }

        @Override
        public boolean visitClass(@NotNull UClass node) {
            return true; // Don't go into inner classes
        }

        @Override
        public boolean visitLambdaExpression(@NotNull ULambdaExpression node) {
            return true; // Don't go into lambdas
        }

        @Override
        public boolean visitReturnExpression(@NotNull UReturnExpression node) {
            UExpression returnExpression = node.getReturnExpression();
            if (returnExpression == null) {
                otherCount++;
            } else {
                Object value = ConstantEvaluator.evaluate(context, returnExpression);
                if (Boolean.TRUE.equals(value)) {
                    trueCount++;
                } else if (Boolean.FALSE.equals(value)) {
                    falseCount++;
                } else {
                    otherCount++;
                }
            }
            return super.visitReturnExpression(node);
        }
    }
}