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

public class PowerManagerDetector extends Detector implements SourceCodeScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(PowerManagerDetector.class, Scope.JAVA_FILE_SCOPE);

    private static final String POWER_MANAGER = "android.os.PowerManager";
    private static final String NEW_WAKE_LOCK = "newWakeLock";

    public static final Issue INVALID_WAKE_LOCK_TAG =
            Issue.create(
                            "InvalidWakeLockTag",
                            "Invalid Wake Lock Tag",
                            "Wake Lock tags must follow the naming conventions defined in the "
                                    + "`PowerManager` documentation. The tag must not be null or "
                                    + "empty, and must follow the format `*TagName*` or "
                                    + "`*PackageName*:*TagName*`.",
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
        if (arguments.size() < 2) {
            return;
        }

        UExpression tagArgument = arguments.get(1);
        if (!(tagArgument instanceof ULiteralExpression)) {
            // Can't evaluate non-literal tags statically
            return;
        }

        ULiteralExpression literal = (ULiteralExpression) tagArgument;
        Object value = literal.getValue();

        if (value == null) {
            context.report(
                    INVALID_WAKE_LOCK_TAG,
                    call,
                    context.getLocation(tagArgument),
                    "Wake Lock tag must not be null");
            return;
        }

        if (!(value instanceof String)) {
            return;
        }

        String tag = (String) value;

        if (tag.isEmpty()) {
            context.report(
                    INVALID_WAKE_LOCK_TAG,
                    call,
                    context.getLocation(tagArgument),
                    "Wake Lock tag must not be empty");
            return;
        }

        // According to PowerManager documentation, the tag should be of the form
        // "*TagName*" or "*PackageName*:*TagName*"
        // Validate that the tag is well-formed: no whitespace and follows naming conventions
        if (!isValidWakeLockTag(tag)) {
            context.report(
                    INVALID_WAKE_LOCK_TAG,
                    call,
                    context.getLocation(tagArgument),
                    "Wake Lock tag `"
                            + tag
                            + "` is not a valid tag. Tags must not contain whitespace "
                            + "and should be in the format `*TagName*` or "
                            + "`*PackageName*:*TagName*`.");
        }
    }

    private static boolean isValidWakeLockTag(@NonNull String tag) {
        // Tags must not contain whitespace characters
        for (int i = 0; i < tag.length(); i++) {
            if (Character.isWhitespace(tag.charAt(i))) {
                return false;
            }
        }

        // Tags should match the pattern: optional "package:" prefix followed by a tag name
        // Both parts must be non-empty
        int colonIndex = tag.indexOf(':');
        if (colonIndex == 0) {
            // Colon at the start means empty package name
            return false;
        }
        if (colonIndex == tag.length() - 1) {
            // Colon at the end means empty tag name
            return false;
        }

        return true;
    }
}