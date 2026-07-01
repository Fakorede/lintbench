package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiLambdaExpression;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiParameterList;
import com.intellij.psi.PsiReturnStatement;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class BadHostnameVerifierDetector extends Detector implements Detector.JavaPsiScanner {

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
    public List<Class<? extends PsiElement>> getApplicablePsiTypes() {
        return Arrays.asList(PsiMethod.class, PsiLambdaExpression.class);
    }

    @Override
    public JavaElementVisitor createJavaVisitor(@NotNull final JavaContext context) {
        return new JavaElementVisitor() {
            @Override
            public void visitMethod(@NotNull PsiMethod method) {
                super.visitMethod(method);
                if (!isVerifyMethod(method)) {
                    return;
                }
                if (!isInHostnameVerifier(method)) {
                    return;
                }
                PsiCodeBlock body = method.getBody();
                if (body != null && alwaysReturnsTrue(body, context)) {
                    context.report(ISSUE, context.getNameLocation(method), MESSAGE);
                }
            }

            @Override
            public void visitLambdaExpression(@NotNull PsiLambdaExpression expression) {
                super.visitLambdaExpression(expression);
                PsiType type = expression.getFunctionalInterfaceType();
                if (type == null || !HOSTNAME_VERIFIER.equals(type.getCanonicalText())) {
                    return;
                }
                if (alwaysReturnsTrue(expression.getBody(), context)) {
                    context.report(ISSUE, context.getLocation(expression), MESSAGE);
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
        PsiClass containingClass = method.getContainingClass();
        return containingClass != null
                && InheritanceUtil.isInheritor(containingClass, HOSTNAME_VERIFIER);
    }

    private static boolean alwaysReturnsTrue(@Nullable PsiElement body, @NotNull JavaContext context) {
        if (body == null) {
            return false;
        }
        Collection<PsiReturnStatement> returns = PsiTreeUtil.findChildrenOfType(body, PsiReturnStatement.class);
        if (returns.isEmpty()) {
            if (body instanceof PsiExpression) {
                return evaluatesToTrue((PsiExpression) body, context);
            }
            return false;
        }
        for (PsiReturnStatement returnStatement : returns) {
            if (!evaluatesToTrue(returnStatement.getReturnValue(), context)) {
                return false;
            }
        }
        return true;
    }

    private static boolean evaluatesToTrue(@Nullable PsiExpression expression, @NotNull JavaContext context) {
        if (expression == null) {
            return false;
        }
        Object value = JavaPsiFacade.getInstance(expression.getProject())
                .getConstantEvaluationHelper()
                .computeConstantExpression(expression);
        return Boolean.TRUE.equals(value);
    }
}