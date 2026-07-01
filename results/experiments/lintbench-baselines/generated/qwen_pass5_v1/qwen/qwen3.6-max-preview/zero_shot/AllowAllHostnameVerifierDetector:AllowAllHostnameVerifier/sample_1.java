package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.UBlockExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UComment;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UElementHandler;
import org.jetbrains.uast.UEmptyExpression;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.ULiteralExpression;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UReturnExpression;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AllowAllHostnameVerifierDetector extends Detector implements Detector.UastScanner {

    public static final Issue ISSUE = Issue.create(
        "AllowAllHostnameVerifier",
        "Insecure HostnameVerifier",
        "This check looks for use of HostnameVerifier implementations whose `verify` method " +
        "always returns true (thus trusting any hostname) which could result in insecure " +
        "network traffic caused by trusting arbitrary hostnames in TLS/SSL certificates " +
        "presented by peers.",
        Category.SECURITY,
        5,
        Severity.WARNING,
        new Implementation(
            AllowAllHostnameVerifierDetector.class,
            Scope.JAVA_FILE_SCOPE
        )
    );

    private static final String HOSTNAME_VERIFIER = "javax.net.ssl.HostnameVerifier";

    @NotNull
    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.singletonList(UMethod.class);
    }

    @Nullable
    @Override
    public UElementHandler createUastHandler(@NotNull JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitMethod(@NotNull UMethod method) {
                if (!"verify".equals(method.getName())) {
                    return;
                }
                if (method.getUastParameters().size() != 2) {
                    return;
                }

                UClass containingClass = method.getContainingUClass();
                if (containingClass == null) {
                    return;
                }

                if (!context.getEvaluator().implementsInterface(containingClass, HOSTNAME_VERIFIER, false)) {
                    return;
                }

                UExpression body = method.getUastBody();
                if (body == null) {
                    return;
                }

                if (isAlwaysReturningTrue(body)) {
                    context.report(ISSUE, context.getLocation(method),
                        "HostnameVerifier verifies all hostnames. This is insecure and allows MITM attacks.");
                }
            }
        };
    }

    private static boolean isAlwaysReturningTrue(@NotNull UExpression body) {
        if (body instanceof UReturnExpression) {
            return isTrueLiteral(((UReturnExpression) body).getReturnExpression());
        }
        if (body instanceof UBlockExpression) {
            List<UExpression> statements = ((UBlockExpression) body).getExpressions();
            List<UExpression> codeStatements = new ArrayList<>();
            for (UExpression stmt : statements) {
                if (!(stmt instanceof UComment) && !(stmt instanceof UEmptyExpression)) {
                    codeStatements.add(stmt);
                }
            }
            if (codeStatements.size() == 1 && codeStatements.get(0) instanceof UReturnExpression) {
                return isTrueLiteral(((UReturnExpression) codeStatements.get(0)).getReturnExpression());
            }
        }
        return false;
    }

    private static boolean isTrueLiteral(@Nullable UExpression expr) {
        if (expr instanceof ULiteralExpression) {
            Object value = ((ULiteralExpression) expr).getValue();
            return Boolean.TRUE.equals(value);
        }
        return false;
    }
}