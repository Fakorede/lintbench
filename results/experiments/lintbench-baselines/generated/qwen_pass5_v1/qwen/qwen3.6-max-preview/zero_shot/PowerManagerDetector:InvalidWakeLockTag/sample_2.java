package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import org.jetbrains.annotations.NonNull;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UExpression;

import java.util.Collections;
import java.util.List;

public class PowerManagerDetector extends Detector implements Detector.UastScanner {

    public static final Issue ISSUE = Issue.create(
            "InvalidWakeLockTag",
            "Invalid Wake Lock Tag",
            "Wake Lock tags must follow the naming conventions defined in the PowerManager documentation. " +
            "The tag should be in the format `your.package.name:your_tag_name`.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(PowerManagerDetector.class, Scope.JAVA_FILE_SCOPE)
    );

    @Override
    public List<String> getApplicableMethodNames() {
        return Collections.singletonList("newWakeLock");
    }

    @Override
    public void visitMethodCall(@NonNull JavaContext context, @NonNull UCallExpression node, @NonNull PsiMethod method) {
        PsiClass containingClass = method.getContainingClass();
        if (containingClass == null || !"android.os.PowerManager".equals(containingClass.getQualifiedName())) {
            return;
        }

        List<UExpression> arguments = node.getValueArguments();
        if (arguments.size() < 2) {
            return;
        }

        UExpression tagArgument = arguments.get(1);
        Object evaluatedTag = context.getEvaluator().evaluate(tagArgument);

        if (evaluatedTag instanceof String) {
            String tag = (String) evaluatedTag;
            if (tag.indexOf(':') == -1) {
                context.report(
                        ISSUE,
                        context.getLocation(tagArgument),
                        "Wake lock tag should be in the format `package.name:tag_name` (must contain a colon)"
                );
            }
        }
    }
}