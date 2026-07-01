package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.UElementHandler;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.jetbrains.uast.UBlockExpression;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.ULiteralExpression;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UReferenceExpression;
import org.jetbrains.uast.UReturnExpression;

public class AllowAllHostnameVerifierDetector extends Detector implements SourceCodeScanner {

    private static final String ALLOW_ALL_HOSTNAME_VERIFIER =
            "org.apache.http.conn.ssl.AllowAllHostnameVerifier";
    private static final String HOSTNAME_VERIFIER = "javax.net.ssl.HostnameVerifier";
    private static final String X509_HOSTNAME_VERIFIER =
            "org.apache.http.conn.ssl.X509HostnameVerifier";
    private static final String SSL_SOCKET_FACTORY =
            "org.apache.http.conn.ssl.SSLSocketFactory";
    private static final String ALLOW_ALL_HOSTNAME_VERIFIER_FIELD =
            "ALLOW_ALL_HOSTNAME_VERIFIER";

    private static final String MESSAGE =
            "Using an insecure `HostnameVerifier` that accepts every hostname can expose "
                    + "the app to man-in-the-middle attacks. Use a strict hostname verifier "
                    + "that validates the certificate hostname.";

    private static final Implementation IMPLEMENTATION =
            new Implementation(AllowAllHostnameVerifierDetector.class, Scope.JAVA_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "AllowAllHostnameVerifier",
                    "Insecure `HostnameVerifier`",
                    "Using a `HostnameVerifier` that accepts any certificate hostname (for "
                            + "example by always returning `true` from `verify`) makes the "
                            + "application vulnerable to man-in-the-middle attacks. Network "
                            + "traffic should only be trusted when the server's hostname matches "
                            + "the one in the presented certificate.",
                    Category.SECURITY,
                    6,
                    Severity.WARNING,
                    IMPLEMENTATION);

    @Override
    public List<String> getApplicableConstructorTypes() {
        return Collections.singletonList(ALLOW_ALL_HOSTNAME_VERIFIER);
    }

    @Override
    public void visitConstructor(
            @NonNull JavaContext context,
            @NonNull UCallExpression node,
            @NonNull PsiMethod constructor) {
        reportIssue(context, node);
    }

    @Override
    public List<String> getApplicableMethodNames() {
        return Arrays.asList("setDefaultHostnameVerifier", "setHostnameVerifier");
    }

    @Override
    public void visitMethodCall(
            @NonNull JavaContext context,
            @NonNull UCallExpression node,
            @NonNull PsiMethod method) {
        for (UExpression argument : node.getValueArguments()) {
            if (isAllowAllHostnameVerifierReference(argument)) {
                reportIssue(context, node);
                return;
            }
        }
    }

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.<Class<? extends UElement>>singletonList(UMethod.class);
    }

    @Override
    public UElementHandler createUastHandler(@NonNull JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitMethod(@NonNull UMethod node) {
                if (!"verify".equals(node.getName())) {
                    return;
                }
                if (!returnsTrue(node)) {
                    return;
                }
                PsiClass containingClass = node.getContainingClass();
                if (containingClass != null && implementsHostnameVerifier(containingClass)) {
                    reportIssue(context, node);
                }
            }
        };
    }

    private static void reportIssue(@NonNull JavaContext context, @NonNull UElement node) {
        context.report(ISSUE, node, context.getLocation(node), MESSAGE);
    }

    private static boolean returnsTrue(@NonNull UMethod method) {
        UExpression body = method.getUastBody();
        if (body == null) {
            return false;
        }
        if (body instanceof UBlockExpression) {
            List<UExpression> expressions = ((UBlockExpression) body).getExpressions();
            if (expressions.size() == 1) {
                UExpression expression = expressions.get(0);
                if (expression instanceof UReturnExpression) {
                    return isTrue(((UReturnExpression) expression).getReturnExpression());
                }
            }
            return false;
        }
        return isTrue(body);
    }

    private static boolean isTrue(UExpression expression) {
        return expression instanceof ULiteralExpression
                && Boolean.TRUE.equals(((ULiteralExpression) expression).getValue());
    }

    private static boolean implementsHostnameVerifier(@NonNull PsiClass cls) {
        for (PsiClassType type : cls.getImplementsListTypes()) {
            PsiClass resolved = type.resolve();
            if (resolved != null) {
                String qualifiedName = resolved.getQualifiedName();
                if (HOSTNAME_VERIFIER.equals(qualifiedName)
                        || X509_HOSTNAME_VERIFIER.equals(qualifiedName)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isAllowAllHostnameVerifierReference(@NonNull UExpression expression) {
        if (!(expression instanceof UReferenceExpression)) {
            return false;
        }
        PsiElement resolved = ((UReferenceExpression) expression).resolve();
        if (!(resolved instanceof PsiField)) {
            return false;
        }
        PsiField field = (PsiField) resolved;
        if (!ALLOW_ALL_HOSTNAME_VERIFIER_FIELD.equals(field.getName())) {
            return false;
        }
        PsiClass containingClass = field.getContainingClass();
        return containingClass != null
                && SSL_SOCKET_FACTORY.equals(containingClass.getQualifiedName());
    }
}