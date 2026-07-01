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
                    "Wake Lock tags must follow the format `*TagName*` as defined in the "
                            + "`PowerManager` documentation. The tag must not be null or empty, "
                            + "and should be in the format `YourClassName:YourTag` or similar "
                            + "identifier that helps identify the wake lock owner. "
                            + "Avoid using spaces or special characters in the tag.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    IMPLEMENTATION);

    private static final String POWER_MANAGER_CLASS = "android.os.PowerManager";
    private static final String NEW_WAKE_LOCK_METHOD = "newWakeLock";

    /**
     * Wake lock tags must follow the format defined by PowerManager:
     * - Must not be null or empty
     * - Must be in the format "package:tag" or "ClassName:tag"
     * - Should contain a colon separating the package/class name from the tag name
     * - Should not contain spaces or special characters
     */
    private static final String TAG_FORMAT_REGEX = "^[\\w.]+:[\\w.]+$";

    @Override
    public List<String> getApplicableMethodNames() {
        return Collections.singletonList(NEW_WAKE_LOCK_METHOD);
    }

    @Override
    public void visitMethodCall(
            @NonNull JavaContext context,
            @NonNull UCallExpression node,
            @NonNull PsiMethod method) {

        // Check that this is a call to PowerManager.newWakeLock
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

        // We can only validate string literals at compile time
        if (!(tagArgument instanceof ULiteralExpression)) {
            return;
        }

        ULiteralExpression literal = (ULiteralExpression) tagArgument;

        // Check for null literal
        if (literal.isNull()) {
            context.report(
                    ISSUE,
                    node,
                    context.getLocation(tagArgument),
                    "Wake Lock tag must not be null");
            return;
        }

        // Get the string value
        Object value = literal.getValue();
        if (!(value instanceof String)) {
            return;
        }

        String tag = (String) value;

        // Check for empty tag
        if (tag.isEmpty()) {
            context.report(
                    ISSUE,
                    node,
                    context.getLocation(tagArgument),
                    "Wake Lock tag must not be empty");
            return;
        }

        // Validate the tag format: should be "package:tag" or "ClassName:tag"
        // The tag must contain a colon separating the identifier from the tag name
        if (!isValidWakeLockTag(tag)) {
            context.report(
                    ISSUE,
                    node,
                    context.getLocation(tagArgument),
                    String.format(
                            "Wake Lock tag `%1$s` does not follow the recommended naming "
                                    + "convention of `<package_or_class_name>:<tag>` "
                                    + "(e.g. `MyClass:MyTag`). Tags should not contain "
                                    + "spaces or special characters.",
                            tag));
        }
    }

    /**
     * Validates that the wake lock tag follows the PowerManager naming conventions.
     *
     * <p>According to the PowerManager documentation, tags should:
     * <ul>
     *   <li>Not be null or empty</li>
     *   <li>Follow the format "package:tag" or "ClassName:tag"</li>
     *   <li>Not contain spaces</li>
     * </ul>
     *
     * @param tag the wake lock tag to validate
     * @return true if the tag is valid, false otherwise
     */
    private static boolean isValidWakeLockTag(@NonNull String tag) {
        // Tag must contain a colon separator
        if (!tag.contains(":")) {
            return false;
        }

        // Tag must not contain spaces
        if (tag.contains(" ")) {
            return false;
        }

        // Split on the colon - must have a non-empty prefix and suffix
        int colonIndex = tag.indexOf(':');
        if (colonIndex == 0 || colonIndex == tag.length() - 1) {
            return false;
        }

        String prefix = tag.substring(0, colonIndex);
        String suffix = tag.substring(colonIndex + 1);

        // Both parts must be non-empty
        if (prefix.isEmpty() || suffix.isEmpty()) {
            return false;
        }

        // Check for invalid characters (allow alphanumeric, dots, underscores, hyphens)
        if (!prefix.matches("[\\w.\\-]+") || !suffix.matches("[\\w.\\-]+")) {
            return false;
        }

        return true;
    }
}