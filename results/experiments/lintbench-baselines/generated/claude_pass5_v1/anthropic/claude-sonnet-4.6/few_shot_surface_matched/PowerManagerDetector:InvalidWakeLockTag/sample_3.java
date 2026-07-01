package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
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

    private static final String POWER_MANAGER = "android.os.PowerManager";
    private static final String NEW_WAKE_LOCK = "newWakeLock";

    /**
     * Wake lock tags must be of the form "package:reason" or "*tag*" (the latter is reserved for
     * system use, but we still validate its format). According to the PowerManager documentation,
     * the tag should be of the form "<package_name>:<reason>".
     */
    private static final String TAG_PATTERN = "[a-zA-Z0-9_.\\-]+:[a-zA-Z0-9_.\\-]+";

    public static final Issue ISSUE =
            Issue.create(
                            "InvalidWakeLockTag",
                            "Invalid Wake Lock Tag",
                            "Wake Lock tags must follow the naming conventions defined in the "
                                    + "`PowerManager` documentation. The tag should be of the form "
                                    + "`\"<package_name>:<reason>\"` (e.g. `\"MyApp:MyWakeLock\"`). "
                                    + "Using an invalid tag may cause issues with wake lock tracking "
                                    + "and debugging.\n\n"
                                    + "See https://developer.android.com/reference/android/os/PowerManager.html",
                            Category.CORRECTNESS,
                            6,
                            Severity.ERROR,
                            IMPLEMENTATION)
                    .setAndroidSpecific(true);

    public PowerManagerDetector() {}

    @Nullable
    @Override
    public List<String> getApplicableMethodNames() {
        return Collections.singletonList(NEW_WAKE_LOCK);
    }

    @Override
    public void visitMethodCall(
            @NonNull JavaContext context,
            @NonNull UCallExpression call,
            @NonNull PsiMethod method) {
        if (!context.getEvaluator().isMemberInClass(method, POWER_MANAGER)) {
            return;
        }

        List<UExpression> arguments = call.getValueArguments();
        // newWakeLock(int levelAndFlags, String tag)
        if (arguments.size() < 2) {
            return;
        }

        UExpression tagArgument = arguments.get(1);
        if (!(tagArgument instanceof ULiteralExpression)) {
            // We can only validate string literals at compile time
            return;
        }

        Object value = UastLiteralUtils.getValueIfStringLiteral(tagArgument);
        if (!(value instanceof String)) {
            return;
        }

        String tag = (String) value;

        if (!isValidWakeLockTag(tag)) {
            context.report(
                    ISSUE,
                    call,
                    context.getLocation(tagArgument),
                    "Invalid wake lock tag `\""
                            + tag
                            + "\"`: wake lock tags must be in the format "
                            + "`\"<package_name>:<reason>\"` (e.g. `\"MyApp:MyWakeLock\"`)");
        }
    }

    /**
     * Validates a wake lock tag string.
     *
     * <p>According to the PowerManager documentation, a tag must be of the form
     * {@code "<package_name>:<reason>"} where both parts consist of alphanumeric characters,
     * underscores, dots, or hyphens. Tags starting and ending with '*' are reserved for the
     * system.
     *
     * @param tag the tag string to validate
     * @return true if the tag is valid, false otherwise
     */
    private static boolean isValidWakeLockTag(@NonNull String tag) {
        if (tag.isEmpty()) {
            return false;
        }

        // System-reserved tags of the form *tag* are allowed (used internally by Android)
        if (tag.startsWith("*") && tag.endsWith("*") && tag.length() > 2) {
            return true;
        }

        // Must contain exactly one colon separating package name and reason
        int colonIndex = tag.indexOf(':');
        if (colonIndex <= 0 || colonIndex == tag.length() - 1) {
            return false;
        }

        // Ensure there is only one colon
        if (tag.indexOf(':', colonIndex + 1) != -1) {
            return false;
        }

        String packagePart = tag.substring(0, colonIndex);
        String reasonPart = tag.substring(colonIndex + 1);

        return isValidTagComponent(packagePart) && isValidTagComponent(reasonPart);
    }

    /**
     * Returns true if the given string is a valid tag component (package name or reason).
     * Valid components consist of alphanumeric characters, underscores, dots, or hyphens.
     */
    private static boolean isValidTagComponent(@NonNull String component) {
        if (component.isEmpty()) {
            return false;
        }
        for (int i = 0; i < component.length(); i++) {
            char c = component.charAt(i);
            if (!Character.isLetterOrDigit(c) && c != '_' && c != '.' && c != '-') {
                return false;
            }
        }
        return true;
    }
}