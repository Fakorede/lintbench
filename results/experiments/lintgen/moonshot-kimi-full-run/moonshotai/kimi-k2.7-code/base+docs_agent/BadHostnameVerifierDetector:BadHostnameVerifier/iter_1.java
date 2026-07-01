package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.ConstantEvaluator;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.UElementHandler;
import com.intellij.psi.PsiClass;
import java.util.Collections;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.UBlockExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UParameter;
import org.jetbrains.uast.UReturnExpression;
import org.jetbrains.uast.UastUtils;
import org.jetbrains.uast.visitor.AbstractUastVisitor;

public class BadHostnameVerifierDetector extends Detector implements SourceCodeScanner {

    private static final Implementation IMPLEMENTATION = new Implementation(
            BadHostnameVerifierDetector.class,
            Scope.JAVA_FILE_SCOPE
    );

    public static final Issue ISSUE = Issue.create(
            "BadHostnameVerifier",
            "Insecure HostnameVerifier",
            "This `HostnameVerifier` implementation always returns `true`, which causes the " +
            "app to trust any hostname presented by the peer. This bypasses hostname " +
            "verification in TLS/SSL connections and can allow man-in-the-middle attacks.\n" +
            "See https://goo.gle/BadHostnameVerifier",
            Category.SECURITY,
            6,
            Severity.WARNING,
            IMPLEMENTATION
    );

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.singletonList(UMethod.class);
    }

    @Override
    public UElementHandler createUastHandler(@NotNull JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitMethod(@NotNull UMethod node) {
                if (!isVerifyMethod(node)) {
                    return;
                }
                if (!isInHostnameVerifier(node)) {
                    return;
                }
                if (alwaysReturnsTrue(node, context)) {
                    Location location = context.getNameLocation(node);
                    context.report(
                            ISSUE,
                            location,
                            "Insecure `HostnameVerifier`: `verify` always returns `true`"
                    );
                }
            }
        };
    }

    private static boolean isInHostnameVerifier(@NotNull UMethod method) {
        UClass current = UastUtils.getParentOfType(method, UClass.class, true);
        while (current != null) {
            if (implementsHostnameVerifier(current)) {
                return true;
            }
            current = UastUtils.getParentOfType(current, UClass.class, true);
        }
        return false;
    }

    private static boolean implementsHostnameVerifier(@NotNull UClass node) {
        PsiClass psi = node.getJavaPsi();
        return psi != null && isInheritor(psi, "javax.net.ssl.HostnameVerifier");
    }

    private static boolean isInheritor(@Nullable PsiClass psi, @NotNull String qualifiedName) {
        if (psi == null) {
            return false;
        }
        if (qualifiedName.equals(psi.getQualifiedName())) {
            return true;
        }
        for (PsiClass iface : psi.getInterfaces()) {
            if (isInheritor(iface, qualifiedName)) {
                return true;
            }
        }
        return isInheritor(psi.getSuperClass(), qualifiedName);
    }

    private static boolean isVerifyMethod(@NotNull UMethod method) {
        if (!"verify".equals(method.getName())) {
            return false;
        }

        List<UParameter> params = method.getUastParameters();
        if (params.size() != 2) {
            return false;
        }

        String first = params.get(0).getType().getCanonicalText();
        String second = params.get(1).getType().getCanonicalText();

        return "java.lang.String".equals(first)
                && "javax.net.ssl.SSLSession".equals(second);
    }

    private static boolean alwaysReturnsTrue(@NotNull UMethod method, @NotNull JavaContext context) {
        UExpression body = method.getUastBody();
        if (body == null) {
            return false;
        }

        if (body instanceof UReturnExpression) {
            return evaluatesToTrue(((UReturnExpression) body).getReturnExpression(), context);
        }

        if (!(body instanceof UBlockExpression)) {
            return evaluatesToTrue(body, context);
        }

        final boolean[] foundReturn = {false};
        final boolean[] allTrue = {true};

        body.accept(new AbstractUastVisitor() {
            @Override
            public boolean visitReturnExpression(@NotNull UReturnExpression node) {
                foundReturn[0] = true;
                if (!evaluatesToTrue(node.getReturnExpression(), context)) {
                    allTrue[0] = false;
                }
                return super.visitReturnExpression(node);
            }
        });

        return foundReturn[0] && allTrue[0];
    }

    private static boolean evaluatesToTrue(
            @Nullable UExpression expression,
            @NotNull JavaContext context) {
        if (expression == null) {
            return false;
        }
        Object value = ConstantEvaluator.evaluate(context, expression);
        return Boolean.TRUE.equals(value);
    }
}