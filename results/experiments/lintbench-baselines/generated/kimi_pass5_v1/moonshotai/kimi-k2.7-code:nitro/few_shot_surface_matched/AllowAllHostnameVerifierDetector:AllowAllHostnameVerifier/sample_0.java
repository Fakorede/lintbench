package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.Category;
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
import java.util.Arrays;
import java.util.List;
import org.jetbrains.uast.UBlockExpression;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.ULiteralExpression;
import org.jetbrains.uast.UReturnExpression;

public class AllowAllHostnameVerifierDetector extends Detector implements SourceCodeScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(AllowAllHostnameVerifierDetector.class, Scope.JAVA_FILE_SCOPE);

    private static final String HOSTNAME_VERIFIER = "javax.net.ssl.HostnameVerifier";
    private static final String ALLOW_ALL_HOSTNAME_VERIFIER_CLASS =
            "org.apache.http.conn.ssl.AllowAllHostnameVerifier";

    public static final Issue ISSUE =
            Issue.create(
                            "AllowAllHostnameVerifier",
                            "Insecure HostnameVerifier",
                            "Using a HostnameVerifier whose verify() method always returns true "
                                    + "allows an attacker to perform man-in-the-middle attacks by "
                                    + "presenting certificates for any hostname. You should use a "
                                    + "HostnameVerifier that actually validates the hostname.",
                            Category.SECURITY,
                            6,
                            Severity.WARNING,
                            IMPLEMENTATION)
                    .setAndroidSpecific(true);

    public AllowAllHostnameVerifierDetector() {}

    @Nullable
    @Override
    public List<String> getApplicableConstructorTypes() {
        return Arrays.asList(HOSTNAME_VERIFIER, ALLOW_ALL_HOSTNAME_VERIFIER_CLASS);
    }

    @Override
    public void visitConstructor(
            @NonNull JavaContext context,
            @NonNull UCallExpression node,
            @NonNull PsiMethod constructor) {
        PsiClass cls = context.getEvaluator().getTypeClass(node.getExpressionType());
        if (cls != null && isInsecureHostnameVerifier(context, cls)) {
            context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    "Using an allow-all HostnameVerifier is insecure and permits "
                            + "man-in-the-middle attacks");
        }
    }

    @Nullable
    @Override
    public List<String> getApplicableMethodNames() {
        return Arrays.asList("setDefaultHostnameVerifier", "setHostnameVerifier");
    }

    @Override
    public void visitMethodCall(
            @NonNull JavaContext context,
            @NonNull UCallExpression node,
            @NonNull PsiMethod method) {
        List<UExpression> args = node.getValueArguments();
        if (args.isEmpty()) {
            return;
        }
        UExpression first = args.get(0);
        if (first instanceof UCallExpression) {
            PsiClass cls = context.getEvaluator().getTypeClass(((UCallExpression) first).getExpressionType());
            if (cls != null && isInsecureHostnameVerifier(context, cls)) {
                context.report(
                        ISSUE,
                        first,
                        context.getLocation(first),
                        "Setting an allow-all HostnameVerifier is insecure and permits "
                                + "man-in-the-middle attacks");
            }
        }
    }

    private boolean isInsecureHostnameVerifier(@NonNull JavaContext context, @NonNull PsiClass cls) {
        String qualifiedName = cls.getQualifiedName();
        if (ALLOW_ALL_HOSTNAME_VERIFIER_CLASS.equals(qualifiedName)) {
            return true;
        }

        if (!context.getEvaluator().extendsClass(cls, HOSTNAME_VERIFIER, false)) {
            return false;
        }

        for (PsiMethod method : cls.getMethods()) {
            if (!"verify".equals(method.getName())) {
                continue;
            }
            if (method.getParameterList().getParameters().length != 2) {
                continue;
            }
            if (!PsiType.BOOLEAN.equals(method.getReturnType())) {
                continue;
            }
            UExpression body = context.getUastContext().getMethodBody(method);
            if (body != null && returnsTrue(body)) {
                return true;
            }
        }
        return false;
    }

    private boolean returnsTrue(@NonNull UExpression expression) {
        if (expression instanceof UReturnExpression) {
            UExpression ret = ((UReturnExpression) expression).getReturnExpression();
            return isTrueLiteral(ret);
        }
        if (expression instanceof UBlockExpression) {
            List<UExpression> expressions = ((UBlockExpression) expression).getExpressions();
            for (int i = expressions.size() - 1; i >= 0; i--) {
                UExpression e = expressions.get(i);
                if (e instanceof UReturnExpression) {
                    return returnsTrue(e);
                }
            }
        }
        return false;
    }

    private boolean isTrueLiteral(@Nullable UExpression expression) {
        return expression instanceof ULiteralExpression
                && Boolean.TRUE.equals(((ULiteralExpression) expression).getValue());
    }
}