package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiParameterList;
import com.intellij.psi.PsiType;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.ULiteralExpression;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UReturnExpression;
import org.jetbrains.uast.UThrowExpression;
import org.jetbrains.uast.UastUtils;

public class BadHostnameVerifierDetector extends Detector implements SourceCodeScanner {

    private static final String HOSTNAME_VERIFIER = "javax.net.ssl.HostnameVerifier";

    private static final Implementation IMPLEMENTATION =
            new Implementation(BadHostnameVerifierDetector.class, Scope.JAVA_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                            "BadHostnameVerifier",
                            "Insecure HostnameVerifier",
                            "Implementations of `HostnameVerifier` whose `verify` method always "
                                    + "returns true trust every hostname in TLS/SSL certificates. "
                                    + "This allows attackers to intercept network traffic by "
                                    + "presenting a certificate for any hostname.",
                            Category.SECURITY,
                            6,
                            Severity.WARNING,
                            IMPLEMENTATION)
                    .setAndroidSpecific(false);

    private final Map<UMethod, UReturnExpression> mTrueReturns = new HashMap<>();
    private final Set<UMethod> mNonTrueExits = new HashSet<>();

    public BadHostnameVerifierDetector() {}

    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList(HOSTNAME_VERIFIER);
    }

    @Override
    public void visitClass(JavaContext context, UClass declaration) {
        if (declaration.isInterface()
                || HOSTNAME_VERIFIER.equals(declaration.getQualifiedName())) {
            return;
        }
    }

    @Override
    public void visitReturnExpression(JavaContext context, UReturnExpression expression) {
        UMethod method = getVerifyMethod(context, expression);
        if (method == null) {
            return;
        }

        UExpression returnValue = expression.getReturnExpression();
        if (returnValue != null && isTrueLiteral(returnValue)) {
            mTrueReturns.put(method, expression);
        } else {
            mNonTrueExits.add(method);
        }
    }

    @Override
    public void visitThrowExpression(JavaContext context, UThrowExpression expression) {
        UMethod method = getVerifyMethod(context, expression);
        if (method != null) {
            mNonTrueExits.add(method);
        }
    }

    @Override
    public void visitCallExpression(JavaContext context, UCallExpression expression) {
        // Calls inside verify() are not, by themselves, insecure. The insecure case
        // handled by this detector is an unconditional return of true.
    }

    @Override
    public void afterCheckFile(Context context) {
        if (context instanceof JavaContext) {
            JavaContext javaContext = (JavaContext) context;
            for (Map.Entry<UMethod, UReturnExpression> entry : mTrueReturns.entrySet()) {
                if (mNonTrueExits.contains(entry.getKey())) {
                    continue;
                }
                UReturnExpression ret = entry.getValue();
                javaContext.report(
                        ISSUE,
                        ret,
                        javaContext.getLocation(ret),
                        "Insecure HostnameVerifier: verify() always returns true, trusting any hostname");
            }
        }

        mTrueReturns.clear();
        mNonTrueExits.clear();
    }

    private static UMethod getVerifyMethod(JavaContext context, UElement node) {
        UMethod method = UastUtils.getParentOfType(node, UMethod.class, false);
        if (method == null || !"verify".equals(method.getName())) {
            return null;
        }

        PsiClass containingClass = UastUtils.getParentOfType(method, PsiClass.class, false);
        if (containingClass == null
                || !context.getEvaluator().implementsInterface(containingClass, HOSTNAME_VERIFIER, false)) {
            return null;
        }

        PsiParameterList parameterList = method.getParameterList();
        if (parameterList.getParametersCount() != 2) {
            return null;
        }

        PsiType returnType = method.getReturnType();
        if (returnType == null || !returnType.equalsToText("boolean")) {
            return null;
        }

        return method;
    }

    private static boolean isTrueLiteral(UExpression expression) {
        if (!(expression instanceof ULiteralExpression)) {
            return false;
        }
        Object value = ((ULiteralExpression) expression).getValue();
        return Boolean.TRUE.equals(value);
    }
}