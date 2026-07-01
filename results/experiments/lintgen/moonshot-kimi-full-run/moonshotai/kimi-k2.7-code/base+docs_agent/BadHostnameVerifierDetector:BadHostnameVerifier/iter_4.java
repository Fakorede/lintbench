package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.UElementHandler;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiParameterList;
import com.intellij.psi.util.InheritanceUtil;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.ULambdaExpression;
import org.jetbrains.uast.ULiteralExpression;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UReturnExpression;
import org.jetbrains.uast.visitor.AbstractUastVisitor;

public class BadHostnameVerifierDetector extends Detector implements Detector.UastScanner {

    private static final Implementation IMPLEMENTATION = new Implementation(
            BadHostnameVerifierDetector.class,
            Scope.JAVA_FILE_SCOPE
    );

    public static final Issue ISSUE = Issue.create(
            "BadHostnameVerifier",
            "Insecure HostnameVerifier",
            "This `HostnameVerifier` implementation always returns `true`, which causes the " +
            "app to trust any hostname presented by the peer. This bypasses hostname " +
            "verification in TLS/SSL connections and can allow man-in-the-middle attacks.\n" +
            "See https://goo.gle/BadHostnameVerifier",
            Category.SECURITY,
            6,
            Severity.WARNING,
            IMPLEMENTATION
    );

    private static final String HOSTNAME_VERIFIER = "javax.net.ssl.HostnameVerifier";
    private static final String MESSAGE = "Insecure `HostnameVerifier`: `verify` always returns `true`";

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Arrays.asList(UMethod.class, ULambdaExpression.class);
    }

    @Override
    public UElementHandler createUastHandler(@NotNull final JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitMethod(@NotNull UMethod node) {
                PsiMethod psiMethod = node.getJavaPsi();
                if (psiMethod == null || !isVerifyMethod(psiMethod)) {
                    return;
                }
                if (!isInHostnameVerifier(psiMethod)) {
                    return;
                }
                UExpression body = node.getUastBody();
                if (body != null && alwaysReturnsTrue(body)) {
                    context.report(ISSUE, context.getNameLocation(node), MESSAGE);
                }
            }

            @Override
            public void visitLambdaExpression(@NotNull ULambdaExpression node) {
                PsiType type = node.getFunctionalInterfaceType();
                if (type == null || !HOSTNAME_VERIFIER.equals(type.getCanonicalText())) {
                    return;
                }
                UExpression body = node.getBody();
                if (body != null && alwaysReturnsTrue(body)) {
                    context.report(ISSUE, context.getLocation(node), MESSAGE);
                }
            }
        };
    }

    private static boolean isVerifyMethod(@NotNull PsiMethod method) {
        if (!"verify".equals(method.getName())) {
            return false;
        }
        PsiParameterList parameterList = method.getParameterList();
        if (parameterList.getParametersCount() != 2) {
            return false;
        }
        PsiParameter[] parameters = parameterList.getParameters();
        return "java.lang.String".equals(parameters[0].getType().getCanonicalText())
                && "javax.net.ssl.SSLSession".equals(parameters[1].getType().getCanonicalText());
    }

    private static boolean isInHostnameVerifier(@NotNull PsiMethod method) {
        return method.getContainingClass() != null
                && InheritanceUtil.isInheritor(method.getContainingClass(), HOSTNAME_VERIFIER);
    }

    private static boolean alwaysReturnsTrue(@NotNull UExpression body) {
        ReturnVisitor visitor = new ReturnVisitor();
        body.accept(visitor);
        if (visitor.foundReturn) {
            return visitor.allTrue;
        }
        return isTrue(body);
    }

    private static boolean isTrue(@Nullable UExpression expression) {
        if (!(expression instanceof ULiteralExpression)) {
            return false;
        }
        Object value = ((ULiteralExpression) expression).getValue();
        return Boolean.TRUE.equals(value);
    }

    private static class ReturnVisitor extends AbstractUastVisitor {
        boolean foundReturn = false;
        boolean allTrue = true;

        @Override
        public boolean visitReturnExpression(@NotNull UReturnExpression node) {
            foundReturn = true;
            if (!isTrue(node.getReturnExpression())) {
                allTrue = false;
            }
            return super.visitReturnExpression(node);
        }
    }
}