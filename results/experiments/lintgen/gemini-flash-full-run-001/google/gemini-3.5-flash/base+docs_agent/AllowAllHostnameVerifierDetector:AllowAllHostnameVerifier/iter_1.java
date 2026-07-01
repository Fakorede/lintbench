package com.android.tools.lint.checks;

import com.android.tools.lint.client.api.UElementHandler;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UReferenceExpression;
import org.jetbrains.uast.UReturnExpression;
import org.jetbrains.uast.USimpleNameReferenceExpression;
import org.jetbrains.uast.UastCallKind;
import org.jetbrains.uast.visitor.AbstractUastVisitor;

public class AllowAllHostnameVerifierDetector extends Detector implements SourceCodeScanner {

    public static final Issue ISSUE = Issue.create(
            "AllowAllHostnameVerifier",
            "Insecure `HostnameVerifier`",
            "This check looks for use of HostnameVerifier implementations whose `verify` " +
            "method always returns true (thus trusting any hostname) which could result in " +
            "insecure network traffic caused by trusting arbitrary hostnames in TLS/SSL " +
            "certificates presented by peers.",
            Category.SECURITY,
            6,
            Severity.WARNING,
            new Implementation(
                    AllowAllHostnameVerifierDetector.class,
                    Scope.JAVA_FILE_SCOPE
            )
    );

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        List<Class<? extends UElement>> types = new ArrayList<>();
        types.add(UClass.class);
        types.add(UCallExpression.class);
        types.add(UReferenceExpression.class);
        return types;
    }

    @Override
    public UElementHandler createUastHandler(JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitClass(UClass node) {
                checkClass(context, node);
            }

            @Override
            public void visitCallExpression(UCallExpression node) {
                checkCallExpression(context, node);
            }

            @Override
            public void visitReferenceExpression(UReferenceExpression node) {
                checkReferenceExpression(context, node);
            }
        };
    }

    private void checkClass(JavaContext context, UClass declaration) {
        if (declaration.isInterface()) {
            return;
        }
        if (!isHostnameVerifier(declaration)) {
            return;
        }

        UMethod[] methods = declaration.getMethods();
        for (UMethod method : methods) {
            if ("verify".equals(method.getName())) {
                if (method.getUastParameters().size() == 2) {
                    checkVerifyMethod(context, method);
                }
            }
        }
    }

    private void checkCallExpression(JavaContext context, UCallExpression node) {
        if (node.getKind() == UastCallKind.CONSTRUCTOR_CALL) {
            String typeName = null;
            PsiMethod resolvedConstructor = node.resolve();
            if (resolvedConstructor != null) {
                PsiClass containingClass = resolvedConstructor.getContainingClass();
                if (containingClass != null) {
                    typeName = containingClass.getQualifiedName();
                }
            }
            if (typeName == null) {
                UReferenceExpression classRef = node.getClassReference();
                if (classRef != null) {
                    typeName = classRef.asSourceString();
                }
            }

            if (typeName != null && (typeName.equals("org.apache.http.conn.ssl.AllowAllHostnameVerifier")
                    || typeName.endsWith("AllowAllHostnameVerifier"))) {
                context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    "Using `AllowAllHostnameVerifier` is insecure because it trusts any hostname in TLS/SSL certificates presented by peers."
                );
            }
        }
    }

    private void checkReferenceExpression(JavaContext context, UReferenceExpression node) {
        String name = null;
        PsiElement resolved = node.resolve();
        if (resolved instanceof PsiField) {
            name = ((PsiField) resolved).getName();
        }
        if (name == null) {
            if (node instanceof USimpleNameReferenceExpression) {
                name = ((USimpleNameReferenceExpression) node).getIdentifier();
            } else {
                name = node.asSourceString();
                if (name.contains(".")) {
                    name = name.substring(name.lastIndexOf('.') + 1);
                }
            }
        }
        if ("ALLOW_ALL_HOSTNAME_VERIFIER".equals(name)) {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Using `ALLOW_ALL_HOSTNAME_VERIFIER` is insecure because it trusts any hostname in TLS/SSL certificates presented by peers."
            );
        }
    }

    private boolean isHostnameVerifier(UClass uClass) {
        for (PsiClassType type : uClass.getSuperTypes()) {
            String canonicalText = type.getCanonicalText();
            if ("javax.net.ssl.HostnameVerifier".equals(canonicalText)) {
                return true;
            }
            PsiClass resolved = type.resolve();
            if (resolved != null && isHostnameVerifierClass(resolved)) {
                return true;
            }
        }
        try {
            for (UExpression superType : uClass.getUastSuperTypes()) {
                String typeText = superType.asSourceString();
                if (typeText.contains("HostnameVerifier")) {
                    return true;
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private boolean isHostnameVerifierClass(PsiClass psiClass) {
        String qName = psiClass.getQualifiedName();
        if ("javax.net.ssl.HostnameVerifier".equals(qName)) {
            return true;
        }
        for (PsiClass superClass : psiClass.getSupers()) {
            if (isHostnameVerifierClass(superClass)) {
                return true;
            }
        }
        return false;
    }

    private void checkVerifyMethod(JavaContext context, UMethod method) {
        UExpression body = method.getUastBody();
        if (body == null) {
            return;
        }

        List<UReturnExpression> returns = new ArrayList<>();
        body.accept(new AbstractUastVisitor() {
            @Override
            public boolean visitClass(UClass node) {
                return true;
            }

            @Override
            public boolean visitLambdaExpression(org.jetbrains.uast.ULambdaExpression node) {
                return true;
            }

            @Override
            public boolean visitReturnExpression(UReturnExpression node) {
                returns.add(node);
                return super.visitReturnExpression(node);
            }
        });

        boolean alwaysReturnsTrue = false;

        if (returns.isEmpty()) {
            if (isBooleanTrue(body)) {
                alwaysReturnsTrue = true;
            }
        } else {
            alwaysReturnsTrue = true;
            for (UReturnExpression ret : returns) {
                UExpression returnVal = ret.getReturnExpression();
                if (returnVal == null || !isBooleanTrue(returnVal)) {
                    alwaysReturnsTrue = false;
                    break;
                }
            }
        }

        if (alwaysReturnsTrue) {
            context.report(
                    ISSUE,
                    method,
                    context.getNameLocation(method),
                    "Using a `HostnameVerifier` that always returns true is insecure because " +
                    "it trusts any hostname in TLS/SSL certificates presented by peers."
            );
        }
    }

    private boolean isBooleanTrue(UExpression expression) {
        if (expression == null) {
            return false;
        }
        Object evaluated = expression.evaluate();
        if (evaluated instanceof Boolean) {
            return (Boolean) evaluated;
        }
        while (expression instanceof org.jetbrains.uast.UParenthesizedExpression) {
            expression = ((org.jetbrains.uast.UParenthesizedExpression) expression).getExpression();
        }
        String text = expression.asSourceString().trim();
        if ("true".equalsIgnoreCase(text)) {
            return true;
        }
        if (expression instanceof org.jetbrains.uast.UQualifiedReferenceExpression) {
            UExpression selector = ((org.jetbrains.uast.UQualifiedReferenceExpression) expression).getSelector();
            String selectorText = selector.asSourceString().trim();
            if ("TRUE".equals(selectorText)) {
                return true;
            }
        }
        return "Boolean.TRUE".equals(text) || "java.lang.Boolean.TRUE".equals(text) || "TRUE".equals(text);
    }
}