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
import com.intellij.psi.*;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.jetbrains.uast.*;

public class AllowAllHostnameVerifierDetector extends Detector implements SourceCodeScanner {

    private static final String HOSTNAME_VERIFIER = "javax.net.ssl.HostnameVerifier";
    private static final String APACHE_ALLOW_ALL = "org.apache.http.conn.ssl.AllowAllHostnameVerifier";
    private static final String APACHE_SSL_SOCKET_FACTORY = "org.apache.http.conn.ssl.SSLSocketFactory";
    private static final String ALLOW_ALL_FIELD = "ALLOW_ALL_HOSTNAME_VERIFIER";
    private static final String MESSAGE =
            "Using an insecure `HostnameVerifier` that trusts all hostnames can allow man-in-the-middle attacks.";

    private static final Implementation IMPLEMENTATION =
            new Implementation(AllowAllHostnameVerifierDetector.class, Scope.JAVA_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "AllowAllHostnameVerifier",
                    "Insecure `HostnameVerifier`",
                    "A `HostnameVerifier` whose `verify` method always returns true trusts any hostname in a TLS/SSL certificate, which makes the app vulnerable to man-in-the-middle attacks. Use the system's default `HostnameVerifier` instead.",
                    Category.SECURITY,
                    6,
                    Severity.WARNING,
                    IMPLEMENTATION);

    @Override
    public List<String> getApplicableConstructorTypes() {
        return Arrays.asList(HOSTNAME_VERIFIER, APACHE_ALLOW_ALL);
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

        String qualifiedName = containingClass.getQualifiedName();
        if (APACHE_ALLOW_ALL.equals(qualifiedName)) {
            report(context, node);
            return;
        }

        if (containingClass instanceof PsiAnonymousClass) {
            PsiClassType baseClassType = ((PsiAnonymousClass) containingClass).getBaseClassType();
            PsiClass baseClass = baseClassType.resolve();
            if (baseClass != null
                    && HOSTNAME_VERIFIER.equals(baseClass.getQualifiedName())
                    && hasVerifyAlwaysTrue(containingClass)) {
                report(context, node);
            }
        }
    }

    @Override
    public List<String> getApplicableMethodNames() {
        return Collections.emptyList();
    }

    @Override
    public void visitMethodCall(
            @NonNull JavaContext context,
            @NonNull UCallExpression node,
            @NonNull PsiMethod method) {
    }

    @Override
    public List<String> getApplicableReferenceNames() {
        return Collections.singletonList(ALLOW_ALL_FIELD);
    }

    @Override
    public void visitReference(
            @NonNull JavaContext context,
            @NonNull UReferenceExpression node,
            @NonNull PsiElement referenced) {
        if (isAllowAllHostnameVerifierField(referenced)) {
            report(context, node);
        }
    }

    private static boolean hasVerifyAlwaysTrue(@NonNull PsiClass cls) {
        for (PsiMethod method : cls.getMethods()) {
            if (!"verify".equals(method.getName())) {
                continue;
            }
            if (method.getParameterList().getParametersCount() != 2) {
                continue;
            }
            PsiType returnType = method.getReturnType();
            if (!PsiType.BOOLEAN.equals(returnType)) {
                continue;
            }
            PsiCodeBlock body = method.getBody();
            if (body == null) {
                continue;
            }
            PsiStatement[] statements = body.getStatements();
            if (statements.length != 1) {
                continue;
            }
            PsiStatement statement = statements[0];
            if (!(statement instanceof PsiReturnStatement)) {
                continue;
            }
            PsiExpression value = ((PsiReturnStatement) statement).getReturnValue();
            if (value instanceof PsiLiteralExpression
                    && Boolean.TRUE.equals(((PsiLiteralExpression) value).getValue())) {
                return true;
            }
        }
        return false;
    }

    private static boolean isAllowAllHostnameVerifierField(@NonNull PsiElement element) {
        if (!(element instanceof PsiField)) {
            return false;
        }
        PsiField field = (PsiField) element;
        PsiClass containingClass = field.getContainingClass();
        return ALLOW_ALL_FIELD.equals(field.getName())
                && containingClass != null
                && APACHE_SSL_SOCKET_FACTORY.equals(containingClass.getQualifiedName());
    }

    private static void report(@NonNull JavaContext context, @NonNull UElement node) {
        context.report(ISSUE, node, context.getLocation(node), MESSAGE);
    }
}