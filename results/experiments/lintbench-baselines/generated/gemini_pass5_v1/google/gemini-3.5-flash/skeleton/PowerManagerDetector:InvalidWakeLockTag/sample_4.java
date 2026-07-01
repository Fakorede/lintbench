package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.ConstantEvaluator;
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

public class PowerManagerDetector extends Detector implements SourceCodeScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(PowerManagerDetector.class, Scope.JAVA_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "InvalidWakeLockTag",
                    "Invalid Wake Lock Tag",
                    "Wake lock tags should be prefixed with the application package name "
                            + "or a unique identifier, e.g. `MyApp:MyTag` to avoid collisions. "
                            + "Also, the tag must not be longer than 127 characters, and should "
                            + "contain only letters, numbers, and the characters `.`, `_`, `-`, `:`, and `/`.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    IMPLEMENTATION);

    @Override
    public void visitMethodCall(
            @NonNull JavaContext context,
            @NonNull UCallExpression node,
            @NonNull PsiMethod method) {
        if (!context.getEvaluator().isMemberInClass(method, "android.os.PowerManager")) {
            return;
        }
        List<UExpression> arguments = node.getValueArguments();
        if (arguments.size() < 2) {
            return;
        }
        UExpression tagArgument = arguments.get(1);
        Object value = ConstantEvaluator.evaluate(context, tagArgument);
        if (value instanceof String) {
            String tag = (String) value;
            validateTag(context, tagArgument, tag);
        }
    }

    private static void validateTag(
            @NonNull JavaContext context,
            @NonNull UExpression argument,
            @NonNull String tag) {
        if (tag.startsWith("android:")) {
            context.report(
                    ISSUE,
                    argument,
                    context.getLocation(argument),
                    "Avoid using the `android:` prefix for wake lock tags; that is reserved for system code");
            return;
        }

        int colon = tag.indexOf(':');
        if (colon == -1) {
            context.report(
                    ISSUE,
                    argument,
                    context.getLocation(argument),
                    "Wake lock tags should be prefixed with the application package name "
                            + "or a unique identifier, e.g. `MyApp:MyTag` to avoid collisions");
            return;
        }

        if (tag.length() > 127) {
            context.report(
                    ISSUE,
                    argument,
                    context.getLocation(argument),
                    "Wake lock tag is too long; must be 127 characters or less");
            return;
        }

        for (int i = 0; i < tag.length(); i++) {
            char c = tag.charAt(i);
            if (!Character.isLetterOrDigit(c) && c != '.' && c != '_' && c != '-' && c != ':' && c != '/') {
                context.report(
                        ISSUE,
                        argument,
                        context.getLocation(argument),
                        "Wake lock tags should contain only letters, numbers, and '.', '_', '-', ':', and '/'");
                return;
            }
        }
    }

    @Override
    public List<String> getApplicableMethodNames() {
        return Collections.singletonList("newWakeLock");
    }
}