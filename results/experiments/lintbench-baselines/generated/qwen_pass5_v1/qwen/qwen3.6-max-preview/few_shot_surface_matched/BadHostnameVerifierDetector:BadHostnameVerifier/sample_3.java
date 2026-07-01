package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import org.jetbrains.annotations.NonNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.ULiteralExpression;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UReturnExpression;
import org.jetbrains.uast.UThrowExpression;
import org.jetbrains.uast.UastUtils;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class BadHostnameVerifierDetector extends Detector implements SourceCodeScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(BadHostnameVerifierDetector.class, Scope.JAVA_FILE_SCOPE);

    private static final String HOSTNAME_VERIFIER = "javax.net.ssl.HostnameVerifier";

    public static final Issue ISSUE = Issue.create(
            "BadHostnameVerifier",
            "Insecure HostnameVerifier",
            "This check looks for implementations of `HostnameVerifier` whose `verify` method "
                    + "always returns true (thus trusting any hostname) which could result in insecure "
                    + "network traffic caused by trusting arbitrary hostnames in TLS/SSL certificates "
                    + "presented by peers.",
            Category.SECURITY,
            6,
            Severity.WARNING,
            IMPLEMENTATION
    );

    private final Set<UMethod> methodsWithLogic = new HashSet<>();

    @Nullable
    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList(HOSTNAME_VERIFIER);
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        methodsWithLogic.clear();
    }

    @Override
    public void visitCallExpression(@NonNull JavaContext context, @NonNull UCallExpression node) {
        UMethod method = UastUtils.getContainingUMethod(node);
        if (method != null && "verify".equals(method.getName())) {
            methodsWithLogic.add(method);
        }
    }

    @Override
    public void visitThrowExpression(@NonNull JavaContext context, @NonNull UThrowExpression node) {
        UMethod method = UastUtils.getContainingUMethod(node);
        if (method != null && "verify".equals(method.getName())) {
            methodsWithLogic.add(method);
        }
    }

    @Override
    public void visitReturnExpression(@NonNull JavaContext context, @NonNull UReturnExpression node) {
        UMethod method = UastUtils.getContainingUMethod(node);
        if (method == null || !"verify".equals(method.getName())) {
            return;
        }

        UClass cls = UastUtils.getContainingUClass(node);
        if (cls == null || !context.getEvaluator().implementsInterface(cls, HOSTNAME_VERIFIER, true)) {
            return;
        }

        if (methodsWithLogic.contains(method)) {
            return;
        }

        org.jetbrains.uast.UExpression returnValue = node.getReturnExpression();
        if (returnValue instanceof ULiteralExpression) {
            Object value = ((ULiteralExpression) returnValue).getValue();
            if (Boolean.TRUE.equals(value)) {
                context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        "Insecure `HostnameVerifier`: `verify()` always returns `true`"
                );
            }
        }
    }
}