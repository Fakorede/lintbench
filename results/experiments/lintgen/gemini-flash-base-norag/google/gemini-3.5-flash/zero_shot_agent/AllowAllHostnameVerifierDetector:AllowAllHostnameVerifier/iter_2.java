package com.android.tools.lint.checks;

import com.android.tools.lint.client.api.UElementHandler;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiType;
import java.util.Arrays;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.UBlockExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.ULambdaExpression;
import org.jetbrains.uast.ULiteralExpression;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UParenthesizedExpression;
import org.jetbrains.uast.UReturnExpression;
import org.jetbrains.uast.visitor.AbstractUastVisitor;

public class AllowAllHostnameVerifierDetector extends Detector implements Detector.UastScanner {

    public static final Issue ISSUE = Issue.create(
            "AllowAllHostnameVerifier",
            "Insecure HostnameVerifier",
            "This check looks for use of HostnameVerifier implementations whose `verify` " +
            "method always returns true (thus trusting any hostname) which could result " +
            "in insecure network traffic caused by trusting arbitrary hostnames in TLS/SSL " +
            "certificates presented by peers.",
            Category.SECURITY,
            6,
            Severity.WARNING,
            new Implementation(
                    AllowAllHostnameVerifierDetector.class,
                    Scope.JAVA_FILE_SCOPE
            )
    ).addMoreInfo("https://goo.gle/AllowAllHostnameVerifier");

    @Nullable
    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Arrays.asList(UClass.class, ULambdaExpression.class);
    }

    @Nullable
    @Override
    public UElementHandler createUastHandler(@NotNull JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitClass(@NotNull UClass node) {
                if (node.isInterface()) {
                    return;
                }

                if (isHostnameVerifier(node, context)) {
                    for (UMethod method : node.getMethods()) {
                        if ("verify".equals(method.getName()) && method.getUastParameters().size() == 2) {
                            if (alwaysReturnsTrue(method.getUastBody())) {
                                context.report(
                                        ISSUE,
                                        method,
                                        context.getNameLocation(method),
                                        "Trusting all hostnames can lead to insecure network connections"
                                );
                            }
                        }
                    }
                }
            }

            @Override
            public void visitLambdaExpression(@NotNull ULambdaExpression node) {
                PsiType type = node.getFunctionalInterfaceType();
                boolean isHostnameVerifier = false;
                if (type != null) {
                    String text = type.getCanonicalText();
                    if (text.contains("HostnameVerifier")) {
                        isHostnameVerifier = true;
                    }
                }
                if (isHostnameVerifier) {
                    UExpression body = node.getBody();
                    if (alwaysReturnsTrue(body)) {
                        context.report(
                                ISSUE,
                                node,
                                context.getLocation(node),
                                "Trusting all hostnames can lead to insecure network connections"
                        );
                    }
                }
            }
        };
    }

    private static boolean isHostnameVerifier(@NotNull UClass node, @NotNull JavaContext context) {
        if (context.getEvaluator().inheritsFrom(node, "javax.net.ssl.HostnameVerifier", false)) {
            return true;
        }
        for (PsiClassType type : node.getImplementsListTypes()) {
            String text = type.getCanonicalText();
            if (text.contains("HostnameVerifier")) {
                return true;
            }
        }
        for (PsiClassType type : node.getExtendsListTypes()) {
            String text = type.getCanonicalText();
            if (text.contains("HostnameVerifier")) {
                return true;
            }
        }
        for (PsiType type : node.getSuperTypes()) {
            String text = type.getCanonicalText();
            if (text.contains("HostnameVerifier")) {
                return true;
            }
        }
        for (PsiClass iface : node.getInterfaces()) {
            String name = iface.getQualifiedName();
            if (name != null && name.contains("HostnameVerifier")) {
                return true;
            }
            String interfaceName = iface.getName();
            if (interfaceName != null && interfaceName.contains("HostnameVerifier")) {
                return true;
            }
        }
        String className = node.getName();
        if (className != null && className.contains("HostnameVerifier")) {
            return true;
        }
        return false;
    }

    private static boolean alwaysReturnsTrue(@Nullable UExpression body) {
        if (body == null) {
            return false;
        }

        UExpression singleExpression = skipParentheses(body);
        if (singleExpression instanceof ULiteralExpression) {
            return Boolean.TRUE.equals(((ULiteralExpression) singleExpression).getValue());
        }
        if (singleExpression instanceof UReturnExpression) {
            UExpression returnVal = ((UReturnExpression) singleExpression).getReturnExpression();
            return returnVal != null && isTrueLiteral(returnVal);
        }
        if (singleExpression instanceof UBlockExpression) {
            List<UExpression> expressions = ((UBlockExpression) singleExpression).getExpressions();
            if (expressions.size() == 1) {
                return alwaysReturnsTrue(expressions.get(0));
            }
        }

        ReturnVisitor visitor = new ReturnVisitor();
        body.accept(visitor);
        if (visitor.hasReturns()) {
            return visitor.alwaysReturnsTrue();
        }

        if (singleExpression instanceof UBlockExpression) {
            List<UExpression> expressions = ((UBlockExpression) singleExpression).getExpressions();
            if (!expressions.isEmpty()) {
                UExpression last = expressions.get(expressions.size() - 1);
                return alwaysReturnsTrue(last);
            }
        }

        return false;
    }

    private static UExpression skipParentheses(UExpression expression) {
        while (expression instanceof UParenthesizedExpression) {
            expression = ((UParenthesizedExpression) expression).getExpression();
        }
        return expression;
    }

    private static boolean isTrueLiteral(UExpression expression) {
        if (expression == null) {
            return false;
        }
        UExpression clean = skipParentheses(expression);
        if (clean instanceof ULiteralExpression) {
            Object value = ((ULiteralExpression) clean).getValue();
            return Boolean.TRUE.equals(value);
        }
        return false;
    }

    private static class ReturnVisitor extends AbstractUastVisitor {
        private int returnCount = 0;
        private int trueReturnCount = 0;

        public boolean hasReturns() {
            return returnCount > 0;
        }

        public boolean alwaysReturnsTrue() {
            return returnCount == trueReturnCount;
        }

        @Override
        public boolean visitReturnExpression(@NotNull UReturnExpression node) {
            returnCount++;
            UExpression returnExpression = node.getReturnExpression();
            if (returnExpression != null && isTrueLiteral(returnExpression)) {
                trueReturnCount++;
            }
            return super.visitReturnExpression(node);
        }

        @Override
        public boolean visitLambdaExpression(@NotNull ULambdaExpression node) {
            return true; // Skip nested lambdas
        }

        @Override
        public boolean visitClass(@NotNull UClass node) {
            return true; // Skip nested classes
        }
    }
}