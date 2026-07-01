package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.ConstantEvaluator;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UReturnExpression;
import org.jetbrains.uast.visitor.AbstractUastVisitor;

public class AllowAllHostnameVerifierDetector extends Detector implements SourceCodeScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(AllowAllHostnameVerifierDetector.class, Scope.JAVA_FILE_SCOPE);

    private static final String HOSTNAME_VERIFIER = "javax.net.ssl.HostnameVerifier";
    private static final String ALLOW_ALL_HOSTNAME_VERIFIER =
            "org.apache.http.conn.ssl.AllowAllHostnameVerifier";

    public static final Issue ISSUE =
            Issue.create(
                            "AllowAllHostnameVerifier",
                            "Insecure `HostnameVerifier`",
                            "This check looks for use of `HostnameVerifier` implementations "
                                    + "whose `verify` method always returns true (thus trusting "
                                    + "any hostname), which could result in insecure network "
                                    + "traffic caused by trusting arbitrary hostnames in "
                                    + "TLS/SSL certificates presented by peers.",
                            Category.SECURITY,
                            6,
                            Severity.WARNING,
                            IMPLEMENTATION)
                    .setAndroidSpecific(true)
                    .setMoreInfo("https://goo.gle/AllowAllHostnameVerifier");

    public AllowAllHostnameVerifierDetector() {}

    @Nullable
    @Override
    public List<String> getApplicableConstructorTypes() {
        return Arrays.asList(HOSTNAME_VERIFIER, ALLOW_ALL_HOSTNAME_VERIFIER);
    }

    @Override
    public void visitConstructor(
            @NonNull JavaContext context,
            @NonNull UCallExpression node,
            @NonNull PsiMethod constructor) {
        PsiClass containingClass = constructor.getContainingClass();
        if (containingClass == null) {
            return;
        }

        String qualifiedName = containingClass.getQualifiedName();
        if (ALLOW_ALL_HOSTNAME_VERIFIER.equals(qualifiedName)) {
            reportIssue(context, node);
            return;
        }

        if (context.getEvaluator().implementsInterface(containingClass, HOSTNAME_VERIFIER, false)) {
            PsiMethod verifyMethod = findVerifyMethod(containingClass);
            if (verifyMethod != null && methodAlwaysReturnsTrue(context, verifyMethod)) {
                reportIssue(context, node);
            }
        }
    }

    @Nullable
    @Override
    public List<String> getApplicableMethodNames() {
        return Collections.singletonList("verify");
    }

    @Override
    public void visitMethodCall(
            @NonNull JavaContext context,
            @NonNull UCallExpression node,
            @NonNull PsiMethod method) {
        PsiClass containingClass = method.getContainingClass();
        if (containingClass == null) {
            return;
        }

        String qualifiedName = containingClass.getQualifiedName();
        if (ALLOW_ALL_HOSTNAME_VERIFIER.equals(qualifiedName)) {
            reportIssue(context, node);
            return;
        }

        if (context.getEvaluator().implementsInterface(containingClass, HOSTNAME_VERIFIER, false)) {
            if (method.getParameterList().getParametersCount() == 2
                    && PsiType.BOOLEAN.equals(method.getReturnType())
                    && methodAlwaysReturnsTrue(context, method)) {
                reportIssue(context, node);
            }
        }
    }

    private static PsiMethod findVerifyMethod(@NonNull PsiClass cls) {
        for (PsiMethod method : cls.getMethods()) {
            if ("verify".equals(method.getName())
                    && method.getParameterList().getParametersCount() == 2
                    && PsiType.BOOLEAN.equals(method.getReturnType())) {
                return method;
            }
        }
        return null;
    }

    private static boolean methodAlwaysReturnsTrue(
            @NonNull JavaContext context, @NonNull PsiMethod method) {
        UMethod uMethod = context.getUastContext().getMethod(method);
        if (uMethod == null) {
            return false;
        }

        UExpression body = uMethod.getUastBody();
        if (body == null) {
            return false;
        }

        final List<UReturnExpression> returns = new ArrayList<>();
        body.accept(
                new AbstractUastVisitor() {
                    @Override
                    public boolean visitReturnExpression(@NonNull UReturnExpression node) {
                        returns.add(node);
                        return super.visitReturnExpression(node);
                    }
                });

        if (returns.isEmpty()) {
            return false;
        }

        for (UReturnExpression returnExpr : returns) {
            UExpression expression = returnExpr.getReturnExpression();
            Object value = expression != null ? ConstantEvaluator.evaluate(expression) : null;
            if (!Boolean.TRUE.equals(value)) {
                return false;
            }
        }

        return true;
    }

    private static void reportIssue(@NonNull JavaContext context, @NonNull UElement node) {
        context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Using a `HostnameVerifier` that unconditionally returns `true` is insecure; "
                        + "it permits man-in-the-middle attacks by trusting arbitrary hostnames.");
    }
}