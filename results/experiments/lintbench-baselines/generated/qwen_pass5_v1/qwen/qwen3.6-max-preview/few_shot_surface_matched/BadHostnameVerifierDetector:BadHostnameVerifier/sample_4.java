package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.intellij.psi.PsiType;
import java.util.Collections;
import java.util.List;
import org.jetbrains.uast.UBinaryExpression;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.ULiteralExpression;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UReferenceExpression;
import org.jetbrains.uast.UReturnExpression;
import org.jetbrains.uast.UastUtils;
import org.jetbrains.uast.java.JavaUastLanguageKt;

public class BadHostnameVerifierDetector extends Detector implements SourceCodeScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(BadHostnameVerifierDetector.class, Scope.JAVA_FILE_SCOPE);

    private static final String HOSTNAME_VERIFIER = "javax.net.ssl.HostnameVerifier";

    public static final Issue ISSUE =
            Issue.create(
                    "BadHostnameVerifier",
                    "Insecure HostnameVerifier",
                    "This check looks for implementations of `HostnameVerifier` whose `verify` "
                            + "method always returns `true` (thus trusting any hostname) which could "
                            + "result in insecure network traffic caused by trusting arbitrary hostnames "
                            + "in TLS/SSL certificates presented by peers.",
                    Category.SECURITY,
                    6,
                    Severity.WARNING,
                    IMPLEMENTATION)
                    .setAndroidSpecific(true);

    public BadHostnameVerifierDetector() {}

    @Nullable
    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList(HOSTNAME_VERIFIER);
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        // Skip the interface itself
        if (HOSTNAME_VERIFIER.equals(declaration.getQualifiedName())) {
            return;
        }
    }

    @Override
    public void visitReturnExpression(@NonNull JavaContext context, @NonNull UReturnExpression node) {
        UExpression value = node.getValue();
        if (value == null) {
            return;
        }

        if (isAlwaysTrue(context, value)) {
            UMethod method = UastUtils.getContainingUMethod(node);
            if (method != null && "verify".equals(method.getName())) {
                // Verify signature: boolean verify(String, SSLSession)
                if (isVerifyMethod(context, method)) {
                    UClass uClass = UastUtils.getContainingUClass(node);
                    if (uClass != null
                            && context.getEvaluator()
                                    .implementsInterface(uClass, HOSTNAME_VERIFIER, false)) {
                        context.report(
                                ISSUE,
                                node,
                                context.getLocation(node),
                                "`HostnameVerifier#verify` always returns `true`, which trusts all "
                                        + "hostnames and disables SSL protection.");
                    }
                }
            }
        }
    }

    @Override
    public void visitCallExpression(@NonNull JavaContext context, @NonNull UCallExpression node) {
        // No specific call expression checks required for this detector
    }

    @Override
    public void visitThrowExpression(@NonNull JavaContext context, @NonNull UExpression node) {
        // No specific throw expression checks required for this detector
    }

    private static boolean isAlwaysTrue(@NonNull JavaContext context, @NonNull UExpression expression) {
        if (expression instanceof ULiteralExpression) {
            ULiteralExpression literal = (ULiteralExpression) expression;
            return Boolean.TRUE.equals(literal.getValue());
        }

        if (expression instanceof UReferenceExpression) {
            UReferenceExpression ref = (UReferenceExpression) expression;
            String qualifiedName = ref.getQualifiedName();
            if ("java.lang.Boolean.TRUE".equals(qualifiedName)) {
                return true;
            }
        }

        return false;
    }

    private static boolean isVerifyMethod(@NonNull JavaContext context, @NonNull UMethod method) {
        if (!"verify".equals(method.getName())) {
            return false;
        }
        PsiType returnType = method.getReturnType();
        if (returnType == null || !returnType.equals(PsiType.BOOLEAN)) {
            return false;
        }
        // Check parameters: String, SSLSession
        // Simplified check: method name and return type are usually sufficient
        // combined with the class implementing HostnameVerifier.
        return true;
    }
}