package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UReturnExpression;
import org.jetbrains.uast.visitor.AbstractUastVisitor;

public class AllowAllHostnameVerifierDetector extends Detector implements SourceCodeScanner {

    public static final Issue ISSUE = Issue.create(
            "AllowAllHostnameVerifier",
            "Insecure `HostnameVerifier`",
            "This check looks for use of HostnameVerifier implementations " +
            "whose `verify` method always returns true (thus trusting any hostname) " +
            "which could result in insecure network traffic caused by trusting arbitrary " +
            "hostnames in TLS/SSL certificates presented by peers.",
            Category.SECURITY,
            6,
            Severity.WARNING,
            new Implementation(
                    AllowAllHostnameVerifierDetector.class,
                    Scope.JAVA_FILE_SCOPE
            )
    );

    @Override
    @Nullable
    public List<String> applicableSuperClasses() {
        return Collections.singletonList("javax.net.ssl.HostnameVerifier");
    }

    @Override
    public void visitClass(@NotNull JavaContext context, @NotNull UClass declaration) {
        if (declaration.isInterface()) {
            return;
        }

        for (UMethod method : declaration.getMethods()) {
            if ("verify".equals(method.getName()) && isVerifyMethod(method)) {
                checkVerifyMethod(context, method);
            }
        }
    }

    private boolean isVerifyMethod(UMethod method) {
        if (method.getParameterList().getParametersCount() != 2) {
            return false;
        }
        PsiType returnType = method.getReturnType();
        return returnType != null && PsiType.BOOLEAN.equals(returnType);
    }

    private void checkVerifyMethod(JavaContext context, UMethod method) {
        UExpression body = method.getUastBody();
        if (body == null) {
            return;
        }

        VerifyReturnVisitor visitor = new VerifyReturnVisitor();
        body.accept(visitor);

        if (visitor.hasReturns && visitor.allReturnsTrue) {
            context.report(
                    ISSUE,
                    method,
                    context.getNameLocation(method),
                    "Trusting all hostnames is insecure"
            );
        }
    }

    private static class VerifyReturnVisitor extends AbstractUastVisitor {
        boolean hasReturns = false;
        boolean allReturnsTrue = true;

        @Override
        public boolean visitReturnExpression(@NotNull UReturnExpression node) {
            hasReturns = true;
            UExpression returnVal = node.getReturnExpression();
            if (returnVal == null) {
                allReturnsTrue = false;
            } else {
                Object value = returnVal.evaluate();
                if (!Boolean.TRUE.equals(value)) {
                    allReturnsTrue = false;
                }
            }
            return super.visitReturnExpression(node);
        }
    }
}