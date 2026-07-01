package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.UElementHandler;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.ULiteralExpression;

import java.util.Collections;
import java.util.List;

public class PowerManagerDetector extends Detector implements Detector.UastScanner {

    public static final Issue ISSUE = Issue.create(
        "InvalidWakeLockTag",
        "Invalid Wake Lock Tag",
        "Wake Lock tags must follow the naming conventions defined in the `PowerManager` documentation. " +
        "The tag should be in the format `*:*` (e.g., `MyApp:MyWakeLock`) to help with debugging.",
        Category.CORRECTNESS,
        6,
        Severity.WARNING,
        new Implementation(PowerManagerDetector.class, Scope.JAVA_FILE_SCOPE));

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.singletonList(UCallExpression.class);
    }

    @Override
    public UElementHandler createUastHandler() {
        return new UElementHandler() {
            @Override
            public void visitCallExpression(UCallExpression node) {
                String methodName = node.getMethodName();
                if (!"newWakeLock".equals(methodName)) {
                    return;
                }

                PsiMethod method = node.resolve();
                if (method == null) {
                    return;
                }

                PsiClass containingClass = method.getContainingClass();
                if (containingClass == null || !"android.os.PowerManager".equals(containingClass.getQualifiedName())) {
                    return;
                }

                if (method.getParameterList().getParametersCount() != 2) {
                    return;
                }

                List<UExpression> args = node.getValueArguments();
                if (args.size() < 2) {
                    return;
                }

                UExpression tagArg = args.get(1);
                if (tagArg instanceof ULiteralExpression) {
                    ULiteralExpression literal = (ULiteralExpression) tagArg;
                    Object value = literal.getValue();
                    if (value instanceof String) {
                        String tag = (String) value;
                        if (!tag.contains(":")) {
                            JavaContext context = (JavaContext) getContext();
                            context.report(
                                ISSUE,
                                tagArg,
                                context.getLocation(tagArg),
                                "Wake lock tags should follow the format `*:*` (e.g., `MyApp:MyWakeLock`) " +
                                "to help with debugging and battery usage tracking."
                            );
                        }
                    }
                }
            }
        };
    }
}