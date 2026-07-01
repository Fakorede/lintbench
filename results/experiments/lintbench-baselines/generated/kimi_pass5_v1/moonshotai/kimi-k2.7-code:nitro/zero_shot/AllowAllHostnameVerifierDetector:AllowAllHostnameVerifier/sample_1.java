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
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypes;
import java.util.Arrays;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UElementHandler;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UParameter;
import org.jetbrains.uast.UReturnExpression;
import org.jetbrains.uast.UastUtils;
import org.jetbrains.uast.visitor.AbstractUastVisitor;

public class AllowAllHostnameVerifierDetector extends Detector
        implements Detector.SourceCodeScanner {

    private static final String HOSTNAME_VERIFIER = "javax.net.ssl.HostnameVerifier";
    private static final String SSL_SESSION = "javax.net.ssl.SSLSession";
    private static final String VERIFY = "verify";

    @Override
    @NotNull
    public List<Class<? extends UElement>> getApplicableUElementTypes() {
        return Arrays.asList(UMethod.class);
    }

    @Override
    @NotNull
    public UElementHandler createUElementHandler(@NotNull JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitMethod(@NotNull UMethod node) {
                handleMethod(context, node);
            }
        };
    }

    private static void handleMethod(@NotNull JavaContext context, @NotNull UMethod node) {
        if (!VERIFY.equals(node.getName())) {
            return;
        }

        PsiMethod psiMethod = node.getJavaPsi();
        if (psiMethod == null || psiMethod.getParameterList().getParametersCount() != 2) {
            return;
        }

        List<UParameter> parameters = node.getUastParameters();
        if (parameters.size() != 2) {
            return;
        }

        if (!context.getEvaluator().typeHasName(parameters.get(0).getType(), "java.lang.String")) {
            return;
        }
        if (!context.getEvaluator().typeHasName(parameters.get(1).getType(), SSL_SESSION)) {
            return;
        }

        PsiType returnType = psiMethod.getReturnType();
        if (returnType == null || !returnType.equals(PsiTypes.booleanType())) {
            return;
        }

        UClass containingClass = UastUtils.getParentOfType(node, UClass.class, false);
        if (containingClass == null) {
            return;
        }

        PsiClass psiClass = containingClass.getJavaPsi();
        if (psiClass == null
                || !context.getEvaluator().extendsClass(psiClass, HOSTNAME_VERIFIER, false)) {
            return;
        }

        UExpression body = node.getUastBody();
        if (body == null) {
            return;
        }

        if (returnsAlwaysTrue(context, body)) {
            Location location = context.getNameLocation(node);
            context.report(
                    ISSUE,
                    node,
                    location,
                    "This HostnameVerifier accepts every hostname and is insecure");
        }
    }

    private static boolean returnsAlwaysTrue(@NotNull JavaContext context, @NotNull UExpression body) {
        final boolean[] foundReturn = new boolean[1];
        final boolean[] allTrue = new boolean[] { true };

        body.accept(new AbstractUastVisitor() {
            @Override
            public boolean visitReturnExpression(@NotNull UReturnExpression node) {
                foundReturn[0] = true;
                UExpression value = node.getReturnExpression();
                if (value == null) {
                    allTrue[0] = false;
                } else {
                    Object result = ConstantEvaluator.evaluate(context, value);
                    if (!Boolean.TRUE.equals(result)) {
                        allTrue[0] = false;
                    }
                }
                return false;
            }
        });

        if (!foundReturn[0]) {
            Object result = ConstantEvaluator.evaluate(context, body);
            return Boolean.TRUE.equals(result);
        }

        return allTrue[0];
    }

    public static final Issue ISSUE =
            Issue.create(
                    "AllowAllHostnameVerifier",
                    "Insecure HostnameVerifier",
                    "This HostnameVerifier implementation always returns true from its verify "
                            + "method. Accepting any hostname can allow TLS/SSL certificate "
                            + "hostname mismatches to be ignored, resulting in insecure "
                            + "network traffic.",
                    Category.SECURITY,
                    6,
                    Severity.WARNING,
                    new Implementation(
                            AllowAllHostnameVerifierDetector.class, Scope.JAVA_FILE_SCOPE),
                    "https://goo.gle/AllowAllHostnameVerifier");
}