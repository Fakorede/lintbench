package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiParameterList;
import com.intellij.psi.PsiType;
import java.util.Collections;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.UBlockExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UReturnExpression;
import org.jetbrains.uast.ULiteralExpression;
import org.jetbrains.uast.UastUtils;

public class AllowAllHostnameVerifierDetector extends Detector
        implements Detector.SourceCodeScanner {

    private static final Implementation IMPLEMENTATION = new Implementation(
            AllowAllHostnameVerifierDetector.class,
            Scope.JAVA_FILE_SCOPE);

    public static final Issue ISSUE = Issue.create(
            "AllowAllHostnameVerifier",
            "Insecure `HostnameVerifier`",
            "This check looks for use of `HostnameVerifier` implementations whose `verify` "
                    + "method always returns true (thus trusting any hostname), which could "
                    + "result in insecure network traffic caused by trusting arbitrary "
                    + "hostnames in TLS/SSL certificates presented by peers.",
            Category.SECURITY,
            6,
            Severity.WARNING,
            IMPLEMENTATION)
            .addMoreInfo("https://goo.gle/AllowAllHostnameVerifier");

    @Override
    @Nullable
    public List<String> applicableAsSuperClasses() {
        return Collections.singletonList("javax.net.ssl.HostnameVerifier");
    }

    @Override
    public void visitClass(@NotNull JavaContext context, @NotNull UClass node) {
        for (UMethod method : node.getMethods()) {
            if (isInsecureVerify(method)) {
                Location location = context.getNameLocation(method);
                context.report(
                        ISSUE,
                        location,
                        "Insecure `HostnameVerifier`: `verify()` always returns true, "
                                + "which trusts any hostname in TLS/SSL certificates.");
            }
        }
    }

    private static boolean isInsecureVerify(UMethod method) {
        if (!"verify".equals(method.getName())) {
            return false;
        }

        PsiParameterList parameterList = method.getParameterList();
        if (parameterList.getParametersCount() != 2) {
            return false;
        }

        PsiParameter[] parameters = parameterList.getParameters();
        if (!"java.lang.String".equals(parameters[0].getType().getCanonicalText())
                || !"javax.net.ssl.SSLSession".equals(parameters[1].getType().getCanonicalText())) {
            return false;
        }

        PsiType returnType = method.getReturnType();
        if (returnType == null || !"boolean".equals(returnType.getCanonicalText())) {
            return false;
        }

        UExpression body = method.getUastBody();
        return body != null && returnsTrue(body);
    }

    private static boolean returnsTrue(UExpression expression) {
        if (expression == null) {
            return false;
        }

        UExpression expr = UastUtils.skipParentheses(expression);
        if (expr == null) {
            return false;
        }

        if (expr instanceof UReturnExpression) {
            return isTrueLiteral(((UReturnExpression) expr).getReturnExpression());
        }

        if (expr instanceof UBlockExpression) {
            List<UExpression> expressions = ((UBlockExpression) expr).getExpressions();
            for (int i = expressions.size() - 1; i >= 0; i--) {
                UExpression last = expressions.get(i);
                if (last instanceof UReturnExpression) {
                    return isTrueLiteral(((UReturnExpression) last).getReturnExpression());
                }
            }
        }

        return isTrueLiteral(expr);
    }

    private static boolean isTrueLiteral(UExpression expression) {
        if (expression == null) {
            return false;
        }

        UExpression expr = UastUtils.skipParentheses(expression);
        if (expr == null) {
            return false;
        }

        return expr instanceof ULiteralExpression
                && Boolean.TRUE.equals(((ULiteralExpression) expr).getValue());
    }
}