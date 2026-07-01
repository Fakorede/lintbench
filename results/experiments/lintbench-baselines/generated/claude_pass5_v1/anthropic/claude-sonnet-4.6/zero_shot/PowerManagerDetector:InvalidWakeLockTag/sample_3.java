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
 * Detector for invalid WakeLock tags passed to {@code PowerManager.newWakeLock()}.
 *
 * <p>Wake Lock tags must follow the naming convention {@code "YourPackage:YourTag"} as described in
 * https://developer.android.com/reference/android/os/PowerManager.html
 */
public class PowerManagerDetector extends Detector implements Detector.UastScanner {

    /** The main issue reported by this detector */
    public static final Issue ISSUE = Issue.create(
            "InvalidWakeLockTag",
            "Invalid Wake Lock Tag",
            "Wake Lock tags must follow the naming conventions defined in the `PowerManager` " +
            "documentation.\n\n" +
            "Specifically, a tag must be of the form `\"YourPackage:YourTag\"`, where " +
            "`YourPackage` is a valid Java package name and `YourTag` is a non-empty string.\n\n" +
            "Reference: https://developer.android.com/reference/android/os/PowerManager.html",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(PowerManagerDetector.class, Scope.JAVA_FILE_SCOPE));

    private static final String POWER_MANAGER_CLASS = "android.os.PowerManager";
    private static final String NEW_WAKE_LOCK_METHOD = "newWakeLock";

    /** Constructs a new {@link PowerManagerDetector} */
    public PowerManagerDetector() {
    }

    // ---- Implements UastScanner ----

    @Override
    @Nullable
    public List<String> getApplicableMethodNames() {
        return Collections.singletonList(NEW_WAKE_LOCK_METHOD);
    }

    @Override
    public void visitMethodCall(
            @NonNull JavaContext context,
            @NonNull UCallExpression call,
            @NonNull PsiMethod method) {

        // Make sure this is android.os.PowerManager#newWakeLock
        if (!context.getEvaluator().isMemberInClass(method, POWER_MANAGER_CLASS)) {
            return;
        }

        // newWakeLock(int levelAndFlags, String tag)
        List<UExpression> arguments = call.getValueArguments();
        if (arguments.size() < 2) {
            return;
        }

        UExpression tagArg = arguments.get(1);

        // Try to resolve the tag to a compile-time constant string
        Object tagValue = tagArg.evaluate();
        if (tagValue == null) {
            // Cannot evaluate statically – check if it is a literal so we can still warn
            if (tagArg instanceof ULiteralExpression) {
                tagValue = UastLiteralUtils.getPsiLiteral((ULiteralExpression) tagArg);
            }
            if (tagValue == null) {
                // Not a constant we can check
                return;
            }
        }

        if (!(tagValue instanceof String)) {
            return;
        }

        String tag = (String) tagValue;
        checkTag(context, call, tagArg, tag);
    }

    /**
     * Validates the wake lock tag and reports issues when the tag does not conform to the
     * required {@code "package:tag"} format.
     */
    private static void checkTag(
            @NonNull JavaContext context,
            @NonNull UCallExpression call,
            @NonNull UExpression tagArg,
            @NonNull String tag) {

        if (tag.isEmpty()) {
            context.report(
                    ISSUE,
                    call,
                    context.getLocation(tagArg),
                    "Tag is empty; wake lock tags must be of the form " +
                    "`\"YourPackage:YourTag\"`");
            return;
        }

        int colonIndex = tag.indexOf(':');
        if (colonIndex < 0) {
            context.report(
                    ISSUE,
                    call,
                    context.getLocation(tagArg),
                    "Wake lock tag `\"" + tag + "\"` does not look right; it should be of " +
                    "the form `\"YourPackage:YourTag\"`, e.g. `\"" +
                    suggestFix(tag) + "`");
            return;
        }

        // There should be exactly one colon
        if (tag.indexOf(':', colonIndex + 1) >= 0) {
            context.report(
                    ISSUE,
                    call,
                    context.getLocation(tagArg),
                    "Wake lock tag `\"" + tag + "\"` contains more than one colon; " +
                    "it should be of the form `\"YourPackage:YourTag\"`");
            return;
        }

        String packagePart = tag.substring(0, colonIndex);
        String tagPart = tag.substring(colonIndex + 1);

        if (packagePart.isEmpty()) {
            context.report(
                    ISSUE,
                    call,
                    context.getLocation(tagArg),
                    "Wake lock tag `\"" + tag + "\"` is missing a package prefix before " +
                    "the colon; it should be of the form `\"YourPackage:YourTag\"`");
            return;
        }

        if (tagPart.isEmpty()) {
            context.report(
                    ISSUE,
                    call,
                    context.getLocation(tagArg),
                    "Wake lock tag `\"" + tag + "\"` is missing a tag name after " +
                    "the colon; it should be of the form `\"YourPackage:YourTag\"`");
            return;
        }

        // Validate the package portion – must be a valid Java package name
        if (!isValidPackageName(packagePart)) {
            context.report(
                    ISSUE,
                    call,
                    context.getLocation(tagArg),
                    "Wake lock tag `\"" + tag + "\"` has an invalid package portion " +
                    "`\"" + packagePart + "\"`; it should be a valid Java package name " +
                    "(e.g. `\"com.example.app:YourTag\"`)");
        }
    }

    /**
     * Returns {@code true} if {@code name} is a syntactically valid Java package name, i.e. one
     * or more dot-separated Java identifiers.
     */
    private static boolean isValidPackageName(@NonNull String name) {
        if (name.isEmpty()) {
            return false;
        }
        String[] segments = name.split("\\.", -1);
        for (String segment : segments) {
            if (!isValidJavaIdentifier(segment)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns {@code true} if {@code name} is a valid Java identifier (non-empty, starts with a
     * letter or underscore, followed by letters, digits, or underscores).
     */
    private static boolean isValidJavaIdentifier(@NonNull String name) {
        if (name.isEmpty()) {
            return false;
        }
        if (!Character.isJavaIdentifierStart(name.charAt(0))) {
            return false;
        }
        for (int i = 1; i < name.length(); i++) {
            if (!Character.isJavaIdentifierPart(name.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Produces a simple suggested fix string for display in the error message when the tag is
     * missing a colon.
     */
    private static String suggestFix(@NonNull String tag) {
        // Sanitise the tag to form a simple suggestion
        String sanitised = tag.replaceAll("[^a-zA-Z0-9_]", "");
        if (sanitised.isEmpty()) {
            sanitised = "MyTag";
        }
        return "com.example.app:" + sanitised + "\"";
    }
}