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
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.ULambdaExpression;
import org.jetbrains.uast.ULiteralExpression;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UReturnExpression;

public class AllowAllHostnameVerifierDetector extends Detector implements SourceCodeScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(AllowAllHostnameVerifierDetector.class, Scope.JAVA_FILE_SCOPE);

    private static final String HOSTNAME_VERIFIER = "javax.net.ssl.HostnameVerifier";

    public static final Issue ISSUE =
            Issue.create(
                    "AllowAllHostnameVerifier",
                    "Insecure HostnameVerifier",
                    "This check looks for use of HostnameVerifier implementations whose `verify` "
                            + "method always returns true (thus trusting any hostname) which could "
                            + "result in insecure network traffic caused by trusting arbitrary "
                            + "hostnames in TLS/SSL certificates presented by peers.",
                    Category.SECURITY,
                    5,
                    Severity.WARNING,
                    IMPLEMENTATION)
                    .setAndroidSpecific(true);

    public AllowAllHostnameVerifierDetector() {}

    @Nullable
    @Override
    public List<String> getApplicableConstructorTypes() {
        return Collections.singletonList(HOSTNAME_VERIFIER);
    }

    @Override
    public void visitConstructor(@NonNull JavaContext context, @NonNull UCallExpression call, @NonNull PsiMethod constructor) {
        UClass uClass = call.getContainingUClass();
        if (uClass != null && uClass.isAnonymous()) {
            checkHostnameVerifierImplementation(context, uClass);
        }
    }

    @Nullable
    @Override
    public List<String> getApplicableMethodNames() {
        return Arrays.asList("setHostnameVerifier", "setDefaultHostnameVerifier");
    }

    @Override
    public void visitMethodCall(@NonNull JavaContext context, @NonNull UCallExpression call, @NonNull PsiMethod method) {
        List<UExpression> args = call.getValueArguments();
        if (args.isEmpty()) {
            return;
        }
        UExpression arg = args.get(0);
        if (arg instanceof ULambdaExpression) {
            ULambdaExpression lambda = (ULambdaExpression) arg;
            if (returnsTrue(lambda.getBody())) {
                context.report(ISSUE, call, context.getLocation(call),
                        "Insecure HostnameVerifier implementation: verify() always returns true");
            }
        } else if (arg instanceof UCallExpression) {
            UCallExpression ctor = (UCallExpression) arg;
            UClass uClass = ctor.getContainingUClass();
            if (uClass != null && uClass.isAnonymous()) {
                checkHostnameVerifierImplementation(context, uClass);
            }
        }
    }

    private void checkHostnameVerifierImplementation(@NonNull JavaContext context, @NonNull UClass uClass) {
        for (UMethod m : uClass.getMethods()) {
            if ("verify".equals(m.getName())) {
                UExpression body = m.getUastBody();
                if (body != null && returnsTrue(body)) {
                    context.report(ISSUE, m, context.getNameLocation(m),
                            "Insecure HostnameVerifier implementation: verify() always returns true");
                }
            }
        }
    }

    private boolean returnsTrue(@Nullable UElement body) {
        if (body == null) {
            return false;
        }
        UExpression expr = null;
        if (body instanceof UReturnExpression) {
            expr = ((UReturnExpression) body).getReturnExpression();
        } else if (body instanceof UBlockExpression) {
            List<UExpression> statements = ((UBlockExpression) body).getExpressions();
            if (statements.size() == 1 && statements.get(0) instanceof UReturnExpression) {
                expr = ((UReturnExpression) statements.get(0)).getReturnExpression();
            }
        } else if (body instanceof UExpression) {
            expr = (UExpression) body;
        }

        if (expr instanceof ULiteralExpression) {
            Object value = ((ULiteralExpression) expr).getValue();
            return Boolean.TRUE.equals(value);
        }
        return false;
    }
}