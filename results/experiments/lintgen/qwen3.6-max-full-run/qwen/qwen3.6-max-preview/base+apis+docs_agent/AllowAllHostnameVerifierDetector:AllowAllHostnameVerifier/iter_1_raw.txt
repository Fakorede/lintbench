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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class AllowAllHostnameVerifierDetector extends Detector implements SourceCodeScanner {

    public static final Issue ISSUE = Issue.create(
            "AllowAllHostnameVerifier",
            "Insecure HostnameVerifier",
            "This check looks for use of HostnameVerifier implementations whose `verify` method always returns true (thus trusting any hostname) which could result in insecure network traffic caused by trusting arbitrary hostnames in TLS/SSL certificates presented by peers.",
            Category.SECURITY,
            8,
            Severity.WARNING,
            new Implementation(AllowAllHostnameVerifierDetector.class, Scope.JAVA_FILE_SCOPE)
    );

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Arrays.asList(UClass.class, ULambdaExpression.class);
    }

    @Override
    public UElementHandler createUastHandler(JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitClass(UClass node) {
                if (context.getEvaluator().implementsInterface(node, "javax.net.ssl.HostnameVerifier", true)) {
                    for (UMethod method : node.getMethods()) {
                        if ("verify".equals(method.getName())) {
                            if (alwaysReturnsTrue(method.getUastBody())) {
                                context.report(ISSUE, method, context.getLocation(method),
                                        "HostnameVerifier always returns true, which trusts all hostnames and disables SSL hostname verification.");
                            }
                        }
                    }
                }
            }

            @Override
            public void visitLambdaExpression(ULambdaExpression node) {
                if (node.getValueParameters().size() == 2 && alwaysReturnsTrue(node.getBody())) {
                    context.report(ISSUE, node, context.getLocation(node),
                            "HostnameVerifier always returns true, which trusts all hostnames and disables SSL hostname verification.");
                }
            }
        };
    }

    private static boolean alwaysReturnsTrue(UElement body) {
        if (body == null) {
            return false;
        }

        UElement current = body;
        while (current instanceof UParenthesizedExpression) {
            current = ((UParenthesizedExpression) current).getExpression();
        }

        if (current instanceof ULiteralExpression) {
            return Boolean.TRUE.equals(((ULiteralExpression) current).getValue());
        }

        if (current instanceof UBlockExpression) {
            final List<UReturnExpression> returns = new ArrayList<>();
            current.accept(new AbstractUastVisitor() {
                @Override
                public boolean visitClass(UClass node) {
                    return true;
                }
                @Override
                public boolean visitLambdaExpression(ULambdaExpression node) {
                    return true;
                }
                @Override
                public boolean visitReturnExpression(UReturnExpression node) {
                    returns.add(node);
                    return true;
                }
            });

            if (returns.isEmpty()) {
                return false;
            }

            for (UReturnExpression ret : returns) {
                UExpression val = ret.getReturnExpression();
                if (val == null) {
                    return false;
                }
                while (val instanceof UParenthesizedExpression) {
                    val = ((UParenthesizedExpression) val).getExpression();
                }
                if (!(val instanceof ULiteralExpression) || !Boolean.TRUE.equals(((ULiteralExpression) val).getValue())) {
                    return false;
                }
            }
            return true;
        }

        return false;
    }
}