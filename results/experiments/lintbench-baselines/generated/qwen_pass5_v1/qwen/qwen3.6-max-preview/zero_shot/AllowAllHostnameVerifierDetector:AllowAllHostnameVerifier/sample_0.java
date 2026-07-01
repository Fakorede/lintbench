package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.JavaEvaluator;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.UElementHandler;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NonNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.UBlockExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.ULiteralExpression;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UParameter;
import org.jetbrains.uast.UReturnExpression;

import java.util.Collections;
import java.util.List;

public class AllowAllHostnameVerifierDetector extends Detector implements Detector.UastScanner {

    public static final Issue ISSUE = Issue.create(
        "AllowAllHostnameVerifier",
        "Insecure HostnameVerifier",
        "This check looks for use of HostnameVerifier implementations whose `verify` method " +
        "always returns true (thus trusting any hostname) which could result in insecure network " +
        "traffic caused by trusting arbitrary hostnames in TLS/SSL certificates presented by peers.",
        Category.SECURITY,
        6,
        Severity.WARNING,
        new Implementation(
            AllowAllHostnameVerifierDetector.class,
            Scope.JAVA_FILE_SCOPE
        )
    );

    @Nullable
    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.singletonList(UMethod.class);
    }

    @Nullable
    @Override
    public UElementHandler createUastHandler(@NonNull JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitMethod(@NonNull UMethod method) {
                if (!"verify".equals(method.getName())) {
                    return;
                }

                JavaEvaluator evaluator = context.getEvaluator();
                PsiType returnType = method.getReturnType();
                if (returnType == null || !returnType.equalsToText("boolean")) {
                    return;
                }

                List<UParameter> parameters = method.getUastParameters();
                if (parameters.size() != 2) {
                    return;
                }

                PsiType param1 = parameters.get(0).getType();
                PsiType param2 = parameters.get(1).getType();
                if (param1 == null || param2 == null) {
                    return;
                }

                if (!param1.getCanonicalText().equals("java.lang.String") ||
                    !param2.getCanonicalText().equals("javax.net.ssl.SSLSession")) {
                    return;
                }

                UClass containingClass = method.getContainingClass();
                if (containingClass == null ||
                    !evaluator.implementsInterface(containingClass, "javax.net.ssl.HostnameVerifier", false)) {
                    return;
                }

                UExpression body = method.getUastBody();
                if (body == null) {
                    return;
                }

                UReturnExpression returnExpr = null;
                if (body instanceof UBlockExpression) {
                    List<UExpression> statements = ((UBlockExpression) body).getExpressions();
                    if (statements.size() == 1 && statements.get(0) instanceof UReturnExpression) {
                        returnExpr = (UReturnExpression) statements.get(0);
                    }
                } else if (body instanceof UReturnExpression) {
                    returnExpr = (UReturnExpression) body;
                }

                if (returnExpr != null) {
                    UExpression returnedValue = returnExpr.getReturnExpression();
                    if (returnedValue instanceof ULiteralExpression) {
                        Object value = ((ULiteralExpression) returnedValue).getValue();
                        if (Boolean.TRUE.equals(value)) {
                            context.report(ISSUE, context.getLocation(returnExpr),
                                "HostnameVerifier.verify() always returns true, which trusts all hostnames and compromises SSL/TLS security.");
                        }
                    }
                }
            }
        };
    }
}