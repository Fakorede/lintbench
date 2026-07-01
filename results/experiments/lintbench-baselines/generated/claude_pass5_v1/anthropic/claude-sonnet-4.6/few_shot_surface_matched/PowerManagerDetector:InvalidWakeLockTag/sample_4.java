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
     * Wake lock tags must be of the form "package:tag" where package is a valid Java package name
     * and tag is an arbitrary string. The tag must not be null or empty, and must contain a colon.
     */
    public static final Issue ISSUE =
            Issue.create(
                            "InvalidWakeLockTag",
                            "Invalid Wake Lock Tag",
                            "Wake Lock tags must follow the naming conventions defined in the "
                                    + "`PowerManager` documentation. The tag must be of the form "
                                    + "`\"PackageName:Tag\"` (e.g. `\"com.example.app:MyWakeLock\"`). "
                                    + "The tag must not be null, empty, or missing the required "
                                    + "colon-separated package prefix. See "
                                    + "https://developer.android.com/reference/android/os/PowerManager.html "
                                    + "for details.",
                            Category.CORRECTNESS,
                            7,
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

        // Make sure this is PowerManager#newWakeLock
        if (!context.getEvaluator().isMemberInClass(method, POWER_MANAGER)) {
            return;
        }

        // newWakeLock(int levelAndFlags, String tag)
        List<UExpression> arguments = call.getValueArguments();
        if (arguments.size() < 2) {
            return;
        }

        UExpression tagArgument = arguments.get(1);

        // Only validate string literals; skip variables/expressions we can't resolve statically
        if (!(tagArgument instanceof ULiteralExpression)) {
            return;
        }

        ULiteralExpression literal = (ULiteralExpression) tagArgument;
        if (!literal.isString()) {
            return;
        }

        Object value = UastLiteralUtils.getValueIfStringLiteral(literal);
        if (value == null) {
            return;
        }

        String tag = value.toString();

        if (tag.isEmpty()) {
            context.report(
                    ISSUE,
                    call,
                    context.getLocation(tagArgument),
                    "Wake Lock tag must not be empty; it should be of the form "
                            + "`\"PackageName:Tag\"`");
            return;
        }

        if (!tag.contains(":")) {
            context.report(
                    ISSUE,
                    call,
                    context.getLocation(tagArgument),
                    "Wake Lock tag `\""
                            + tag
                            + "\"` does not look like a valid tag; it should be of the form "
                            + "`\"PackageName:Tag\"`, e.g. `\"com.example.app:MyWakeLock\"`");
            return;
        }

        int colonIndex = tag.indexOf(':');
        String packagePart = tag.substring(0, colonIndex);
        if (packagePart.isEmpty()) {
            context.report(
                    ISSUE,
                    call,
                    context.getLocation(tagArgument),
                    "Wake Lock tag `\""
                            + tag
                            + "\"` is missing a package name prefix before the colon; "
                            + "it should be of the form `\"PackageName:Tag\"`");
            return;
        }

        // Validate that the package part looks like a valid Java package / class name
        if (!isValidPackageName(packagePart)) {
            context.report(
                    ISSUE,
                    call,
                    context.getLocation(tagArgument),
                    "Wake Lock tag `\""
                            + tag
                            + "\"` has an invalid package name prefix `\""
                            + packagePart
                            + "\"`; the prefix before the colon should be a valid Java "
                            + "package or class name");
        }
    }

    /**
     * Returns true if the given string looks like a valid Java package name (dot-separated
     * identifiers). We allow single identifiers (no dots) as well, since some system wake locks
     * use a simple class name rather than a fully-qualified package name.
     */
    private static boolean isValidPackageName(@NonNull String name) {
        if (name.isEmpty()) {
            return false;
        }
        String[] parts = name.split("\\.", -1);
        for (String part : parts) {
            if (!isValidJavaIdentifier(part)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns true if the given string is a valid Java identifier (starts with a letter or
     * underscore, followed by letters, digits, or underscores).
     */
    private static boolean isValidJavaIdentifier(@NonNull String identifier) {
        if (identifier.isEmpty()) {
            return false;
        }
        char first = identifier.charAt(0);
        if (!Character.isLetter(first) && first != '_') {
            return false;
        }
        for (int i = 1; i < identifier.length(); i++) {
            char c = identifier.charAt(i);
            if (!Character.isLetterOrDigit(c) && c != '_') {
                return false;
            }
        }
        return true;
    }
}