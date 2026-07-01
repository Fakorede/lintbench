package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.intellij.psi.PsiMethod;
import java.util.Collections;
import java.util.List;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UExpression;

public class PowerManagerDetector extends Detector implements SourceCodeScanner {

    public static final Issue ISSUE = Issue.create(
            "InvalidWakeLockTag",
            "Invalid Wake Lock Tag",
            "Wake Lock tags must follow the naming conventions defined in the `PowerManager` documentation.\n" +
            "\n" +
            "Specifically, they should not contain spaces, they should be prefixed with the package name " +
            "or a unique prefix, separated by a colon, and they should be under 50 characters.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(
                    PowerManagerDetector.class,
                    Scope.JAVA_FILE_SCOPE
            )
    );

    @Override
    public List<String> getApplicableMethodNames() {
        return Collections.singletonList("newWakeLock");
    }

    @Override
    public void visitMethodCall(JavaContext context, UCallExpression node, PsiMethod method) {
        if (!context.getEvaluator().isMemberInClass(method, "android.os.PowerManager")) {
            return;
        }

        List<UExpression> arguments = node.getValueArguments();
        if (arguments.size() < 2) {
            return;
        }

        UExpression tagArgument = arguments.get(1);
        Object evaluated = tagArgument.evaluate();
        if (!(evaluated instanceof String)) {
            return;
        }

        String tag = (String) evaluated;

        if (tag.contains(" ")) {
            context.report(
                    ISSUE,
                    node,
                    context.getLocation(tagArgument),
                    "Wake Lock tags should not contain spaces"
            );
        } else if (tag.length() > 50) {
            context.report(
                    ISSUE,
                    node,
                    context.getLocation(tagArgument),
                    "Wake Lock tags should be less than 50 characters"
            );
        } else if (!tag.contains(":")) {
            context.report(
                    ISSUE,
                    node,
                    context.getLocation(tagArgument),
                    "Wake Lock tags should be prefixed with the package name or a unique prefix, separated by a colon"
            );
        }
    }
}