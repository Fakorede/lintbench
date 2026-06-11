package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;

import com.intellij.psi.PsiMethod;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UReturnExpression;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UastUtils;

import java.util.Collections;
import java.util.List;

public class AllowAllHostnameVerifierDetector extends Detector implements SourceCodeScanner {

    public static final Issue ISSUE = Issue.create(
            "AllowAllHostnameVerifier",
            "Insecure `HostnameVerifier`",
            "This check looks for use of HostnameVerifier implementations whose `verify` method always returns true (thus trusting any hostname) which could result in insecure network traffic caused by trusting arbitrary hostnames in TLS/SSL certificates presented by peers.",
            Category.SECURITY,
            6,
            Severity.ERROR,
            new Implementation(
                    AllowAllHostnameVerifierDetector.class,
                    Scope.JAVA_FILE_SCOPE
            )
    );

    @Override
    public List<String> getApplicableMethodNames() {
        return Collections.singletonList("verify");
    }

    @Override
    public void visitMethodCall(JavaContext context, UCallExpression node, PsiMethod method) {
        if (method.getName().equals("verify")) {
            // Check the implementation of the verify method to see if it always returns true.
            UClass uClass = UastUtils.findParentOfType(node, UClass.class);
            if (uClass != null && isAlwaysTrue(uClass)) {
                context.report(ISSUE, node, context.getLocation(node),
                        "The `verify` method always returns true, which can lead to insecure network traffic.");
            }
        }
    }

    private boolean isAlwaysTrue(UClass uClass) {
        for (UMethod method : uClass.getMethods()) {
            if ("verify".equals(method.getName())) {
                return UastUtils.findDescendantsOfType(method.getBody(), UReturnExpression.class)
                        .stream()
                        .anyMatch(returnExpr -> "true".equals(returnExpr.getValue().getExpressionType().toString()));
            }
        }
        return false;
    }

}