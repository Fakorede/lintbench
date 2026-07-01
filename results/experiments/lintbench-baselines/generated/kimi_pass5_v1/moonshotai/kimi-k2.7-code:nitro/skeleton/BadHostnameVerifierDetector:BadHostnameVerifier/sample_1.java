package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import java.util.Collections;
import java.util.List;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UReturnExpression;
import org.jetbrains.uast.UThrowExpression;
import org.jetbrains.uast.UastUtils;
import org.jetbrains.uast.visitor.AbstractUastVisitor;

public class BadHostnameVerifierDetector extends Detector implements SourceCodeScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(BadHostnameVerifierDetector.class, Scope.JAVA_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "BadHostnameVerifier",
                    "Insecure HostnameVerifier",
                    "This HostnameVerifier implementation unconditionally returns true, which will trust any hostname presented by an SSL/TLS peer. This is insecure because it allows man-in-the-middle attacks.",
                    Category.SECURITY,
                    6,
                    Severity.WARNING,
                    IMPLEMENTATION);

    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList("javax.net.ssl.HostnameVerifier");
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        for (UMethod method : declaration.getMethods()) {
            if (!isVerifyMethod(method)) {
                continue;
            }
            method.accept(new AbstractUastVisitor() {
                @Override
                public boolean visitReturnExpression(@NonNull UReturnExpression node) {
                    checkReturnExpression(context, node);
                    return super.visitReturnExpression(node);
                }

                @Override
                public boolean visitThrowExpression(@NonNull UThrowExpression node) {
                    checkThrowExpression(context, node);
                    return super.visitThrowExpression(node);
                }

                @Override
                public boolean visitCallExpression(@NonNull UCallExpression node) {
                    checkCallExpression(context, node);
                    return super.visitCallExpression(node);
                }
            });
        }
    }

    private static boolean isVerifyMethod(@NonNull UMethod method) {
        PsiMethod psiMethod = method.getJavaPsi();
        if (psiMethod == null) {
            return false;
        }
        return "verify".equals(psiMethod.getName())
                && psiMethod.getParameterList().getParametersCount() == 2;
    }

    private static void checkReturnExpression(@NonNull JavaContext context, @NonNull UReturnExpression node) {
        if (UastUtils.isTrue(node.getReturnExpression())) {
            reportIssue(context, node);
        }
    }

    private static void checkThrowExpression(@NonNull JavaContext context, @NonNull UThrowExpression node) {
        UElement thrown = node.getThrownExpression();
        if (thrown != null && UastUtils.isSubclassOf(thrown.getExpressionType(), "javax.net.ssl.SSLException")) {
            reportIssue(context, node);
        }
    }

    private static void checkCallExpression(@NonNull JavaContext context, @NonNull UCallExpression node) {
        PsiMethod resolved = node.resolve();
        if (resolved == null) {
            return;
        }
        String name = resolved.getName();
        if ("return".equals(name)) {
            return;
        }
        PsiType returnType = resolved.getReturnType();
        if (returnType == null) {
            return;
        }
        if (PsiType.BOOLEAN.equals(returnType) || returnType.getCanonicalText().equals("java.lang.Boolean")) {
            if (isAlwaysTrueCall(node)) {
                reportIssue(context, node);
            }
        }
    }

    private static boolean isAlwaysTrueCall(@NonNull UCallExpression node) {
        String methodName = node.getMethodName();
        if (methodName == null) {
            return false;
        }
        return methodName.equals("verify") || methodName.equals("allow");
    }

    private static void reportIssue(@NonNull JavaContext context, @NonNull UElement node) {
        context.report(
                ISSUE,
                context.getLocation(node),
                "This HostnameVerifier implementation is insecure because it trusts any hostname. Use the default HostnameVerifier instead.");
    }
}