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
import com.intellij.psi.PsiMethod;
import java.util.Collections;
import java.util.List;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.ULiteralExpression;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UReturnExpression;
import org.jetbrains.uast.UThrowExpression;

public class BadHostnameVerifierDetector extends Detector implements SourceCodeScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(BadHostnameVerifierDetector.class, Scope.JAVA_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                            "BadHostnameVerifier",
                            "Insecure HostnameVerifier",
                            "This check looks for implementations of `HostnameVerifier` whose "
                                    + "`verify` method always returns true (thus trusting any "
                                    + "hostname) which could result in insecure network traffic caused "
                                    + "by trusting arbitrary hostnames in TLS/SSL certificates "
                                    + "presented by peers.",
                            Category.SECURITY,
                            6,
                            Severity.WARNING,
                            IMPLEMENTATION)
                    .setAndroidSpecific(true)
                    .addMoreInfo("https://goo.gle/BadHostnameVerifier");

    private static final String HOSTNAME_VERIFIER_CLASS = "javax.net.ssl.HostnameVerifier";

    private static final ThreadLocal<VerifierState> STATE = new ThreadLocal<>();

    private static class VerifierState {
        boolean returnsTrue = false;
        boolean returnsFalseOrOther = false;
        boolean throwsException = false;
    }

    public BadHostnameVerifierDetector() {}

    @Nullable
    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList(HOSTNAME_VERIFIER_CLASS);
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        UMethod verifyMethod = null;
        for (UMethod method : declaration.getMethods()) {
            if ("verify".equals(method.getName()) && method.getUastParameters().size() == 2) {
                verifyMethod = method;
                break;
            }
        }

        if (verifyMethod == null) {
            return;
        }

        UExpression body = verifyMethod.getUastBody();
        if (body == null) {
            return;
        }

        VerifierState state = new VerifierState();
        STATE.set(state);
        try {
            traverse(body, context);

            if (state.returnsTrue && !state.returnsFalseOrOther && !state.throwsException) {
                context.report(
                        ISSUE,
                        verifyMethod,
                        context.getNameLocation(verifyMethod),
                        "This `HostnameVerifier` implementation always returns `true`, "
                                + "which makes SSL/TLS connections highly insecure by trusting any "
                                + "host.");
            }
        } finally {
            STATE.remove();
        }
    }

    private void traverse(UElement element, JavaContext context) {
        if (element instanceof UReturnExpression) {
            visitReturnExpression(context, (UReturnExpression) element);
        } else if (element instanceof UCallExpression) {
            visitCallExpression(context, (UCallExpression) element);
        } else if (element instanceof UThrowExpression) {
            visitThrowExpression(context, (UThrowExpression) element);
        }
        for (UElement child : element.getUastChildren()) {
            traverse(child, context);
        }
    }

    @Override
    public void visitReturnExpression(@NonNull JavaContext context, @NonNull UReturnExpression node) {
        VerifierState state = STATE.get();
        if (state != null) {
            UExpression returnValue = node.getReturnExpression();
            if (returnValue instanceof ULiteralExpression) {
                Object value = ((ULiteralExpression) returnValue).getValue();
                if (Boolean.TRUE.equals(value)) {
                    state.returnsTrue = true;
                } else {
                    state.returnsFalseOrOther = true;
                }
            } else if (returnValue != null) {
                state.returnsFalseOrOther = true;
            }
        }
    }

    @Override
    public void visitThrowExpression(@NonNull JavaContext context, @NonNull UThrowExpression node) {
        VerifierState state = STATE.get();
        if (state != null) {
            state.throwsException = true;
        }
    }

    @Override
    public void visitCallExpression(@NonNull JavaContext context, @NonNull UCallExpression node) {
        // Overridden as scanner interface specification requirement
    }
}