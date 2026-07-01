package com.android.tools.lint.checks;

import com.android.tools.lint.client.api.JavaEvaluator;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;

import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.ULiteralExpression;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UParameter;
import org.jetbrains.uast.UReturnExpression;
import org.jetbrains.uast.UastVisitor;
import org.jetbrains.uast.visitor.AbstractUastVisitor;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class AllowAllHostnameVerifierDetector extends Detector implements SourceCodeScanner {

    public static final Issue ISSUE = Issue.create(
            "AllowAllHostnameVerifier",
            "Insecure `HostnameVerifier`",
            "This `HostnameVerifier` implementation accepts any hostname, which could "
                    + "result in insecure network traffic caused by trusting arbitrary "
                    + "hostnames in TLS/SSL certificates presented by peers. "
                    + "See https://goo.gle/AllowAllHostnameVerifier",
            Category.SECURITY,
            6,
            Severity.WARNING,
            new Implementation(
                    AllowAllHostnameVerifierDetector.class,
                    Scope.JAVA_FILE_SCOPE
            )
    );

    private static final String HOSTNAME_VERIFIER_CLASS = "javax.net.ssl.HostnameVerifier";
    private static final String VERIFY_METHOD_NAME = "verify";
    private static final String STRING_CLASS = "java.lang.String";
    private static final String SSL_SESSION_CLASS = "javax.net.ssl.SSLSession";

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Arrays.asList(UMethod.class);
    }

    @Override
    public UastVisitor createUastHandler(JavaContext context) {
        return new AllowAllHostnameVerifierVisitor(context);
    }

    private static class AllowAllHostnameVerifierVisitor extends AbstractUastVisitor {

        private final JavaContext mContext;

        AllowAllHostnameVerifierVisitor(JavaContext context) {
            this.mContext = context;
        }

        @Override
        public boolean visitMethod(UMethod node) {
            if (!isVerifyMethod(node)) {
                return super.visitMethod(node);
            }

            UClass containingClass = node.getContainingClass();
            if (containingClass == null) {
                return super.visitMethod(node);
            }

            JavaEvaluator evaluator = mContext.getEvaluator();
            if (!evaluator.extendsClass(containingClass.getPsi(), HOSTNAME_VERIFIER_CLASS, false)) {
                return super.visitMethod(node);
            }

            if (alwaysReturnsTrue(node)) {
                mContext.report(
                        ISSUE,
                        node,
                        mContext.getLocation(node),
                        "Using a `HostnameVerifier` that accepts any hostname is unsafe"
                );
            }

            return super.visitMethod(node);
        }

        private static boolean isVerifyMethod(UMethod method) {
            if (!VERIFY_METHOD_NAME.equals(method.getName())) {
                return false;
            }

            if (method.getReturnType() == null
                    || !"boolean".equals(method.getReturnType().getCanonicalText())) {
                return false;
            }

            List<UParameter> parameters = method.getUastParameters();
            if (parameters.size() != 2) {
                return false;
            }

            if (parameters.get(0).getType() == null
                    || !STRING_CLASS.equals(parameters.get(0).getType().getCanonicalText())) {
                return false;
            }

            if (parameters.get(1).getType() == null
                    || !SSL_SESSION_CLASS.equals(parameters.get(1).getType().getCanonicalText())) {
                return false;
            }

            return true;
        }

        private static boolean alwaysReturnsTrue(UMethod method) {
            if (method.getUastBody() == null) {
                return false;
            }

            final AtomicBoolean foundReturn = new AtomicBoolean(false);
            final AtomicBoolean allTrue = new AtomicBoolean(true);

            method.getUastBody().accept(new AbstractUastVisitor() {
                @Override
                public boolean visitReturnExpression(UReturnExpression node) {
                    foundReturn.set(true);
                    if (node.getReturnExpression() instanceof ULiteralExpression) {
                        Object value = ((ULiteralExpression) node.getReturnExpression()).getValue();
                        if (value == Boolean.TRUE) {
                            return super.visitReturnExpression(node);
                        }
                    }
                    allTrue.set(false);
                    return super.visitReturnExpression(node);
                }
            });

            return foundReturn.get() && allTrue.get();
        }
    }
}