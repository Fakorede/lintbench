package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.intellij.psi.PsiMethod;
import java.util.Collections;
import java.util.List;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.ULiteralExpression;
import org.jetbrains.uast.UastLiteralUtils;

public class PowerManagerDetector extends Detector implements Detector.UastScanner {

    private static final String POWER_MANAGER_CLASS = "android.os.PowerManager";
    private static final String NEW_WAKE_LOCK_METHOD = "newWakeLock";

    private static final Implementation IMPLEMENTATION =
            new Implementation(PowerManagerDetector.class, Scope.JAVA_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "InvalidWakeLockTag",
                    "Invalid Wake Lock Tag",
                    "Wake Lock tags must follow the naming convention `\"*TagName*:*Description*\"` "
                            + "as defined in the `PowerManager` documentation. "
                            + "The tag must contain a colon (`:`) separating the tag name from the description. "
                            + "Tags must not be null or empty.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    IMPLEMENTATION);

    @Override
    public List<String> getApplicableMethodNames() {
        return Collections.singletonList(NEW_WAKE_LOCK_METHOD);
    }

    @Override
    public void visitMethodCall(
            @NonNull JavaContext context,
            @NonNull UCallExpression node,
            @NonNull PsiMethod method) {
        // Check that the method is called on PowerManager
        if (!context.getEvaluator().isMemberInClass(method, POWER_MANAGER_CLASS)) {
            return;
        }

        // newWakeLock(int levelAndFlags, String tag)
        // The tag is the second argument (index 1)
        List<UExpression> arguments = node.getValueArguments();
        if (arguments.size() < 2) {
            return;
        }

        UExpression tagArgument = arguments.get(1);

        // Try to evaluate the tag as a constant string
        Object tagValue = tagArgument.evaluate();

        if (tagValue == null) {
            // We can't statically determine the value; skip
            return;
        }

        if (!(tagValue instanceof String)) {
            context.report(
                    ISSUE,
                    node,
                    context.getLocation(tagArgument),
                    "Wake Lock tag must be a string");
            return;
        }

        String tag = (String) tagValue;

        if (tag.isEmpty()) {
            context.report(
                    ISSUE,
                    node,
                    context.getLocation(tagArgument),
                    "Wake Lock tag must not be empty");
            return;
        }

        // According to PowerManager documentation, the tag should follow the format
        // "*TagName*:*description*" - it must contain a colon
        if (!tag.contains(":")) {
            context.report(
                    ISSUE,
                    node,
                    context.getLocation(tagArgument),
                    String.format(
                            "Wake Lock tag \"%s\" does not follow the naming convention "
                                    + "`\"*TagName*:*description*\"` — it is missing a colon `:` separator",
                            tag));
        }
    }
}