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
 * <p>Wake Lock tags must follow the naming convention {@code <package_name>:<tag>} as required by
 * the Android PowerManager documentation.
 */
public class PowerManagerDetector extends Detector implements Detector.UastScanner {

    /** The main issue reported by this detector. */
    public static final Issue ISSUE =
            Issue.create(
                    "InvalidWakeLockTag",
                    "Invalid Wake Lock Tag",
                    "Wake Lock tags must follow the naming conventions defined in the "
                            + "`PowerManager` documentation.\n\n"
                            + "Specifically, a tag must be of the form "
                            + "`\"<package_name>:<tag>\"` (e.g. `\"com.example.app:MyWakeLock\"`).\n\n"
                            + "Tags that do not contain a colon, or that use an empty package or "
                            + "tag component, may be rejected on newer versions of Android.",
                    Category.CORRECTNESS,
                    6,
                    Severity.WARNING,
                    new Implementation(PowerManagerDetector.class, Scope.JAVA_FILE_SCOPE))
                    .addMoreInfo(
                            "https://developer.android.com/reference/android/os/PowerManager.html");

    private static final String POWER_MANAGER_CLASS = "android.os.PowerManager";
    private static final String NEW_WAKE_LOCK_METHOD = "newWakeLock";

    /** Constructs a new {@link PowerManagerDetector}. */
    public PowerManagerDetector() {}

    // ---- Implements UastScanner ----

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

        // Make sure this is PowerManager#newWakeLock
        if (!context.getEvaluator().isMemberInClass(method, POWER_MANAGER_CLASS)) {
            return;
        }

        // newWakeLock(int levelAndFlags, String tag) — tag is the second argument (index 1)
        List<UExpression> arguments = call.getValueArguments();
        if (arguments.size() < 2) {
            return;
        }

        UExpression tagArgument = arguments.get(1);

        // We can only validate compile-time string literals
        if (!(tagArgument instanceof ULiteralExpression)) {
            return;
        }

        ULiteralExpression literal = (ULiteralExpression) tagArgument;
        if (!literal.isString()) {
            return;
        }

        String tag = (String) UastLiteralUtils.getValueIfStringLiteral(literal);
        if (tag == null) {
            return;
        }

        validateTag(context, call, tag);
    }

    /**
     * Validates the wake lock tag string and reports issues if the tag does not conform to the
     * required {@code <package>:<tag>} format.
     */
    private static void validateTag(
            @NonNull JavaContext context,
            @NonNull UCallExpression call,
            @NonNull String tag) {

        // The tag must contain a colon separator
        int colonIndex = tag.indexOf(':');
        if (colonIndex < 0) {
            context.report(
                    ISSUE,
                    call,
                    context.getLocation(call.getValueArguments().get(1)),
                    "Wake Lock tag `\""
                            + tag
                            + "\"` does not look right: it should be of the form "
                            + "`\"<package_name>:<tag>\"`, e.g. `\"com.example.app:MyWakeLock\"`");
            return;
        }

        String packagePart = tag.substring(0, colonIndex);
        String tagPart = tag.substring(colonIndex + 1);

        if (packagePart.isEmpty()) {
            context.report(
                    ISSUE,
                    call,
                    context.getLocation(call.getValueArguments().get(1)),
                    "Wake Lock tag `\""
                            + tag
                            + "\"` is missing the package name component before the colon; "
                            + "expected `\"<package_name>:<tag>\"`");
            return;
        }

        if (tagPart.isEmpty()) {
            context.report(
                    ISSUE,
                    call,
                    context.getLocation(call.getValueArguments().get(1)),
                    "Wake Lock tag `\""
                            + tag
                            + "\"` is missing the tag component after the colon; "
                            + "expected `\"<package_name>:<tag>\"`");
            return;
        }

        // Check that the package component looks like a valid Java package name
        // (dot-separated identifiers consisting of letters, digits, and underscores)
        if (!isValidPackageName(packagePart)) {
            context.report(
                    ISSUE,
                    call,
                    context.getLocation(call.getValueArguments().get(1)),
                    "Wake Lock tag `\""
                            + tag
                            + "\"`: the package name component `\""
                            + packagePart
                            + "\"` does not look like a valid package name");
        }
    }

    /**
     * Returns {@code true} if {@code name} looks like a valid Java package name, i.e. one or more
     * dot-separated identifiers where each identifier starts with a letter or underscore and
     * contains only letters, digits, and underscores.
     */
    private static boolean isValidPackageName(@NonNull String name) {
        if (name.isEmpty()) {
            return false;
        }
        String[] segments = name.split("\\.", -1);
        for (String segment : segments) {
            if (segment.isEmpty()) {
                return false;
            }
            char first = segment.charAt(0);
            if (!Character.isLetter(first) && first != '_') {
                return false;
            }
            for (int i = 1; i < segment.length(); i++) {
                char c = segment.charAt(i);
                if (!Character.isLetterOrDigit(c) && c != '_') {
                    return false;
                }
            }
        }
        return true;
    }
}