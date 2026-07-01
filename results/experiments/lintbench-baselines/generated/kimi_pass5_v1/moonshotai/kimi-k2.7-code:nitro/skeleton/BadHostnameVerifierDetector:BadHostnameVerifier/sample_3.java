package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiParameterList;
import java.util.Collections;
import java.util.List;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.ULiteralExpression;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UReturnExpression;
import org.jetbrains.uast.UThrowExpression;
import org.jetbrains.uast.visitor.AbstractUastVisitor;

public class BadHostnameVerifierDetector extends Detector implements SourceCodeScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(BadHostnameVerifierDetector.class, Scope.JAVA_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "BadHostnameVerifier",
                    "Insecure HostnameVerifier",
                    "Implementations of `HostnameVerifier` whose `verify` method always returns true "
                            + "trust any hostname, which can result in insecure network traffic caused "
                            + "by trusting arbitrary hostnames in TLS/SSL certificates presented by peers.",
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
            UElement body = method.getUastBody();
            if (body == null) {
                continue;
            }
            Analysis analysis = new Analysis();
            body.accept(new AbstractUastVisitor() {
                @Override
                public boolean visitReturnExpression(@NonNull UReturnExpression node) {
                    BadHostnameVerifierDetector.this.visitReturnExpression(node, analysis);
                    return super.visitReturnExpression(node);
                }

                @Override
                public boolean visitThrowExpression(@NonNull UThrowExpression node) {
                    BadHostnameVerifierDetector.this.visitThrowExpression(node, analysis);
                    return super.visitThrowExpression(node);
                }

                @Override
                public boolean visitCallExpression(@NonNull UCallExpression node) {
                    BadHostnameVerifierDetector.this.visitCallExpression(node, analysis);
                    return super.visitCallExpression(node);
                }
            });
            if (analysis.isBad()) {
                context.report(
                        ISSUE,
                        method,
                        context.getLocation(method),
                        "verify always returns true, which could result in insecure network traffic due to trusting arbitrary hostnames");
            }
        }
    }

    private void visitThrowExpression(@NonNull UThrowExpression node, @NonNull Analysis analysis) {
        analysis.mHasThrow = true;
    }

    private void visitCallExpression(@NonNull UCallExpression node, @NonNull Analysis analysis) {
        analysis.mHasCall = true;
    }

    private void visitReturnExpression(@NonNull UReturnExpression node, @NonNull Analysis analysis) {
        if (isTrueLiteral(node.getReturnExpression())) {
            analysis.mHasReturnTrue = true;
        } else {
            analysis.mHasReturnFalse = true;
        }
    }

    private static boolean isVerifyMethod(@NonNull UMethod method) {
        if (!"verify".equals(method.getName())) {
            return false;
        }
        PsiParameterList parameterList = method.getParameterList();
        if (parameterList.getParametersCount() != 2) {
            return false;
        }
        PsiParameter[] parameters = parameterList.getParameters();
        return parameters[0].getType().equalsToText("java.lang.String")
                && parameters[1].getType().equalsToText("javax.net.ssl.SSLSession");
    }

    private static boolean isTrueLiteral(UElement expression) {
        if (expression instanceof ULiteralExpression) {
            Object value = ((ULiteralExpression) expression).getValue();
            return Boolean.TRUE.equals(value);
        }
        return false;
    }

    private static class Analysis {
        boolean mHasReturnTrue;
        boolean mHasReturnFalse;
        boolean mHasThrow;
        boolean mHasCall;

        boolean isBad() {
            return mHasReturnTrue && !mHasReturnFalse && !mHasThrow && !mHasCall;
        }
    }
}