package com.android.tools.lint.checks;

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
import java.util.Arrays;
import java.util.List;
import org.jetbrains.uast.UBlockExpression;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UReturnExpression;
import org.jetbrains.uast.UastContextKt;

public class AllowAllHostnameVerifierDetector extends Detector implements SourceCodeScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(AllowAllHostnameVerifierDetector.class, Scope.JAVA_FILE_SCOPE);

    private static final String HOSTNAME_VERIFIER = "javax.net.ssl.HostnameVerifier";
    private static final String ALLOW_ALL_HOSTNAME_VERIFIER =
            "org.apache.http.conn.ssl.AllowAllHostnameVerifier";

    public static final Issue ISSUE =
            Issue.create(
                            "AllowAllHostnameVerifier",
                            "Insecure HostnameVerifier",
                            "A `HostnameVerifier` implementation whose `verify()` method always "
                                    + "returns true trusts any hostname, which can allow an attacker "
                                    + "to intercept TLS/SSL network traffic.",
                            Category.SECURITY,
                            6,
                            Severity.WARNING,
                            IMPLEMENTATION)
                    .setAndroidSpecific(true);

    public AllowAllHostnameVerifierDetector() {}

    @Override
    public List<String> getApplicableConstructorTypes() {
        return Arrays.asList(HOSTNAME_VERIFIER, ALLOW_ALL_HOSTNAME_VERIFIER);
    }

    @Override
    public void visitConstructor(JavaContext context, UCallExpression node, PsiMethod constructor) {
        String fqcn =
                constructor.getContainingClass() != null
                        ? constructor.getContainingClass().getQualifiedName()
                        : null;
        if (ALLOW_ALL_HOSTNAME_VERIFIER.equals(fqcn)) {
            reportInsecure(context, node);
            return;
        }

        UClass anonymousClass = node.getAnonymousClass();
        if (anonymousClass != null) {
            for (PsiMethod method : anonymousClass.getMethods()) {
                if ("verify".equals(method.getName()) && alwaysReturnsTrue(method, context)) {
                    reportInsecure(context, node);
                    return;
                }
            }
        }
    }

    @Override
    public List<String> getApplicableMethodNames() {
        return Arrays.asList("setDefaultHostnameVerifier", "setHostnameVerifier");
    }

    @Override
    public void visitMethodCall(JavaContext context, UCallExpression node, PsiMethod method) {
        List<UExpression> args = node.getValueArguments();
        if (args.isEmpty()) {
            return;
        }

        UExpression arg = args.get(0);
        if (arg instanceof UCallExpression) {
            // Already reported by visitConstructor when the verifier is created.
            return;
        }

        PsiType type = arg.getExpressionType();
        if (type != null && ALLOW_ALL_HOSTNAME_VERIFIER.equals(type.getCanonicalText())) {
            reportInsecure(context, node);
        }
    }

    private static boolean alwaysReturnsTrue(PsiMethod method, JavaContext context) {
        UMethod uMethod =
                (UMethod)
                        UastContextKt.getUastContext(context.getProject())
                                .toUElement(method, UMethod.class);
        if (uMethod == null) {
            return false;
        }

        UExpression body = uMethod.getUastBody();
        if (body == null) {
            return false;
        }

        if (body instanceof UReturnExpression) {
            return isTrueLiteral(((UReturnExpression) body).getReturnExpression());
        }

        if (body instanceof UBlockExpression) {
            for (UExpression expression : ((UBlockExpression) body).getExpressions()) {
                if (expression instanceof UReturnExpression) {
                    return isTrueLiteral(((UReturnExpression) expression).getReturnExpression());
                }
            }
        }

        return false;
    }

    private static boolean isTrueLiteral(UExpression expression) {
        if (expression == null) {
            return false;
        }
        Object value = expression.evaluate();
        return Boolean.TRUE.equals(value);
    }

    private static void reportInsecure(JavaContext context, UElement node) {
        context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Insecure HostnameVerifier: trusting any hostname can allow "
                        + "man-in-the-middle attacks");
    }
}