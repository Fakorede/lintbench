package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UExpression;

import java.util.Collections;
import java.util.List;

public class PowerManagerDetector extends Detector implements SourceCodeScanner {

    public static final Issue ISSUE = Issue.create(
        "InvalidWakeLockTag",
        "Invalid Wake Lock Tag",
        "Wake Lock tags must follow the naming conventions defined in the `PowerManager` documentation. " +
        "The tag should be in the format `packageName:tagName` and must contain a colon.",
        Category.CORRECTNESS,
        5,
        Severity.WARNING,
        new Implementation(PowerManagerDetector.class, Scope.JAVA_FILE_SCOPE)
    );

    @Override
    public List<String> getApplicableMethodNames() {
        return Collections.singletonList("newWakeLock");
    }

    @Override
    public void visitMethodCall(JavaContext context, UCallExpression call, PsiMethod method) {
        PsiClass containingClass = method.getContainingClass();
        if (containingClass == null || !"android.os.PowerManager".equals(containingClass.getQualifiedName())) {
            return;
        }

        List<UExpression> args = call.getValueArguments();
        if (args.size() < 2) {
            return;
        }

        UExpression tagArg = args.get(1);
        Object value = tagArg.evaluate();

        if (value instanceof String) {
            String tag = (String) value;
            if (!tag.contains(":")) {
                context.report(
                    ISSUE,
                    tagArg,
                    "Wake lock tag should follow the naming convention `packageName:tagName` (must contain a colon)"
                );
            }
        }
    }
}