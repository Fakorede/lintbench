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

public class PowerManagerDetector extends Detector implements SourceCodeScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(PowerManagerDetector.class, Scope.JAVA_FILE_SCOPE);

    public static final Issue INVALID_TAG =
            Issue.create(
                            "InvalidWakeLockTag",
                            "Invalid Wake Lock Tag",
                            "Wake Lock tags must follow the naming conventions defined in the "
                                    + "`PowerManager` documentation. They must not be empty, "
                                    + "must not contain spaces, and must not exceed 127 characters.",
                            Category.CORRECTNESS,
                            5,
                            Severity.ERROR,
                            IMPLEMENTATION)
                    .setAndroidSpecific(true);

    public PowerManagerDetector() {}

    @Nullable
    @Override
    public List<String> getApplicableMethodNames() {
        return Collections.singletonList("newWakeLock");
    }

    @Override
    public void visitMethodCall(
            @NonNull JavaContext context,
            @NonNull UCallExpression node,
            @NonNull PsiMethod method) {
        if (!context.getEvaluator().isMemberInClass(method, "android.os.PowerManager")) {
            return;
        }

        List<UExpression> args = node.getValueArguments();
        if (args.size() < 2) {
            return;
        }

        UExpression tagArg = args.get(1);
        Object evaluated = tagArg.evaluate();
        if (evaluated instanceof String) {
            String tag = (String) evaluated;
            if (tag.isEmpty()) {
                context.report(
                        INVALID_TAG,
                        tagArg,
                        context.getLocation(tagArg),
                        "Wake lock tag cannot be empty");
            } else if (tag.contains(" ")) {
                context.report(
                        INVALID_TAG,
                        tagArg,
                        context.getLocation(tagArg),
                        "Wake lock tag must not contain spaces");
            } else if (tag.length() > 127) {
                context.report(
                        INVALID_TAG,
                        tagArg,
                        context.getLocation(tagArg),
                        "Wake lock tag must not exceed 127 characters");
            }
        }
    }
}