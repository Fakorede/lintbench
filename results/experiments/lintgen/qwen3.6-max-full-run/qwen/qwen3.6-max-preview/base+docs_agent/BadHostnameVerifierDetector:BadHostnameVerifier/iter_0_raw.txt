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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.UBlockExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.ULiteralExpression;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UReturnExpression;
import org.jetbrains.uast.UastUtils;
import org.jetbrains.uast.visitor.UElementHandler;

import java.util.Collections;
import java.util.List;

public class BadHostnameVerifierDetector extends Detector implements SourceCodeScanner {

    public static final Issue ISSUE = Issue.create(
            "BadHostnameVerifier",
            "Insecure HostnameVerifier",
            "This check looks for implementations of `HostnameVerifier` whose `verify` method " +
            "always returns true (thus trusting any hostname) which could result in insecure " +
            "network traffic caused by trusting arbitrary hostnames in TLS/SSL certificates " +
            "presented by peers.",
            Category.SECURITY,
            6,
            Severity.WARNING,
            new Implementation(BadHostnameVerifierDetector.class, Scope.JAVA_FILE_SCOPE)
    );

    @Nullable
    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.singletonList(UMethod.class);
    }

    @Nullable
    @Override
    public UElementHandler createUastHandler(@NotNull JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitMethod(@NotNull UMethod method) {
                if (!"verify".equals(method.getName())) {
                    return;
                }

                if (!PsiType.BOOLEAN.equals(method.getReturnType())) {
                    return;
                }

                if (method.getUastParameters().size() != 2) {
                    return;
                }

                UClass containingClass = UastUtils.getContainingUClass(method);
                if (containingClass == null) {
                    return;
                }

                if (!context.getEvaluator().implementsInterface(containingClass, "javax.net.ssl.HostnameVerifier", false)) {
                    return;
                }

                UExpression body = method.getUastBody();
                if (body instanceof UBlockExpression) {
                    UBlockExpression block = (UBlockExpression) body;
                    List<UExpression> statements = block.getExpressions();
                    if (statements.size() == 1) {
                        UExpression statement = statements.get(0);
                        if (statement instanceof UReturnExpression) {
                            UReturnExpression returnExpr = (UReturnExpression) statement;
                            UExpression returnValue = returnExpr.getReturnExpression();
                            if (returnValue instanceof ULiteralExpression) {
                                ULiteralExpression literal = (ULiteralExpression) returnValue;
                                if (Boolean.TRUE.equals(literal.getValue())) {
                                    context.report(ISSUE, method, context.getLocation(method),
                                            "Insecure `HostnameVerifier`: `verify()` always returns `true`");
                                }
                            }
                        }
                    }
                }
            }
        };
    }
}