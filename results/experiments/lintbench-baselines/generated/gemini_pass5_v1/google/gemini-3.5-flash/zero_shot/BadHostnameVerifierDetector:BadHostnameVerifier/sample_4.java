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
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.ULiteralExpression;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UParenthesizedExpression;
import org.jetbrains.uast.UReturnExpression;
import org.jetbrains.uast.visitor.AbstractUastVisitor;

public class BadHostnameVerifierDetector extends Detector implements SourceCodeScanner {

    public static final Issue ISSUE = Issue.create(
            "BadHostnameVerifier",
            "Insecure HostnameVerifier",
            "This check looks for implementations of `HostnameVerifier` whose " +
            "`verify` method always returns true (thus trusting any hostname) " +
            "which could result in insecure network traffic caused by trusting " +
            "arbitrary hostnames in TLS/SSL certificates presented by peers.",
            Category.SECURITY,
            6,
            Severity.ERROR,
            new Implementation(BadHostnameVerifierDetector.class, Scope.JAVA_FILE_SCOPE)
    );

    @Nullable
    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList("javax.net.ssl.HostnameVerifier");
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        for (UMethod method : declaration.getMethods()) {
            if ("verify".equals(method.getName())) {
                PsiMethod psiMethod = method.getJavaPsi();
                if (isVerifyMethod(psiMethod)) {
                    if (alwaysReturnsTrue(method)) {
                        context.report(
                                ISSUE,
                                method,
                                context.getLocation(method),
                                "This `verify` method always returns `true`, which effectively " +
                                "disables hostname verification and makes network traffic vulnerable " +
                                "to Man-in-the-Middle attacks."
                        );
                    }
                }
            }
        }
    }

    private static boolean isVerifyMethod(PsiMethod method) {
        if (method.getParameterList().getParametersCount() != 2) {
            return false;
        }
        PsiType returnType = method.getReturnType();
        if (returnType == null || !PsiType.BOOLEAN.equals(returnType)) {
            return false;
        }
        PsiType param1 = method.getParameterList().getParameters()[0].getType();
        PsiType param2 = method.getParameterList().getParameters()[1].getType();
        return param1.equalsToText("java.lang.String") && param2.equalsToText("javax.net.ssl.SSLSession");
    }

    private static boolean alwaysReturnsTrue(UMethod method) {
        UExpression body = method.getUastBody();
        if (body == null) {
            return false;
        }
        if (isBooleanLiteral(body, true)) {
            return true;
        }
        List<UReturnExpression> returns = new ArrayList<>();
        body.accept(new AbstractUastVisitor() {
            @Override
            public boolean visitReturnExpression(@NonNull UReturnExpression node) {
                returns.add(node);
                return super.visitReturnExpression(node);
            }
        });
        if (returns.isEmpty()) {
            return false;
        }
        for (UReturnExpression ret : returns) {
            UExpression expr = ret.getReturnExpression();
            if (expr == null || !isBooleanLiteral(expr, true)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isBooleanLiteral(UExpression expression, boolean value) {
        if (expression instanceof ULiteralExpression) {
            Object val = ((ULiteralExpression) expression).getValue();
            return val instanceof Boolean && (Boolean) val == value;
        }
        if (expression instanceof UParenthesizedExpression) {
            return isBooleanLiteral(((UParenthesizedExpression) expression).getExpression(), value);
        }
        return false;
    }
}