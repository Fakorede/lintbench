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

    private static final String NEW_WAKE_LOCK_METHOD = "newWakeLock";

    /**
     * Wake lock tags must be of the form "package_name:tag_name" or simply a short descriptive
     * string. According to the Android documentation, the tag should not be null or empty, and
     * should follow the convention "*:*" (package:tag) or similar valid identifier format.
     *
     * <p>The Android framework enforces that the tag:
     * - Must not be null
     * - Must not be empty
     * - Should follow the format "PackageName:Tag" (colon-separated)
     */
    public static final Issue ISSUE =
            Issue.create(
                            "InvalidWakeLockTag",
                            "Invalid Wake Lock Tag",
                            "Wake Lock tags must follow the naming conventions defined in the "
                                    + "`PowerManager` documentation. The tag should be a non-null, "
                                    + "non-empty string. It is recommended to use the format "
                                    + "`\"PackageName:Tag\"` (e.g., `\"MyApp:MyWakeLock\"`) so that "
                                    + "it is easy to identify the wake lock in logs and bug reports.\n"
                                    + "\n"
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
        return Collections.singletonList(NEW_WAKE_LOCK_METHOD);
    }

    @Override
    public void visitMethodCall(
            @NonNull JavaContext context,
            @NonNull UCallExpression call,
            @NonNull PsiMethod method) {

        // Check that this is a call on PowerManager
        if (!context.getEvaluator().isMemberInClass(method, POWER_MANAGER)) {
            return;
        }

        // newWakeLock(int levelAndFlags, String tag) — tag is the second argument (index 1)
        List<UExpression> arguments = call.getValueArguments();
        if (arguments.size() < 2) {
            return;
        }

        UExpression tagArgument = arguments.get(1);

        // Only validate string literals; skip variables/expressions we can't evaluate statically
        if (!(tagArgument instanceof ULiteralExpression)) {
            return;
        }

        ULiteralExpression literal = (ULiteralExpression) tagArgument;

        // Check for null literal
        if (literal.isNull()) {
            context.report(
                    ISSUE,
                    call,
                    context.getLocation(tagArgument),
                    "Wake Lock tag must not be null");
            return;
        }

        // Retrieve the string value
        Object value = literal.getValue();
        if (!(value instanceof String)) {
            return;
        }

        String tag = (String) value;

        // Check for empty tag
        if (tag.isEmpty()) {
            context.report(
                    ISSUE,
                    call,
                    context.getLocation(tagArgument),
                    "Wake Lock tag must not be empty");
            return;
        }

        // Validate tag format: should be "PackageName:Tag"
        // The Android documentation recommends the format "*:*"
        if (!tag.contains(":")) {
            context.report(
                    ISSUE,
                    call,
                    context.getLocation(tagArgument),
                    "Wake Lock tag `\""
                            + tag
                            + "\"` does not follow the recommended naming convention "
                            + "`\"PackageName:Tag\"` (e.g., `\"MyApp:MyWakeLock\"`). "
                            + "This makes it harder to identify the wake lock in logs.");
            return;
        }

        // Check that neither the package part nor the tag part is empty
        int colonIndex = tag.indexOf(':');
        String packagePart = tag.substring(0, colonIndex);
        String tagPart = tag.substring(colonIndex + 1);

        if (packagePart.isEmpty()) {
            context.report(
                    ISSUE,
                    call,
                    context.getLocation(tagArgument),
                    "Wake Lock tag `\""
                            + tag
                            + "\"` is missing the package name portion before the colon. "
                            + "Use the format `\"PackageName:Tag\"`.");
            return;
        }

        if (tagPart.isEmpty()) {
            context.report(
                    ISSUE,
                    call,
                    context.getLocation(tagArgument),
                    "Wake Lock tag `\""
                            + tag
                            + "\"` is missing the tag portion after the colon. "
                            + "Use the format `\"PackageName:Tag\"`.");
        }
    }
}