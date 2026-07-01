package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
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
import org.jetbrains.uast.ULiteralExpression;
import org.jetbrains.uast.UastLiteralUtils;

public class PowerManagerDetector extends Detector implements SourceCodeScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(PowerManagerDetector.class, Scope.JAVA_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "InvalidWakeLockTag",
                    "Invalid Wake Lock Tag",
                    "Wake lock tags must be in the format `\"*TagName*\"` where `*TagName*` "
                            + "is a string that identifies your tag. The tag must contain a colon "
                            + "(:) to separate the package or class name from the specific tag "
                            + "for the wake lock. For example: `\"MyApp:MyWakeLockTag\"`.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    IMPLEMENTATION);

    private static final String POWER_MANAGER_CLASS = "android.os.PowerManager";
    private static final String NEW_WAKE_LOCK_METHOD = "newWakeLock";

    @Override
    public List<String> getApplicableMethodNames() {
        return Collections.singletonList(NEW_WAKE_LOCK_METHOD);
    }

    @Override
    public void visitMethodCall(
            @NonNull JavaContext context,
            @NonNull UCallExpression node,
            @NonNull PsiMethod method) {

        if (!context.getEvaluator().isMemberInClass(method, POWER_MANAGER_CLASS)) {
            return;
        }

        List<UExpression> arguments = node.getValueArguments();
        // newWakeLock(int levelAndFlags, String tag) — tag is the second argument
        if (arguments.size() < 2) {
            return;
        }

        UExpression tagArgument = arguments.get(1);

        // Evaluate the tag expression to get a constant string value
        Object tagValue = tagArgument.evaluate();
        if (tagValue == null) {
            // We can't evaluate the tag at compile time; skip
            return;
        }

        if (!(tagValue instanceof String)) {
            return;
        }

        String tag = (String) tagValue;

        if (!isValidWakeLockTag(tag)) {
            context.report(
                    ISSUE,
                    node,
                    context.getLocation(tagArgument),
                    "Tag name must contain a colon (`:`) to separate the package or class "
                            + "name from the specific tag for the wake lock, "
                            + "e.g. `\"MyApp:MyWakeLockTag\"`");
        }
    }

    /**
     * Validates that the wake lock tag follows the required naming convention.
     *
     * <p>According to the Android documentation, wake lock tags must be in the format
     * "*packagename*:*tag*" where the tag contains a colon separating the package/class
     * name from the specific wake lock identifier.
     *
     * @param tag the wake lock tag string to validate
     * @return true if the tag is valid, false otherwise
     */
    private static boolean isValidWakeLockTag(@NonNull String tag) {
        // The tag must contain a colon to separate the package/class name from the tag name
        return tag.contains(":");
    }
}