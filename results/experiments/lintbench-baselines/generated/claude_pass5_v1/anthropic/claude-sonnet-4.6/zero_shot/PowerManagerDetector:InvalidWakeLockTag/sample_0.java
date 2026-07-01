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
import com.intellij.psi.PsiMethod;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.ULiteralExpression;
import org.jetbrains.uast.UastLiteralUtils;

import java.util.Collections;
import java.util.List;

/**
 * Detector for invalid WakeLock tags passed to PowerManager.newWakeLock().
 *
 * <p>Wake Lock tags must follow the naming convention "YourPackage:YourTag" as defined in the
 * PowerManager documentation.
 */
public class PowerManagerDetector extends Detector implements Detector.UastScanner {

    /** The main issue this detector reports */
    public static final Issue ISSUE =
            Issue.create(
                    "InvalidWakeLockTag",
                    "Invalid Wake Lock Tag",
                    "Wake Lock tags must follow the naming convention defined in the `PowerManager` "
                            + "documentation. Specifically, the tag must be of the form "
                            + "`\"YourPackage:YourTag\"`, where the package name portion matches "
                            + "your app's package name. Using an invalid tag may cause warnings "
                            + "or errors on newer versions of Android.\n"
                            + "\n"
                            + "See https://developer.android.com/reference/android/os/PowerManager.html",
                    Category.CORRECTNESS,
                    6,
                    Severity.WARNING,
                    new Implementation(PowerManagerDetector.class, Scope.JAVA_FILE_SCOPE));

    private static final String POWER_MANAGER_CLASS = "android.os.PowerManager";
    private static final String NEW_WAKE_LOCK_METHOD = "newWakeLock";

    /** Constructs a new {@link PowerManagerDetector} */
    public PowerManagerDetector() {}

    // ---- Implements UastScanner ----

    @Override
    public List<String> getApplicableMethodNames() {
        return Collections.singletonList(NEW_WAKE_LOCK_METHOD);
    }

    @Override
    public void visitMethodCall(
            @NonNull JavaContext context,
            @NonNull UCallExpression call,
            @NonNull PsiMethod method) {

        // Make sure this is PowerManager.newWakeLock
        if (!context.getEvaluator().isMemberInClass(method, POWER_MANAGER_CLASS)) {
            return;
        }

        // newWakeLock(int levelAndFlags, String tag)
        List<UExpression> arguments = call.getValueArguments();
        if (arguments.size() < 2) {
            return;
        }

        UExpression tagArgument = arguments.get(1);

        // We can only validate string literals
        if (!(tagArgument instanceof ULiteralExpression)) {
            // Try to evaluate constant expressions
            Object value = tagArgument.evaluate();
            if (value instanceof String) {
                validateTag(context, tagArgument, (String) value);
            }
            // If we can't evaluate, we skip the check
            return;
        }

        ULiteralExpression literal = (ULiteralExpression) tagArgument;
        Object value = literal.getValue();
        if (value instanceof String) {
            validateTag(context, tagArgument, (String) value);
        }
    }

    /**
     * Validates the wake lock tag and reports an issue if it does not conform to the required
     * naming convention ("PackageName:Tag").
     */
    private void validateTag(
            @NonNull JavaContext context,
            @NonNull UExpression tagExpression,
            @NonNull String tag) {

        if (!isValidWakeLockTag(tag)) {
            context.report(
                    ISSUE,
                    tagExpression,
                    context.getLocation(tagExpression),
                    "Wake lock tag `\""
                            + tag
                            + "\"` does not look right: it should follow the "
                            + "`\"PackageName:Tag\"` convention (colon separating package name "
                            + "from tag label)");
        }
    }

    /**
     * Returns {@code true} if the given tag is a valid WakeLock tag.
     *
     * <p>A valid tag must:
     *
     * <ul>
     *   <li>Not be null or empty.
     *   <li>Contain a colon (":") that separates the package-name portion from the tag label.
     *   <li>Have a non-empty package-name portion before the colon.
     *   <li>Have a non-empty tag label after the colon.
     * </ul>
     */
    private static boolean isValidWakeLockTag(@Nullable String tag) {
        if (tag == null || tag.isEmpty()) {
            return false;
        }

        int colonIndex = tag.indexOf(':');
        if (colonIndex <= 0) {
            // No colon, or colon is the very first character (empty package portion)
            return false;
        }

        // Tag label portion (after the colon) must be non-empty
        if (colonIndex >= tag.length() - 1) {
            return false;
        }

        String packagePortion = tag.substring(0, colonIndex);
        String tagPortion = tag.substring(colonIndex + 1);

        // Both parts must be non-empty (already guaranteed by index checks above,
        // but be explicit for clarity)
        if (packagePortion.isEmpty() || tagPortion.isEmpty()) {
            return false;
        }

        return true;
    }
}