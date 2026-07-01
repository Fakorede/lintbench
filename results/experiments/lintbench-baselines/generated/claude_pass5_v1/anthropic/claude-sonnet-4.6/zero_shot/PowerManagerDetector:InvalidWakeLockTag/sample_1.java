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
 * Detector that checks for invalid WakeLock tags passed to
 * {@code PowerManager#newWakeLock(int, String)}.
 *
 * <p>According to the Android documentation the tag must be of the form
 * {@code "YourPackage:YourFeature"}.
 */
public class PowerManagerDetector extends Detector implements Detector.UastScanner {

    /** The main issue surfaced by this detector. */
    public static final Issue ISSUE = Issue.create(
            "InvalidWakeLockTag",
            "Invalid Wake Lock Tag",
            "Wake Lock tags must follow the naming conventions defined in the " +
                    "`PowerManager` documentation.\n\n" +
                    "The tag must be of the form `\"YourPackage:YourFeature\"` " +
                    "(a colon-separated package name and feature name).\n\n" +
                    "Reference: https://developer.android.com/reference/android/os/PowerManager.html",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(
                    PowerManagerDetector.class,
                    Scope.JAVA_FILE_SCOPE));

    private static final String POWER_MANAGER_CLASS = "android.os.PowerManager";
    private static final String NEW_WAKE_LOCK_METHOD = "newWakeLock";

    /** Constructs a new {@link PowerManagerDetector}. */
    public PowerManagerDetector() {
    }

    // ---- Implements UastScanner ----

    @Nullable
    @Override
    public List<String> getApplicableMethodNames() {
        return Collections.singletonList(NEW_WAKE_LOCK_METHOD);
    }

    @Override
    public void visitMethodCall(@NonNull JavaContext context,
            @NonNull UCallExpression call,
            @NonNull PsiMethod method) {

        // Make sure this is PowerManager#newWakeLock
        if (!context.getEvaluator().isMemberInClass(method, POWER_MANAGER_CLASS)) {
            return;
        }

        // newWakeLock(int levelAndFlags, String tag) — tag is the second argument (index 1)
        List<UExpression> args = call.getValueArguments();
        if (args.size() < 2) {
            return;
        }

        UExpression tagArg = args.get(1);

        // Try to resolve the tag to a compile-time string constant
        Object tagValue = tagArg.evaluate();
        if (tagValue == null) {
            // We can only validate literal / constant values
            return;
        }

        if (!(tagValue instanceof String)) {
            return;
        }

        String tag = (String) tagValue;
        validateTag(context, call, tagArg, tag);
    }

    /**
     * Validates that {@code tag} conforms to the {@code "Package:Tag"} convention
     * required by {@code PowerManager#newWakeLock}.
     *
     * <p>Rules enforced:
     * <ul>
     *   <li>The tag must not be {@code null} or empty.</li>
     *   <li>The tag must contain exactly one colon ({@code :}) separating a non-empty
     *       package portion from a non-empty feature portion.</li>
     *   <li>Neither the package nor the feature portion may be blank (whitespace-only).</li>
     * </ul>
     */
    private static void validateTag(@NonNull JavaContext context,
            @NonNull UCallExpression call,
            @NonNull UExpression tagArg,
            @NonNull String tag) {

        if (tag.isEmpty()) {
            context.report(ISSUE, call, context.getLocation(tagArg),
                    "Wake Lock tag must not be empty; it should be of the form " +
                            "`\"PackageName:FeatureName\"`");
            return;
        }

        int colonIndex = tag.indexOf(':');

        if (colonIndex < 0) {
            // No colon at all
            context.report(ISSUE, call, context.getLocation(tagArg),
                    String.format(
                            "Wake Lock tag `\"%1$s\"` does not look right: it should be of " +
                                    "the form `\"PackageName:FeatureName\"`",
                            tag));
            return;
        }

        // Check for more than one colon (technically allowed by the OS but not by convention)
        // The Android documentation says the format is "YourPackage:YourFeature" which implies
        // exactly one colon.
        if (tag.indexOf(':', colonIndex + 1) >= 0) {
            context.report(ISSUE, call, context.getLocation(tagArg),
                    String.format(
                            "Wake Lock tag `\"%1$s\"` contains more than one colon; " +
                                    "it should be of the form `\"PackageName:FeatureName\"`",
                            tag));
            return;
        }

        String packagePart = tag.substring(0, colonIndex);
        String featurePart = tag.substring(colonIndex + 1);

        if (packagePart.trim().isEmpty()) {
            context.report(ISSUE, call, context.getLocation(tagArg),
                    String.format(
                            "Wake Lock tag `\"%1$s\"` is missing the package name before " +
                                    "the colon; it should be of the form " +
                                    "`\"PackageName:FeatureName\"`",
                            tag));
            return;
        }

        if (featurePart.trim().isEmpty()) {
            context.report(ISSUE, call, context.getLocation(tagArg),
                    String.format(
                            "Wake Lock tag `\"%1$s\"` is missing the feature name after " +
                                    "the colon; it should be of the form " +
                                    "`\"PackageName:FeatureName\"`",
                            tag));
        }
    }
}