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
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiNewExpression;
import com.intellij.psi.PsiReturnStatement;
import com.intellij.psi.PsiStatement;
import java.util.Arrays;
import java.util.List;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.UReferenceExpression;

public class AllowAllHostnameVerifierDetector extends Detector implements SourceCodeScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(AllowAllHostnameVerifierDetector.class, Scope.JAVA_FILE_SCOPE);

    private static final String HOSTNAME_VERIFIER = "javax.net.ssl.HostnameVerifier";
    private static final String ALLOW_ALL_HOSTNAME_VERIFIER =
            "org.apache.http.conn.ssl.AllowAllHostnameVerifier";
    private static final String ABSTRACT_VERIFIER = "org.apache.http.conn.ssl.AbstractVerifier";
    private static final String SSL_SOCKET_FACTORY = "org.apache.http.conn.ssl.SSLSocketFactory";
    private static final String HTTPS_URL_CONNECTION = "javax.net.ssl.HttpsURLConnection";

    public static final Issue ISSUE =
            Issue.create(
                            "AllowAllHostnameVerifier",
                            "Insecure HostnameVerifier",
                            "This check looks for use of HostnameVerifier implementations whose "
                                    + "`verify` method always returns true (thus trusting any hostname) "
                                    + "which could result in insecure network traffic caused by "
                                    + "trusting arbitrary hostnames in TLS/SSL certificates "
                                    + "presented by peers.",
                            Category.SECURITY,
                            6,
                            Severity.WARNING,
                            IMPLEMENTATION)
                    .setAndroidSpecific(true);

    public AllowAllHostnameVerifierDetector() {}

    @Nullable
    @Override
    public List<String> applicableSuperClasses() {
        return Arrays.asList(HOSTNAME_VERIFIER, ABSTRACT_VERIFIER);
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        String qualifiedName = declaration.getQualifiedName();
        if (HOSTNAME_VERIFIER.equals(qualifiedName) || ABSTRACT_VERIFIER.equals(qualifiedName)) {
            return;
        }
        if (hasAlwaysTrueVerify(declaration)) {
            context.report(
                    ISSUE,
                    declaration,
                    context.getNameLocation(declaration),
                    "Implementing a HostnameVerifier whose `verify` always returns true is "
                            + "insecure; it accepts any hostname in TLS/SSL certificates. Use a "
                            + "strict HostnameVerifier instead.");
        }
    }

    @Nullable
    @Override
    public List<String> applicableConstructorTypes() {
        return Arrays.asList(HOSTNAME_VERIFIER, ALLOW_ALL_HOSTNAME_VERIFIER);
    }

    @Override
    public void visitConstructor(
            @NonNull JavaContext context,
            @NonNull UCallExpression node,
            @NonNull PsiMethod constructor) {
        UReferenceExpression classRef = node.getClassReference();
        if (classRef == null) {
            return;
        }
        PsiElement resolved = classRef.resolve();
        if (!(resolved instanceof PsiClass)) {
            return;
        }
        String qualifiedName = ((PsiClass) resolved).getQualifiedName();
        if (ALLOW_ALL_HOSTNAME_VERIFIER.equals(qualifiedName)) {
            context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    "`AllowAllHostnameVerifier` accepts every hostname and is insecure. Use a "
                            + "strict HostnameVerifier instead.");
            return;
        }
        if (HOSTNAME_VERIFIER.equals(qualifiedName)) {
            PsiElement sourcePsi = node.getSourcePsi();
            if (sourcePsi instanceof PsiNewExpression) {
                PsiAnonymousClass anonymousClass =
                        ((PsiNewExpression) sourcePsi).getAnonymousClass();
                if (anonymousClass != null && hasAlwaysTrueVerify(anonymousClass)) {
                    context.report(
                            ISSUE,
                            node,
                            context.getLocation(node),
                            "This HostnameVerifier accepts every hostname and is insecure. Use a "
                                    + "strict HostnameVerifier instead.");
                }
            }
        }
    }

    @Nullable
    @Override
    public List<String> getApplicableMethodNames() {
        return Arrays.asList("setDefaultHostnameVerifier", "setHostnameVerifier");
    }

    @Override
    public void visitMethodCall(
            @NonNull JavaContext context, @NonNull UCallExpression node, @NonNull PsiMethod method) {
        PsiClass containingClass = method.getContainingClass();
        if (containingClass == null) {
            return;
        }
        String containingName = containingClass.getQualifiedName();
        if (!HTTPS_URL_CONNECTION.equals(containingName) && !SSL_SOCKET_FACTORY.equals(containingName)) {
            return;
        }

        for (UExpression argument : node.getValueArguments()) {
            // Constructor calls are handled by visitConstructor.
            if (argument instanceof UCallExpression) {
                continue;
            }
            if (argument instanceof UReferenceExpression) {
                PsiElement resolved = ((UReferenceExpression) argument).resolve();
                if (resolved instanceof PsiField) {
                    PsiField field = (PsiField) resolved;
                    PsiClass fieldClass = field.getContainingClass();
                    if (fieldClass != null
                            && SSL_SOCKET_FACTORY.equals(fieldClass.getQualifiedName())
                            && "ALLOW_ALL_HOSTNAME_VERIFIER".equals(field.getName())) {
                        context.report(
                                ISSUE,
                                node,
                                context.getLocation(node),
                                "Setting `ALLOW_ALL_HOSTNAME_VERIFIER` is insecure; it accepts "
                                        + "every hostname. Use a strict HostnameVerifier instead.");
                    }
                }
            }
        }
    }

    private static boolean hasAlwaysTrueVerify(@NonNull UClass cls) {
        for (PsiMethod method : cls.getMethods()) {
            if ("verify".equals(method.getName()) && isAlwaysTrue(method)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasAlwaysTrueVerify(@NonNull PsiAnonymousClass cls) {
        for (PsiMethod method : cls.getMethods()) {
            if ("verify".equals(method.getName()) && isAlwaysTrue(method)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isAlwaysTrue(@NonNull PsiMethod method) {
        PsiCodeBlock body = method.getBody();
        if (body == null) {
            return false;
        }
        PsiStatement[] statements = body.getStatements();
        if (statements.length != 1) {
            return false;
        }
        PsiStatement statement = statements[0];
        if (!(statement instanceof PsiReturnStatement)) {
            return false;
        }
        PsiExpression returnValue = ((PsiReturnStatement) statement).getReturnValue();
        return returnValue != null && "true".equals(returnValue.getText());
    }
}