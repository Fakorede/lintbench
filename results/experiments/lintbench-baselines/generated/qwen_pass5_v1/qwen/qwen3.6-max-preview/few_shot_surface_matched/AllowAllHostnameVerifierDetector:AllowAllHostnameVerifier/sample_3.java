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
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiType;
import java.util.Collections;
import java.util.List;
import org.jetbrains.uast.UBlockExpression;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.ULambdaExpression;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UReturnExpression;

public class AllowAllHostnameVerifierDetector extends Detector implements SourceCodeScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(AllowAllHostnameVerifierDetector.class, Scope.JAVA_FILE_SCOPE);

    private static final String HOSTNAME_VERIFIER = "javax.net.ssl.HostnameVerifier";

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
            IMPLEMENTATION);

    @Nullable
    @Override
    public List<String> getApplicableConstructorTypes() {
        return Collections.singletonList(HOSTNAME_VERIFIER);
    }

    @Override
    public void visitConstructor(@NonNull JavaContext context, @NonNull UCallExpression node, @NonNull PsiMethod constructor) {
        UElement parent = node.getUastParent();
        if (!(parent instanceof UClass)) {
            return;
        }
        UClass cls = (UClass) parent;
        if (!cls.isAnonymous()) {
            return;
        }

        for (UMethod method : cls.getMethods()) {
            if ("verify".equals(method.getName())) {
                if (alwaysReturnsTrue(method.getUastBody())) {
                    context.report(ISSUE, node, context.getLocation(node),
                            "HostnameVerifier implementation always returns true, which trusts any hostname and is insecure.");
                    return;
                }
            }
        }
    }

    @Nullable
    @Override
    public List<String> getApplicableMethodNames() {
        return Collections.singletonList("verify");
    }

    @Override
    public void visitMethodCall(@NonNull JavaContext context, @NonNull UCallExpression node, @NonNull PsiMethod method) {
        List<UExpression> args = node.getValueArguments();
        PsiParameter[] params = method.getParameterList().getParameters();

        for (int i = 0; i < args.size() && i < params.length; i++) {
            UExpression arg = args.get(i);
            if (arg instanceof ULambdaExpression) {
                PsiType paramType = params[i].getType();
                if (HOSTNAME_VERIFIER.equals(paramType.getCanonicalText())) {
                    ULambdaExpression lambda = (ULambdaExpression) arg;
                    if (alwaysReturnsTrue(lambda.getBody())) {
                        context.report(ISSUE, arg, context.getLocation(arg),
                                "HostnameVerifier lambda always returns true, which trusts any hostname and is insecure.");
                    }
                }
            }
        }
    }

    private static boolean alwaysReturnsTrue(@Nullable UExpression body) {
        if (body == null) {
            return false;
        }
        if (body instanceof UBlockExpression) {
            UBlockExpression block = (UBlockExpression) body;
            for (UExpression stmt : block.getExpressions()) {
                if (stmt instanceof UReturnExpression) {
                    UExpression retExpr = ((UReturnExpression) stmt).getReturnExpression();
                    if (retExpr != null && Boolean.TRUE.equals(retExpr.evaluate())) {
                        return true;
                    }
                }
            }
        } else {
            return Boolean.TRUE.equals(body.evaluate());
        }
        return false;
    }
}