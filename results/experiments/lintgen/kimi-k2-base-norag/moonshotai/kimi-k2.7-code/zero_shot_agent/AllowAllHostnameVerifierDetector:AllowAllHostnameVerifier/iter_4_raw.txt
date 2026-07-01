package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.intellij.psi.PsiType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.ULiteralExpression;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UReturnExpression;

public final class AllowAllHostnameVerifierDetector extends Detector implements SourceCodeScanner {

    private static final String HOSTNAME_VERIFIER = "javax.net.ssl.HostnameVerifier";
    private static final String X509_HOSTNAME_VERIFIER = "org.apache.http.conn.ssl.X509HostnameVerifier";

    private static final Issue ISSUE = Issue.create(
            "AllowAllHostnameVerifier",
            "Insecure HostnameVerifier",
            "This check looks for use of HostnameVerifier implementations whose verify method "
                    + "always returns true (thus trusting any hostname) which could result in "
                    + "insecure network traffic caused by trusting arbitrary hostnames in "
                    + "TLS/SSL certificates presented by peers.",
            Category.SECURITY,
            6,
            Severity.WARNING,
            new Implementation(AllowAllHostnameVerifierDetector.class, Scope.JAVA_FILE_SCOPE)
    );

    @Override
    @NotNull
    public List<String> applicableSuperClasses() {
        return Arrays.asList(HOSTNAME_VERIFIER, X509_HOSTNAME_VERIFIER);
    }

    @Override
    public void visitClass(@NotNull JavaContext context, @NotNull UClass declaration) {
        for (UMethod method : declaration.getMethods()) {
            if (!"verify".equals(method.getName())) {
                continue;
            }
            if (!PsiType.BOOLEAN.equals(method.getReturnType())) {
                continue;
            }
            if (returnsTrue(method)) {
                context.report(
                        ISSUE,
                        method,
                        context.getNameLocation(method),
                        "Using a HostnameVerifier that accepts all hostnames is unsafe"
                );
            }
        }
    }

    private static boolean returnsTrue(@NotNull UMethod method) {
        UExpression body = method.getUastBody();
        if (body == null) {
            return false;
        }
        List<UReturnExpression> returns = new ArrayList<>();
        collectReturns(body, returns);
        if (returns.isEmpty()) {
            return false;
        }
        for (UReturnExpression ret : returns) {
            UExpression expr = ret.getReturnExpression();
            if (expr == null || !isTrueLiteral(expr)) {
                return false;
            }
        }
        return true;
    }

    private static void collectReturns(@NotNull UElement element, @NotNull List<UReturnExpression> returns) {
        if (element instanceof UReturnExpression) {
            returns.add((UReturnExpression) element);
            return;
        }
        for (UElement child : element.getChildren()) {
            collectReturns(child, returns);
        }
    }

    private static boolean isTrueLiteral(@NotNull UExpression expression) {
        return expression instanceof ULiteralExpression
                && Boolean.TRUE.equals(((ULiteralExpression) expression).getValue());
    }
}