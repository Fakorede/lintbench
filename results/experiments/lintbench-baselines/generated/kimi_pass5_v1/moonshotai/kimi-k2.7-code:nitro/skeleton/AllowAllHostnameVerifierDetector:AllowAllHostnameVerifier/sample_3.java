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
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiReturnStatement;
import com.intellij.psi.PsiStatement;
import com.intellij.psi.PsiType;
import java.util.Arrays;
import java.util.List;
import org.jetbrains.uast.UBlockExpression;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.ULambdaExpression;
import org.jetbrains.uast.ULiteralExpression;
import org.jetbrains.uast.UObjectLiteralExpression;
import org.jetbrains.uast.UReturnExpression;

public class AllowAllHostnameVerifierDetector extends Detector implements SourceCodeScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(AllowAllHostnameVerifierDetector.class, Scope.JAVA_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "AllowAllHostnameVerifier",
                    "Insecure `HostnameVerifier`",
                    "This check looks for use of `HostnameVerifier` implementations whose `verify` "
                            + "method always returns true (thus trusting any hostname), which could "
                            + "result in insecure network traffic caused by trusting arbitrary "
                            + "hostnames in TLS/SSL certificates presented by peers. Using a "
                            + "`HostnameVerifier` that accepts all hostnames makes your application "
                            + "vulnerable to man-in-the-middle attacks.",
                    Category.SECURITY,
                    6,
                    Severity.WARNING,
                    IMPLEMENTATION);

    @Override
    public List<String> getApplicableConstructorTypes() {
        return Arrays.asList(
                "javax.net.ssl.HostnameVerifier",
                "org.apache.http.conn.ssl.X509HostnameVerifier",
                "org.apache.http.conn.ssl.AllowAllHostnameVerifier");
    }

    @Override
    public void visitConstructor(
            JavaContext context, UCallExpression node, PsiMethod constructor) {
        PsiClass containingClass = constructor.getContainingClass();
        if (containingClass == null) {
            return;
        }

        if ("org.apache.http.conn.ssl.AllowAllHostnameVerifier"
                .equals(containingClass.getQualifiedName())) {
            reportIssue(context, node);
            return;
        }

        if (isAllowAllHostnameVerifierImplementation(containingClass)) {
            reportIssue(context, node);
        }
    }

    @Override
    public List<String> getApplicableMethodNames() {
        return Arrays.asList("setDefaultHostnameVerifier", "setHostnameVerifier");
    }

    @Override
    public void visitMethodCall(
            JavaContext context, UCallExpression node, PsiMethod method) {
        String methodName = method.getName();
        PsiClass containingClass = method.getContainingClass();
        String className = containingClass != null ? containingClass.getQualifiedName() : null;

        if ("setDefaultHostnameVerifier".equals(methodName)) {
            if (!"javax.net.ssl.HttpsURLConnection".equals(className)) {
                return;
            }
        } else if ("setHostnameVerifier".equals(methodName)) {
            if (className == null
                    || (!className.equals("javax.net.ssl.HttpsURLConnection")
                            && !className.equals("org.apache.http.conn.ssl.SSLSocketFactory"))) {
                return;
            }
        } else {
            return;
        }

        List<UExpression> arguments = node.getValueArguments();
        if (arguments.isEmpty()) {
            return;
        }

        UExpression argument = arguments.get(0);
        if (isAllowAllHostnameVerifier(argument)) {
            reportIssue(context, node);
        }
    }

    private static boolean isAllowAllHostnameVerifier(UExpression expression) {
        if (expression instanceof UCallExpression) {
            PsiMethod constructor = ((UCallExpression) expression).resolve();
            if (constructor != null) {
                PsiClass cls = constructor.getContainingClass();
                if (cls != null) {
                    if ("org.apache.http.conn.ssl.AllowAllHostnameVerifier"
                            .equals(cls.getQualifiedName())) {
                        return true;
                    }
                    return isAllowAllHostnameVerifierImplementation(cls);
                }
            }
        }

        if (expression instanceof UObjectLiteralExpression) {
            PsiClass cls = ((UObjectLiteralExpression) expression).getDeclaration().getJavaPsi();
            return isAllowAllHostnameVerifierImplementation(cls);
        }

        if (expression instanceof ULambdaExpression) {
            return returnsTrue(((ULambdaExpression) expression).getBody());
        }

        return false;
    }

    private static boolean isAllowAllHostnameVerifierImplementation(PsiClass psiClass) {
        if (psiClass == null) {
            return false;
        }

        boolean found = false;
        for (PsiMethod method : psiClass.getMethods()) {
            if ("verify".equals(method.getName()) && returnsBoolean(method)) {
                found = true;
                if (!returnsTrue(method)) {
                    return false;
                }
            }
        }

        return found;
    }

    private static boolean returnsBoolean(PsiMethod method) {
        PsiType returnType = method.getReturnType();
        if (returnType == null) {
            return false;
        }
        String canonicalText = returnType.getCanonicalText();
        return "boolean".equals(canonicalText) || "java.lang.Boolean".equals(canonicalText);
    }

    private static boolean returnsTrue(PsiMethod method) {
        PsiCodeBlock body = method.getBody();
        if (body == null) {
            return false;
        }

        PsiStatement[] statements = body.getStatements();
        if (statements.length != 1) {
            return false;
        }

        if (!(statements[0] instanceof PsiReturnStatement)) {
            return false;
        }

        PsiExpression returnValue = ((PsiReturnStatement) statements[0]).getReturnValue();
        if (!(returnValue instanceof PsiLiteralExpression)) {
            return false;
        }

        return Boolean.TRUE.equals(((PsiLiteralExpression) returnValue).getValue());
    }

    private static boolean returnsTrue(UExpression expression) {
        if (expression instanceof ULiteralExpression) {
            return Boolean.TRUE.equals(((ULiteralExpression) expression).getValue());
        }

        if (expression instanceof UReturnExpression) {
            UExpression returnValue = ((UReturnExpression) expression).getReturnExpression();
            return returnValue != null && returnsTrue(returnValue);
        }

        if (expression instanceof UBlockExpression) {
            for (UExpression inner : ((UBlockExpression) expression).getExpressions()) {
                if (returnsTrue(inner)) {
                    return true;
                }
            }
        }

        return false;
    }

    private static void reportIssue(JavaContext context, UElement node) {
        context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Using a `HostnameVerifier` that accepts all hostnames is insecure and allows "
                        + "man-in-the-middle attacks.");
    }
}