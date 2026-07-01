package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import java.util.Arrays;
import java.util.List;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UReturnExpression;
import org.jetbrains.uast.UThrowExpression;
import org.jetbrains.uast.UastUtils;
import org.jetbrains.uast.visitor.UElementHandler;

public class BadHostnameVerifierDetector extends Detector implements SourceCodeScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(BadHostnameVerifierDetector.class, Scope.JAVA_FILE_SCOPE);

    private static final String HOSTNAME_VERIFIER = "javax.net.ssl.HostnameVerifier";

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
            IMPLEMENTATION);

    public BadHostnameVerifierDetector() {}

    @Override
    public List<String> applicableSuperClasses() {
        return Arrays.asList(HOSTNAME_VERIFIER);
    }

    @Override
    public UElementHandler createUastHandler(JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitClass(UClass node) {
                // Classes are pre-filtered by applicableSuperClasses().
                // No additional traversal setup required.
            }

            @Override
            public void visitReturnExpression(UReturnExpression node) {
                UMethod method = UastUtils.getParentOfType(node, UMethod.class);
                if (method == null || !method.getName().equals("verify")) {
                    return;
                }

                PsiMethod psiMethod = method.getJavaPsi();
                if (psiMethod != null) {
                    PsiParameter[] parameters = psiMethod.getParameterList().getParameters();
                    if (parameters.length != 2) {
                        return;
                    }
                }

                UClass containingClass = UastUtils.getParentOfType(node, UClass.class);
                if (containingClass == null) {
                    return;
                }

                if (!context.getEvaluator().implementsInterface(containingClass, HOSTNAME_VERIFIER, true)) {
                    return;
                }

                UExpression returnExpr = node.getReturnExpression();
                if (returnExpr != null && Boolean.TRUE.equals(returnExpr.evaluate())) {
                    context.report(
                            ISSUE,
                            node,
                            context.getLocation(node),
                            "Insecure HostnameVerifier: verify() always returns true");
                }
            }

            @Override
            public void visitThrowExpression(UThrowExpression node) {
                // Throwing an exception inside verify() indicates proper error handling
                // rather than blindly trusting all hostnames.
            }

            @Override
            public void visitCallExpression(UCallExpression node) {
                // Method calls inside verify() may indicate delegation to a safe verifier
                // or custom validation logic.
            }
        };
    }
}