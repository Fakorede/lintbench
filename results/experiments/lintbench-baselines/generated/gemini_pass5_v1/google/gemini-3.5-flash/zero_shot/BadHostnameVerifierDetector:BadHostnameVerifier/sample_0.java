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
import java.util.Collections;
import java.util.List;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UReturnExpression;
import org.jetbrains.uast.UastLiteralUtils;
import org.jetbrains.uast.UastUtils;
import org.jetbrains.uast.visitor.AbstractUastVisitor;

public class BadHostnameVerifierDetector extends Detector implements SourceCodeScanner {

    public static final Issue ISSUE = Issue.create(
            "BadHostnameVerifier",
            "Insecure HostnameVerifier",
            "This check looks for implementations of `HostnameVerifier` whose " +
            "`verify` method always returns true (thus trusting any hostname) " +
            "which could result in insecure network traffic caused by trusting " +
            "arbitrary hostnames in TLS/SSL certificates presented by peers.",
            Category.SECURITY,
            6,
            Severity.WARNING,
            new Implementation(
                    BadHostnameVerifierDetector.class,
                    Scope.JAVA_FILE_SCOPE
            )
    ).addMoreInfo("https://goo.gle/BadHostnameVerifier");

    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList("javax.net.ssl.HostnameVerifier");
    }

    @Override
    public void visitClass(JavaContext context, UClass declaration) {
        UMethod verifyMethod = null;
        for (UMethod method : declaration.getMethods()) {
            if ("verify".equals(method.getName()) && method.getUastParameters().size() == 2) {
                PsiType returnType = method.getReturnType();
                if (returnType != null && PsiType.BOOLEAN.equals(returnType)) {
                    verifyMethod = method;
                    break;
                }
            }
        }

        if (verifyMethod == null) {
            return;
        }

        ReturnVisitor visitor = new ReturnVisitor(verifyMethod);
        verifyMethod.accept(visitor);

        if (visitor.isAlwaysTrue()) {
            context.report(
                    ISSUE,
                    declaration,
                    context.getNameLocation(verifyMethod),
                    "Implementation of `HostnameVerifier.verify` always returns `true`, which is insecure"
            );
        }
    }

    private static class ReturnVisitor extends AbstractUastVisitor {
        private final UMethod targetMethod;
        private final List<UReturnExpression> returns = new ArrayList<>();

        public ReturnVisitor(UMethod targetMethod) {
            this.targetMethod = targetMethod;
        }

        @Override
        public boolean visitReturnExpression(UReturnExpression node) {
            UMethod containingMethod = UastUtils.getParentOfType(node, UMethod.class);
            if (containingMethod != null && (containingMethod.equals(targetMethod) || 
                containingMethod.getSourcePsi() == targetMethod.getSourcePsi())) {
                returns.add(node);
            }
            return super.visitReturnExpression(node);
        }

        public boolean isAlwaysTrue() {
            if (returns.isEmpty()) {
                return false;
            }
            for (UReturnExpression ret : returns) {
                UExpression returnExpression = ret.getReturnExpression();
                if (returnExpression == null) {
                    return false;
                }
                if (!UastLiteralUtils.isBooleanLiteral(returnExpression, true)) {
                    return false;
                }
            }
            return true;
        }
    }
}