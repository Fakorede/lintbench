package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
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

    private static final Implementation IMPLEMENTATION =
            new Implementation(PowerManagerDetector.class, Scope.JAVA_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "InvalidWakeLockTag",
                    "Invalid Wake Lock Tag",
                    "Wake Lock tags must follow the naming conventions defined in the `PowerManager` documentation. "
                            + "They should not contain spaces, should contain a colon (e.g. `myapp:mytag`) to allow "
                            + "battery attribution, and should not contain wildcards like `*`.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    IMPLEMENTATION);

    @Override
    public void visitMethodCall(
            @NonNull JavaContext context,
            @NonNull UCallExpression node,
            @NonNull PsiMethod method) {
        if (!context.getEvaluator().isMemberInClass(method, "android.os.PowerManager")) {
            return;
        }

        List<UExpression> arguments = node.getValueArguments();
        if (arguments.size() < 2) {
            return;
        }

        UExpression tagArgument = arguments.get(1);
        Object value = ConstantEvaluator.evaluate(context, tagArgument);
        if (value instanceof String) {
            String tag = (String) value;
            validateTag(context, tagArgument, tag);
        }
    }

    private void validateTag(@NonNull JavaContext context, @NonNull UExpression tagNode, @NonNull String tag) {
        if (tag.isEmpty()) {
            context.report(
                    ISSUE,
                    tagNode,
                    context.getLocation(tagNode),
                    "Wake lock tag must not be empty");
            return;
        }

        if (tag.contains(" ")) {
            context.report(
                    ISSUE,
                    tagNode,
                    context.getLocation(tagNode),
                    "Wake lock tags should not contain spaces to avoid platform attribution issues");
            return;
        }

        if (!tag.contains(":")) {
            context.report(
                    ISSUE,
                    tagNode,
                    context.getLocation(tagNode),
                    "Wake lock tags should follow the naming conventions: `<package-name>:<tag-name>` (e.g. `com.example.myapp:my_wakelock`) to allow battery attribution");
            return;
        }

        if (tag.contains("*")) {
            context.report(
                    ISSUE,
                    tagNode,
                    context.getLocation(tagNode),
                    "Wake lock tags should not contain '*' as this can break battery attribution");
        }
    }

    @Override
    public List<String> getApplicableMethodNames() {
        return Collections.singletonList("newWakeLock");
    }
}