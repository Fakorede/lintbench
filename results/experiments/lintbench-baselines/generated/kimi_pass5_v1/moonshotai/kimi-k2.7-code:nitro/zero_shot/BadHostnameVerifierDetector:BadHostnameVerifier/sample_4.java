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
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiType;
import java.util.Collections;
import java.util.List;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UReturnExpression;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.visitor.AbstractUastVisitor;

public class BadHostnameVerifierDetector extends Detector implements Detector.SourceCodeScanner {

    private static final String HOSTNAME_VERIFIER = "javax.net.ssl.HostnameVerifier";
    private static final String SSL_SESSION = "javax.net.ssl.SSLSession";

    private static final Implementation IMPLEMENTATION = new Implementation(
            BadHostnameVerifierDetector.class,
            Scope.JAVA_FILE_SCOPE
    );

    public static final Issue ISSUE = Issue.create(
            "BadHostnameVerifier",
            "Insecure HostnameVerifier",
            "This check looks for implementations of `HostnameVerifier` whose `verify` method "
                    + "always returns true (thus trusting any hostname), which could result in "
                    + "insecure network traffic caused by trusting arbitrary hostnames in TLS/SSL "
                    + "certificates presented by peers.\n"
                    + "Reference: https://goo.gle/BadHostnameVerifier",
            Category.SECURITY,
            6,
            Severity.WARNING,
            IMPLEMENTATION
    );

    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList(HOSTNAME_VERIFIER);
    }

    @Override
    public void visitClass(JavaContext context, UClass declaration) {
        for (UMethod method : declaration.getMethods()) {
            if (!"verify".equals(method.getName())) {
                continue;
            }

            PsiType returnType = method.getReturnType();
            if (returnType == null) {
                continue;
            }
            if (!returnType.equalsToText("boolean") && !returnType.equalsToText("java.lang.Boolean")) {
                continue;
            }

            PsiParameter[] parameters = method.getParameterList().getParameters();
            if (parameters.length != 2) {
                continue;
            }

            if (!isType(context, parameters[0], "java.lang.String")) {
                continue;
            }

            if (!isType(context, parameters[1], SSL_SESSION)) {
                continue;
            }

            if (alwaysReturnsTrue(method, context)) {
                Location location = context.getNameLocation(method);
                String message = "`verify` always returns `true`, which disables TLS/SSL "
                        + "hostname verification and allows any hostname to be trusted.";
                context.report(ISSUE, method, location, message);
                break;
            }
        }
    }

    private static boolean isType(JavaContext context, PsiParameter parameter, String typeName) {
        PsiClass psiClass = context.getEvaluator().getTypeClass(parameter.getType());
        return psiClass != null && typeName.equals(psiClass.getQualifiedName());
    }

    private static boolean alwaysReturnsTrue(UMethod method, JavaContext context) {
        UExpression body = method.getUastBody();
        if (body == null) {
            return false;
        }
        ReturnEvaluator visitor = new ReturnEvaluator(context);
        body.accept(visitor);
        return visitor.hasReturn && visitor.allTrue;
    }

    private static class ReturnEvaluator extends AbstractUastVisitor {
        private final ConstantEvaluator evaluator;
        boolean hasReturn = false;
        boolean allTrue = true;

        ReturnEvaluator(JavaContext context) {
            this.evaluator = new ConstantEvaluator(context);
        }

        @Override
        public boolean visitReturnExpression(UReturnExpression node) {
            hasReturn = true;
            UExpression expression = node.getReturnExpression();
            if (expression == null) {
                allTrue = false;
            } else {
                Object value = evaluator.evaluate(expression);
                if (!Boolean.TRUE.equals(value)) {
                    allTrue = false;
                }
            }
            return super.visitReturnExpression(node);
        }
    }
}