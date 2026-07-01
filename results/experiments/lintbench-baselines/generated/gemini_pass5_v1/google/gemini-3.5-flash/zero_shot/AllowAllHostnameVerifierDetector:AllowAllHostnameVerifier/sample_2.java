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
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NonNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.UBlockExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.ULambdaExpression;
import org.jetbrains.uast.ULiteralExpression;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UReturnExpression;
import org.jetbrains.uast.visitor.AbstractUastVisitor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AllowAllHostnameVerifierDetector extends Detector implements SourceCodeScanner {

    public static final Issue ISSUE = Issue.create(
            "AllowAllHostnameVerifier",
            "Insecure HostnameVerifier",
            "This check looks for use of HostnameVerifier implementations " +
                    "whose `verify` method always returns true (thus trusting any hostname) " +
                    "which could result in insecure network traffic caused by trusting arbitrary " +
                    "hostnames in TLS/SSL certificates presented by peers.",
            Category.SECURITY,
            6,
            Severity.WARNING,
            new Implementation(
                    AllowAllHostnameVerifierDetector.class,
                    Scope.JAVA_FILE_SCOPE
            )
    );

    @Nullable
    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        List<Class<? extends UElement>> types = new ArrayList<>();
        types.add(UClass.class);
        types.add(ULambdaExpression.class);
        return types;
    }

    @Nullable
    @Override
    public UElementHandler createUastHandler(@NonNull JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitClass(@NonNull UClass node) {
                if (node.isInterface() || context.getEvaluator().isAbstract(node)) {
                    return;
                }
                if (!context.getEvaluator().inheritsFrom(node, "javax.net.ssl.HostnameVerifier", false)) {
                    return;
                }

                for (UMethod method : node.getMethods()) {
                    if ("verify".equals(method.getName()) && method.getUastParameters().size() == 2) {
                        UExpression body = method.getUastBody();
                        if (body != null && alwaysReturnsTrue(body)) {
                            context.report(
                                    ISSUE,
                                    node,
                                    context.getNameLocation(method),
                                    "Trusting all hostnames can lead to insecure network traffic"
                            );
                        }
                    }
                }
            }

            @Override
            public void visitLambdaExpression(@NonNull ULambdaExpression node) {
                PsiType type = node.getFunctionalInterfaceType();
                if (type != null && "javax.net.ssl.HostnameVerifier".equals(type.getCanonicalText())) {
                    UExpression body = node.getBody();
                    if (alwaysReturnsTrue(body)) {
                        context.report(
                                ISSUE,
                                node,
                                context.getLocation(node),
                                "Trusting all hostnames can lead to insecure network traffic"
                        );
                    }
                }
            }
        };
    }

    private static boolean alwaysReturnsTrue(@NonNull UExpression body) {
        if (body instanceof ULiteralExpression) {
            return Boolean.TRUE.equals(((ULiteralExpression) body).getValue());
        }

        List<UReturnExpression> returns = new ArrayList<>();
        body.accept(new AbstractUastVisitor() {
            @Override
            public boolean visitReturnExpression(@NonNull UReturnExpression node) {
                returns.add(node);
                return super.visitReturnExpression(node);
            }
        });

        if (returns.isEmpty()) {
            if (body instanceof UBlockExpression) {
                List<UExpression> expressions = ((UBlockExpression) body).getExpressions();
                if (expressions.size() == 1) {
                    return alwaysReturnsTrue(expressions.get(0));
                }
            }
            return false;
        }

        for (UReturnExpression ret : returns) {
            UExpression returnExpression = ret.getReturnExpression();
            if (returnExpression == null || !isTrueLiteral(returnExpression)) {
                return false;
            }
        }

        return true;
    }

    private static boolean isTrueLiteral(@NonNull UExpression expression) {
        if (expression instanceof ULiteralExpression) {
            return Boolean.TRUE.equals(((ULiteralExpression) expression).getValue());
        }
        return false;
    }
}