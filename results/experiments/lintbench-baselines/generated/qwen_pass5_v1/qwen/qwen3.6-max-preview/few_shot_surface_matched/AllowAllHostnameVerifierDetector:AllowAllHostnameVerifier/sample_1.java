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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.jetbrains.uast.UBlockExpression;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.ULambdaExpression;
import org.jetbrains.uast.ULiteralExpression;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UReturnExpression;
import org.jetbrains.uast.UastCallKind;
import org.jetbrains.uast.UastUtils;

public class AllowAllHostnameVerifierDetector extends Detector implements SourceCodeScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(AllowAllHostnameVerifierDetector.class, Scope.JAVA_FILE_SCOPE);

    public static final Issue ISSUE = Issue.create(
            "AllowAllHostnameVerifier",
            "Insecure HostnameVerifier",
            "This check looks for use of HostnameVerifier implementations whose `verify` method "
                    + "always returns true (thus trusting any hostname) which could result in insecure "
                    + "network traffic caused by trusting arbitrary hostnames in TLS/SSL certificates "
                    + "presented by peers.",
            Category.SECURITY,
            6,
            Severity.WARNING,
            IMPLEMENTATION)
            .setAndroidSpecific(true);

    public AllowAllHostnameVerifierDetector() {}

    @Nullable
    @Override
    public List<String> getApplicableConstructorTypes() {
        return Collections.singletonList("javax.net.ssl.HostnameVerifier");
    }

    @Override
    public void visitConstructor(@NonNull JavaContext context, @NonNull UCallExpression node, @NonNull PsiMethod constructor) {
        if (node.getKind() != UastCallKind.CONSTRUCTOR_CALL) {
            return;
        }
        UClass uClass = UastUtils.getContainingUClass(node);
        if (uClass != null && isAllowAllClass(uClass)) {
            context.report(ISSUE, node, context.getLocation(node),
                    "Insecure HostnameVerifier implementation: verify() always returns true");
        }
    }

    @Nullable
    @Override
    public List<String> getApplicableMethodNames() {
        return Arrays.asList("setHostnameVerifier", "setDefaultHostnameVerifier");
    }

    @Override
    public void visitMethodCall(@NonNull JavaContext context, @NonNull UCallExpression node, @NonNull PsiMethod method) {
        List<UExpression> args = node.getValueArguments();
        if (args.isEmpty()) {
            return;
        }
        checkExpression(context, args.get(0));
    }

    private void checkExpression(@NonNull JavaContext context, @NonNull UExpression expression) {
        if (expression instanceof ULambdaExpression) {
            ULambdaExpression lambda = (ULambdaExpression) expression;
            if (isReturningTrue(lambda.getBody())) {
                context.report(ISSUE, expression, context.getLocation(expression),
                        "Insecure HostnameVerifier implementation: verify() always returns true");
            }
        } else if (expression instanceof UCallExpression) {
            UCallExpression call = (UCallExpression) expression;
            if (call.getKind() == UastCallKind.CONSTRUCTOR_CALL) {
                UClass uClass = UastUtils.getContainingUClass(call);
                if (uClass != null && isAllowAllClass(uClass)) {
                    context.report(ISSUE, expression, context.getLocation(expression),
                            "Insecure HostnameVerifier implementation: verify() always returns true");
                }
            }
        }
    }

    private boolean isAllowAllClass(@NonNull UClass uClass) {
        for (UMethod method : uClass.getMethods()) {
            if ("verify".equals(method.getName())) {
                UExpression body = method.getUastBody();
                return isReturningTrue(body);
            }
        }
        return false;
    }

    private boolean isReturningTrue(@Nullable UExpression body) {
        if (body == null) {
            return false;
        }
        if (body instanceof ULiteralExpression) {
            return Boolean.TRUE.equals(((ULiteralExpression) body).getValue());
        }
        if (body instanceof UReturnExpression) {
            UExpression ret = ((UReturnExpression) body).getReturnExpression();
            if (ret instanceof ULiteralExpression) {
                return Boolean.TRUE.equals(((ULiteralExpression) ret).getValue());
            }
        }
        if (body instanceof UBlockExpression) {
            List<UExpression> stmts = ((UBlockExpression) body).getExpressions();
            if (stmts.size() == 1) {
                return isReturningTrue(stmts.get(0));
            }
        }
        return false;
    }
}