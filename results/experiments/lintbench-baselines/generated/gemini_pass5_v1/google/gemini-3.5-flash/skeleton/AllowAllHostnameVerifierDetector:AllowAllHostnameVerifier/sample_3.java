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
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiLocalVariable;
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
import org.jetbrains.uast.UObjectLiteralExpression;
import org.jetbrains.uast.UReferenceExpression;
import org.jetbrains.uast.UReturnExpression;
import org.jetbrains.uast.UVariable;
import org.jetbrains.uast.UastCallKind;
import org.jetbrains.uast.visitor.AbstractUastVisitor;

public class AllowAllHostnameVerifierDetector extends Detector implements SourceCodeScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(AllowAllHostnameVerifierDetector.class, Scope.JAVA_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "AllowAllHostnameVerifier",
                    "Insecure `HostnameVerifier`",
                    "This check looks for use of HostnameVerifier implementations whose `verify` method always returns true (thus trusting any hostname) which could result in insecure network traffic caused by trusting arbitrary hostnames in TLS/SSL certificates presented by peers.",
                    Category.SECURITY,
                    6,
                    Severity.WARNING,
                    IMPLEMENTATION);

    @Override
    public List<String> getApplicableConstructorTypes() {
        return Collections.singletonList("org.apache.http.conn.ssl.AllowAllHostnameVerifier");
    }

    @Override
    public void visitConstructor(
            JavaContext context,
            UCallExpression node,
            PsiMethod constructor) {
        context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Using the `AllowAllHostnameVerifier` class is insecure because it always "
                        + "returns true, which trusts any hostname when establishing a TLS/SSL connection.");
    }

    @Override
    public List<String> getApplicableMethodNames() {
        return Arrays.asList("setHostnameVerifier", "setDefaultHostnameVerifier");
    }

    @Override
    public void visitMethodCall(
            JavaContext context,
            UCallExpression node,
            PsiMethod method) {
        List<UExpression> args = node.getValueArguments();
        if (args.isEmpty()) {
            return;
        }
        UExpression arg = args.get(0);
        UExpression expression = arg;

        if (expression instanceof UReferenceExpression) {
            PsiElement resolved = ((UReferenceExpression) expression).resolve();
            if (resolved instanceof PsiLocalVariable) {
                UVariable uVar = (UVariable) context.getUastContext().getDeclarationOf((PsiLocalVariable) resolved);
                if (uVar != null) {
                    UExpression initializer = uVar.getUastInitializer();
                    if (initializer != null) {
                        expression = initializer;
                    }
                }
            }
        }

        if (isAllowAllHostnameVerifierField(expression)) {
            context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    "Using `SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER` is insecure because it "
                            + "always returns true, which trusts any hostname when establishing a TLS/SSL connection.");
            return;
        }

        if (expression instanceof UObjectLiteralExpression) {
            UObjectLiteralExpression objectLiteral = (UObjectLiteralExpression) expression;
            UClass declaration = objectLiteral.getDeclaration();
            if (isUnsafeHostnameVerifier(declaration)) {
                context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        "Using an insecure `HostnameVerifier` that always returns true, which trusts "
                                + "any hostname when establishing a TLS/SSL connection.");
            }
        } else if (expression instanceof ULambdaExpression) {
            ULambdaExpression lambda = (ULambdaExpression) expression;
            if (isUnsafeLambda(lambda)) {
                context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        "Using an insecure `HostnameVerifier` that always returns true, which trusts "
                                + "any hostname when establishing a TLS/SSL connection.");
            }
        } else if (expression instanceof UCallExpression) {
            UCallExpression call = (UCallExpression) expression;
            if (call.getKind() == UastCallKind.CONSTRUCTOR_CALL) {
                PsiMethod constructor = call.resolve();
                if (constructor != null) {
                    PsiClass containingClass = constructor.getContainingClass();
                    if (containingClass != null) {
                        UClass uClass = (UClass) context.getUastContext().getDeclarationOf(containingClass);
                        if (uClass != null && isUnsafeHostnameVerifier(uClass)) {
                            context.report(
                                    ISSUE,
                                    node,
                                    context.getLocation(node),
                                    "Using an insecure `HostnameVerifier` that always returns true, which trusts "
                                            + "any hostname when establishing a TLS/SSL connection.");
                        }
                    }
                }
            }
        }
    }

    private boolean isAllowAllHostnameVerifierField(UExpression expression) {
        if (expression instanceof UReferenceExpression) {
            PsiElement resolved = ((UReferenceExpression) expression).resolve();
            if (resolved instanceof PsiField) {
                PsiField field = (PsiField) resolved;
                if ("ALLOW_ALL_HOSTNAME_VERIFIER".equals(field.getName())) {
                    PsiClass containingClass = field.getContainingClass();
                    if (containingClass != null && "org.apache.http.conn.ssl.SSLSocketFactory".equals(containingClass.getQualifiedName())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean isUnsafeHostnameVerifier(UClass uClass) {
        UMethod verifyMethod = null;
        for (UMethod method : uClass.getMethods()) {
            if ("verify".equals(method.getName()) && method.getUastParameters().size() == 2) {
                verifyMethod = method;
                break;
            }
        }
        if (verifyMethod == null) {
            return false;
        }
        return isAlwaysTrue(verifyMethod.getUastBody());
    }

    private boolean isUnsafeLambda(ULambdaExpression lambda) {
        UExpression body = lambda.getBody();
        return isAlwaysTrue(body);
    }

    private boolean isAlwaysTrue(UExpression body) {
        if (body == null) {
            return false;
        }

        UExpression lastExpression = null;
        if (body instanceof UBlockExpression) {
            List<UExpression> expressions = ((UBlockExpression) body).getExpressions();
            if (!expressions.isEmpty()) {
                lastExpression = expressions.get(expressions.size() - 1);
            }
        }

        final boolean[] hasReturn = {false};
        final boolean[] hasNonTrueReturn = {false};

        body.accept(new AbstractUastVisitor() {
            @Override
            public boolean visitReturnExpression(UReturnExpression node) {
                hasReturn[0] = true;
                UExpression val = node.getReturnExpression();
                if (!isTrueLiteral(val)) {
                    hasNonTrueReturn[0] = true;
                }
                return super.visitReturnExpression(node);
            }
        });

        if (hasNonTrueReturn[0]) {
            return false;
        }

        if (hasReturn[0]) {
            return true;
        }

        if (lastExpression != null) {
            return isAlwaysTrue(lastExpression);
        }

        return isTrueLiteral(body);
    }

    private boolean isTrueLiteral(UExpression expression) {
        if (expression == null) {
            return false;
        }
        if (expression instanceof ULiteralExpression) {
            Object value = ((ULiteralExpression) expression).getValue();
            return Boolean.TRUE.equals(value);
        }
        return false;
    }
}