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
import com.intellij.psi.PsiAnonymousClass;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiVariable;
import java.util.Arrays;
import java.util.List;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.ULiteralExpression;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UReturnExpression;
import org.jetbrains.uast.visitor.AbstractUastVisitor;

public class AllowAllHostnameVerifierDetector extends Detector implements SourceCodeScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(AllowAllHostnameVerifierDetector.class, Scope.JAVA_FILE_SCOPE);

    private static final String HOSTNAME_VERIFIER = "javax.net.ssl.HostnameVerifier";
    private static final String ALLOW_ALL_HOSTNAME_VERIFIER =
            "org.apache.http.conn.ssl.AllowAllHostnameVerifier";
    private static final String SET_HOSTNAME_VERIFIER = "setHostnameVerifier";
    private static final String SET_DEFAULT_HOSTNAME_VERIFIER = "setDefaultHostnameVerifier";
    private static final String ALLOW_ALL_HOSTNAME_VERIFIER_FIELD =
            "org.apache.http.conn.ssl.SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER";

    public static final Issue ISSUE =
            Issue.create(
                    "AllowAllHostnameVerifier",
                    "Insecure HostnameVerifier",
                    "This check looks for use of HostnameVerifier implementations whose "
                            + "`verify` method always returns true (thus trusting any hostname), "
                            + "which could result in insecure network traffic caused by trusting "
                            + "arbitrary hostnames in TLS/SSL certificates presented by peers.",
                    Category.SECURITY,
                    6,
                    Severity.WARNING,
                    IMPLEMENTATION);

    public AllowAllHostnameVerifierDetector() {}

    @Override
    public List<String> getApplicableConstructorTypes() {
        return Arrays.asList(HOSTNAME_VERIFIER, ALLOW_ALL_HOSTNAME_VERIFIER);
    }

    @Override
    public void visitConstructor(
            @NonNull JavaContext context,
            @NonNull UCallExpression node,
            @NonNull PsiMethod constructor) {
        PsiClass containingClass = constructor.getContainingClass();
        if (containingClass == null) {
            return;
        }

        if (ALLOW_ALL_HOSTNAME_VERIFIER.equals(containingClass.getQualifiedName())) {
            reportIssue(context, node);
            return;
        }

        if (containingClass instanceof PsiAnonymousClass) {
            PsiAnonymousClass anonymousClass = (PsiAnonymousClass) containingClass;
            PsiClassType baseClassType = anonymousClass.getBaseClassType();
            if (baseClassType != null
                    && HOSTNAME_VERIFIER.equals(baseClassType.getCanonicalText())) {
                if (verifyAlwaysReturnsTrue(anonymousClass, context)) {
                    reportIssue(context, node);
                }
            }
        }
    }

    @Override
    public List<String> getApplicableMethodNames() {
        return Arrays.asList(SET_HOSTNAME_VERIFIER, SET_DEFAULT_HOSTNAME_VERIFIER);
    }

    @Override
    public void visitMethodCall(
            @NonNull JavaContext context,
            @NonNull UCallExpression node,
            @NonNull PsiMethod method) {
        String name = method.getName();
        if (!SET_HOSTNAME_VERIFIER.equals(name) && !SET_DEFAULT_HOSTNAME_VERIFIER.equals(name)) {
            return;
        }

        List<UExpression> args = node.getValueArguments();
        if (args.isEmpty()) {
            return;
        }

        if (isInsecureHostnameVerifierReference(args.get(0))) {
            reportIssue(context, node);
        }
    }

    private static boolean verifyAlwaysReturnsTrue(
            @NonNull PsiAnonymousClass anonymousClass, @NonNull JavaContext context) {
        for (PsiMethod method : anonymousClass.getMethods()) {
            if (!"verify".equals(method.getName())) {
                continue;
            }
            UMethod uMethod = context.getUastContext().getMethod(method);
            if (uMethod == null) {
                continue;
            }
            if (methodAlwaysReturnsTrue(uMethod)) {
                return true;
            }
        }
        return false;
    }

    private static boolean methodAlwaysReturnsTrue(@NonNull UMethod method) {
        final boolean[] foundReturn = {false};
        final boolean[] allTrue = {true};
        method.accept(
                new AbstractUastVisitor() {
                    @Override
                    public boolean visitReturnExpression(@NonNull UReturnExpression node) {
                        foundReturn[0] = true;
                        UExpression value = node.getReturnExpression();
                        if (value == null || !isTrueLiteral(value)) {
                            allTrue[0] = false;
                        }
                        return super.visitReturnExpression(node);
                    }
                });
        return foundReturn[0] && allTrue[0];
    }

    private static boolean isTrueLiteral(@Nullable UExpression expression) {
        return expression instanceof ULiteralExpression
                && Boolean.TRUE.equals(((ULiteralExpression) expression).getValue());
    }

    private static boolean isInsecureHostnameVerifierReference(@NonNull UExpression expression) {
        PsiElement javaPsi = expression.getJavaPsi();
        if (!(javaPsi instanceof PsiReference)) {
            return false;
        }
        PsiElement resolved = ((PsiReference) javaPsi).resolve();
        if (resolved instanceof PsiField) {
            PsiField field = (PsiField) resolved;
            PsiClass containingClass = field.getContainingClass();
            if (containingClass != null) {
                String qualifiedName = containingClass.getQualifiedName() + "." + field.getName();
                if (ALLOW_ALL_HOSTNAME_VERIFIER_FIELD.equals(qualifiedName)) {
                    return true;
                }
            }
            PsiType type = field.getType();
            if (type != null && ALLOW_ALL_HOSTNAME_VERIFIER.equals(type.getCanonicalText())) {
                return true;
            }
        } else if (resolved instanceof PsiVariable) {
            PsiVariable variable = (PsiVariable) resolved;
            PsiType type = variable.getType();
            if (type != null && ALLOW_ALL_HOSTNAME_VERIFIER.equals(type.getCanonicalText())) {
                return true;
            }
        }
        return false;
    }

    private static void reportIssue(@NonNull JavaContext context, @NonNull UElement node) {
        context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Using a `HostnameVerifier` that accepts every certificate is unsafe");
    }
}