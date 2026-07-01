package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UReturnExpression;
import org.jetbrains.uast.visitor.URecursiveElementVisitor;
import java.util.Collections;
import java.util.List;

public class BadHostnameVerifierDetector extends Detector implements SourceCodeScanner {

    public static final Issue ISSUE = Issue.create(
            "BadHostnameVerifier",
            "Insecure HostnameVerifier",
            "This check looks for implementations of `HostnameVerifier` whose `verify` " +
            "method always returns true (thus trusting any hostname) which could result in " +
            "insecure network traffic caused by trusting arbitrary hostnames in TLS/SSL " +
            "certificates presented by peers.",
            Category.SECURITY,
            6,
            Severity.WARNING,
            new Implementation(
                    BadHostnameVerifierDetector.class,
                    Scope.JAVA_FILE_SCOPE
            )
    ).addMoreInfo("https://goo.gle/BadHostnameVerifier");

    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList("javax.net.ssl.HostnameVerifier");
    }

    @Override
    public void visitClass(JavaContext context, UClass declaration) {
        UMethod verifyMethod = null;
        for (UMethod method : declaration.getMethods()) {
            if ("verify".equals(method.getName())) {
                if (method.getUastParameters().size() == 2) {
                    verifyMethod = method;
                    break;
                }
            }
        }
        if (verifyMethod == null) {
            return;
        }

        UExpression body = verifyMethod.getUastBody();
        if (body == null) {
            return;
        }

        ReturnVisitor visitor = new ReturnVisitor();
        body.accept(visitor);

        if (visitor.hasReturns() && visitor.allReturnsTrue()) {
            context.report(
                    ISSUE,
                    verifyMethod,
                    context.getNameLocation(verifyMethod),
                    "Strict HostnameVerifier implementation that always returns true"
            );
        }
    }

    private static class ReturnVisitor extends URecursiveElementVisitor {
        private boolean hasReturns = false;
        private boolean allReturnsTrue = true;

        @Override
        public boolean visitReturnExpression(UReturnExpression node) {
            hasReturns = true;
            UExpression returnExpression = node.getReturnExpression();
            if (returnExpression == null) {
                allReturnsTrue = false;
            } else {
                Object constant = returnExpression.evaluate();
                if (!Boolean.TRUE.equals(constant)) {
                    allReturnsTrue = false;
                }
            }
            return super.visitReturnExpression(node);
        }

        public boolean hasReturns() {
            return hasReturns;
        }

        public boolean allReturnsTrue() {
            return allReturnsTrue;
        }
    }
}