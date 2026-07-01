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
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.ULiteralExpression;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UReturnExpression;
import org.jetbrains.uast.visitor.AbstractUastVisitor;

import java.util.Collections;
import java.util.List;

public class BadHostnameVerifierDetector extends Detector implements SourceCodeScanner {

    public static final Issue ISSUE = Issue.create(
            "BadHostnameVerifier",
            "Insecure HostnameVerifier",
            "This check looks for implementations of `HostnameVerifier` whose `verify` method " +
            "always returns true (thus trusting any hostname) which could result in insecure " +
            "network traffic caused by trusting arbitrary hostnames in TLS/SSL certificates " +
            "presented by peers.",
            Category.SECURITY,
            6,
            Severity.WARNING,
            new Implementation(
                    BadHostnameVerifierDetector.class,
                    Scope.JAVA_FILE_SCOPE
            )
    ).addMoreInfo("https://goo.gle/BadHostnameVerifier");

    private static final String HOSTNAME_VERIFIER = "javax.net.ssl.HostnameVerifier";
    private static final String VERIFY_METHOD = "verify";

    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList(HOSTNAME_VERIFIER);
    }

    @Override
    public void visitClass(JavaContext context, UClass declaration) {
        for (UMethod method : declaration.getMethods()) {
            if (isVerifyMethod(method)) {
                checkVerifyMethod(context, method);
            }
        }
    }

    private boolean isVerifyMethod(UMethod method) {
        if (!VERIFY_METHOD.equals(method.getName())) {
            return false;
        }
        PsiMethod psiMethod = method.getJavaPsi();
        if (psiMethod.getParameterList().getParametersCount() != 2) {
            return false;
        }
        PsiType returnType = psiMethod.getReturnType();
        if (returnType == null) {
            return false;
        }
        return PsiType.BOOLEAN.equals(returnType);
    }

    private void checkVerifyMethod(JavaContext context, UMethod method) {
        UExpression body = method.getUastBody();
        if (body == null) {
            return;
        }

        AlwaysReturnsTrueVisitor visitor = new AlwaysReturnsTrueVisitor();
        body.accept(visitor);

        if (visitor.alwaysReturnsTrue()) {
            context.report(
                    ISSUE,
                    method,
                    context.getLocation(method),
                    "`verify` always returns `true`, which could cause insecure network traffic " +
                    "due to trusting arbitrary hostnames"
            );
        }
    }

    private static class AlwaysReturnsTrueVisitor extends AbstractUastVisitor {
        private int returnCount = 0;
        private int returnTrueCount = 0;

        @Override
        public boolean visitReturnExpression(UReturnExpression node) {
            returnCount++;
            UExpression returnValue = node.getReturnExpression();
            if (returnValue instanceof ULiteralExpression) {
                ULiteralExpression literal = (ULiteralExpression) returnValue;
                Object value = literal.getValue();
                if (Boolean.TRUE.equals(value)) {
                    returnTrueCount++;
                }
            }
            return super.visitReturnExpression(node);
        }

        public boolean alwaysReturnsTrue() {
            return returnCount > 0 && returnCount == returnTrueCount;
        }
    }
}