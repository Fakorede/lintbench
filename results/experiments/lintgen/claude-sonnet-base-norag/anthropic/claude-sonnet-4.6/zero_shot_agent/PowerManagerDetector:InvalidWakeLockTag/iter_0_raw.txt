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
 * {@code "YourPackage:YourFeature"} – i.e. it must contain a colon and must
 * not be blank.
 */
public class PowerManagerDetector extends Detector implements Detector.UastScanner {

    /** The main issue reported by this detector. */
    public static final Issue ISSUE = Issue.create(
            "InvalidWakeLockTag",
            "Invalid Wake Lock Tag",
            "Wake Lock tags must follow the naming conventions defined in the " +
            "`PowerManager` documentation.\n\n" +
            "The tag must be of the form `\"YourPackage:YourFeature\"` – it must " +
            "contain a colon (`:`) separating a package-like prefix from a feature " +
            "name, and neither part may be empty.\n\n" +
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

        // newWakeLock(int levelAndFlags, String tag) – tag is the second argument
        List<UExpression> arguments = call.getValueArguments();
        if (arguments.size() < 2) {
            return;
        }

        UExpression tagArgument = arguments.get(1);

        // We can only validate string literals at compile time
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

        validateTag(context, call, tagArgument, tag);
    }

    /**
     * Validates the wake-lock tag string and reports issues when the tag does
     * not conform to the {@code "Package:Tag"} convention.
     */
    private static void validateTag(@NonNull JavaContext context,
                                    @NonNull UCallExpression call,
                                    @NonNull UExpression tagExpression,
                                    @NonNull String tag) {

        // Tag must not be empty
        if (tag.isEmpty()) {
            context.report(
                    ISSUE,
                    call,
                    context.getLocation(tagExpression),
                    "Wake Lock tag must not be empty");
            return;
        }

        // Tag must contain a colon
        int colonIndex = tag.indexOf(':');
        if (colonIndex < 0) {
            context.report(
                    ISSUE,
                    call,
                    context.getLocation(tagExpression),
                    "Wake Lock tag must be in the format `\"PackageName:FeatureName\"` " +
                    "(missing `:` separator)");
            return;
        }

        // The part before the colon (package-like prefix) must not be empty
        if (colonIndex == 0) {
            context.report(
                    ISSUE,
                    call,
                    context.getLocation(tagExpression),
                    "Wake Lock tag must be in the format `\"PackageName:FeatureName\"` " +
                    "(the package prefix before `:` is empty)");
            return;
        }

        // The part after the colon (feature name) must not be empty
        if (colonIndex == tag.length() - 1) {
            context.report(
                    ISSUE,
                    call,
                    context.getLocation(tagExpression),
                    "Wake Lock tag must be in the format `\"PackageName:FeatureName\"` " +
                    "(the feature name after `:` is empty)");
        }
    }
}