package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.ConstantEvaluator;
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
            "Wake Lock tags must follow the naming conventions defined in the `PowerManager` documentation. " +
            "To avoid name collisions, we recommend using a package-prefixed name such as: `com.myapp:mylocktag`.",
            Category.PERFORMANCE,
            5,
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

        List<UExpression> valueArguments = node.getValueArguments();
        if (valueArguments.size() < 2) {
            return;
        }

        UExpression tagArgument = valueArguments.get(1);
        Object evaluated = ConstantEvaluator.evaluate(context, tagArgument);
        if (evaluated instanceof String) {
            String tag = (String) evaluated;
            if (!isValidTag(tag)) {
                context.report(
                        ISSUE,
                        tagArgument,
                        context.getLocation(tagArgument),
                        "Wake Lock tag should be package-prefixed (e.g. `com.myapp:mylocktag`) to avoid name collisions"
                );
            }
        }
    }

    private static boolean isValidTag(String tag) {
        if (tag == null || tag.isEmpty()) {
            return false;
        }
        // Must contain a colon to be package-prefixed
        int colonIndex = tag.indexOf(':');
        if (colonIndex <= 0 || colonIndex == tag.length() - 1) {
            return false;
        }
        // Tags should not contain spaces
        if (tag.contains(" ")) {
            return false;
        }
        return true;
    }
}