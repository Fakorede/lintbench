package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.UElementHandler;
import org.jetbrains.annotations.NonNull;
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
            "This check looks for use of HostnameVerifier implementations whose `verify` method always returns true (thus trusting any hostname) which could result in insecure network traffic caused by trusting arbitrary hostnames in TLS/SSL certificates presented by peers.",
            Category.SECURITY,
            8,
            Severity.WARNING,
            new Implementation(AllowAllHostnameVerifierDetector.class, Scope.JAVA_FILE_SCOPE)
    );

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.singletonList(UClass.class);
    }

    @Override
    public UElementHandler createUastHandler(@NonNull JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitClass(@NonNull UClass node) {
                if (context.getEvaluator().implementsInterface(node, "javax.net.ssl.HostnameVerifier", true)) {
                    for (UMethod method : node.getMethods()) {
                        if ("verify".equals(method.getName())) {
                            if (alwaysReturnsTrue(method)) {
                                context.report(ISSUE, method, context.getLocation(method),
                                        "HostnameVerifier always returns true, which trusts all hostnames and disables SSL hostname verification.");
                            }
                        }
                    }
                }
            }
        };
    }

    private static boolean alwaysReturnsTrue(UMethod method) {
        UExpression body = method.getUastBody();
        if (body == null) {
            return false;
        }

        // Handle expression-bodied methods or lambdas directly returning a value
        if (body instanceof ULiteralExpression) {
            return Boolean.TRUE.equals(((ULiteralExpression) body).getValue());
        }

        List<UReturnExpression> returns = new ArrayList<>();
        body.accept(new AbstractUastVisitor() {
            @Override
            public boolean visitClass(UClass node) {
                return true; // skip nested classes
            }

            @Override
            public boolean visitLambdaExpression(ULambdaExpression node) {
                return true; // skip nested lambdas
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
            if (!(val instanceof ULiteralExpression) || !Boolean.TRUE.equals(((ULiteralExpression) val).getValue())) {
                return false;
            }
        }
        return true;
    }
}