package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiRecursiveElementWalkingVisitor;
import com.intellij.psi.PsiReturnStatement;
import com.intellij.psi.PsiType;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UReturnExpression;
import org.jetbrains.uast.UThrowExpression;

public class BadHostnameVerifierDetector extends Detector implements SourceCodeScanner {

    private static final String HOSTNAME_VERIFIER = "javax.net.ssl.HostnameVerifier";

    private static final Implementation IMPLEMENTATION =
            new Implementation(BadHostnameVerifierDetector.class, Scope.JAVA_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                            "BadHostnameVerifier",
                            "Insecure HostnameVerifier",
                            "This check looks for implementations of HostnameVerifier whose "
                                    + "verify() method always returns true (thus trusting any "
                                    + "hostname), which can result in insecure network traffic "
                                    + "caused by trusting arbitrary hostnames in TLS/SSL "
                                    + "certificates presented by peers.",
                            Category.SECURITY,
                            6,
                            Severity.WARNING,
                            IMPLEMENTATION);

    public BadHostnameVerifierDetector() {}

    @Override
    public java.util.List<String> applicableSuperClasses() {
        return java.util.Collections.singletonList(HOSTNAME_VERIFIER);
    }

    @Override
    public void visitClass(JavaContext context, UClass declaration) {
        if (HOSTNAME_VERIFIER.equals(declaration.getQualifiedName())) {
            return;
        }

        PsiClass psiClass = declaration.getJavaPsi();
        if (psiClass == null
                || !context.getEvaluator().extendsClass(psiClass, HOSTNAME_VERIFIER, false)) {
            return;
        }

        for (PsiMethod method : declaration.getMethods()) {
            if (!isVerifyMethod(method)) {
                continue;
            }
            PsiReturnStatement badReturn = findReturnTrue(method);
            if (badReturn != null) {
                context.report(
                        ISSUE,
                        badReturn,
                        context.getLocation(badReturn),
                        "Insecure HostnameVerifier: verify() always returns true, trusting any "
                                + "hostname. This can allow insecure network traffic caused by "
                                + "trusting arbitrary hostnames in TLS/SSL certificates.");
            }
        }
    }

    @Override
    public void visitCallExpression(JavaContext context, UCallExpression expression) {}

    @Override
    public void visitThrowExpression(JavaContext context, UThrowExpression expression) {}

    @Override
    public void visitReturnExpression(JavaContext context, UReturnExpression expression) {}

    private static boolean isVerifyMethod(PsiMethod method) {
        if (method == null || !"verify".equals(method.getName())) {
            return false;
        }
        PsiParameter[] parameters = method.getParameterList().getParameters();
        if (parameters.length != 2) {
            return false;
        }
        if (method.getReturnType() == null || !method.getReturnType().equals(PsiType.BOOLEAN)) {
            return false;
        }
        return parameters[0].getType().equalsToText("java.lang.String")
                && parameters[1].getType().equalsToText("javax.net.ssl.SSLSession");
    }

    private static PsiReturnStatement findReturnTrue(PsiMethod method) {
        PsiCodeBlock body = method.getBody();
        if (body == null) {
            return null;
        }
        final PsiReturnStatement[] result = new PsiReturnStatement[1];
        body.accept(
                new PsiRecursiveElementWalkingVisitor() {
                    @Override
                    public void visitElement(PsiElement element) {
                        if (element instanceof PsiReturnStatement) {
                            PsiExpression value =
                                    ((PsiReturnStatement) element).getReturnValue();
                            if (isTrue(value)) {
                                result[0] = (PsiReturnStatement) element;
                                stopWalking();
                                return;
                            }
                        }
                        super.visitElement(element);
                    }
                });
        return result[0];
    }

    private static boolean isTrue(PsiExpression expression) {
        return expression instanceof PsiLiteralExpression
                && Boolean.TRUE.equals(((PsiLiteralExpression) expression).getValue());
    }
}