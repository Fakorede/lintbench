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
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import java.util.Collections;
import java.util.List;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UExpression;

public class PowerManagerDetector extends Detector implements SourceCodeScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(PowerManagerDetector.class, Scope.JAVA_FILE_SCOPE);

    public static final Issue INVALID_WAKE_LOCK_TAG =
            Issue.create(
                    "InvalidWakeLockTag",
                    "Invalid Wake Lock Tag",
                    "Wake Lock tags must follow the naming conventions defined in the "
                            + "`PowerManager` documentation. The tag should be in the format "
                            + "`packageName:tagName`.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    IMPLEMENTATION);

    @Nullable
    @Override
    public List<String> getApplicableMethodNames() {
        return Collections.singletonList("newWakeLock");
    }

    @Override
    public void visitMethodCall(@NonNull JavaContext context, @NonNull UCallExpression call, @NonNull PsiMethod method) {
        PsiClass containingClass = method.getContainingClass();
        if (containingClass == null || !"android.os.PowerManager".equals(containingClass.getQualifiedName())) {
            return;
        }

        List<UExpression> args = call.getValueArguments();
        if (args.size() < 2) {
            return;
        }

        UExpression tagArg = args.get(1);
        Object value = tagArg.evaluate();
        if (value instanceof String) {
            String tag = (String) value;
            if (!tag.contains(":")) {
                context.report(
                        INVALID_WAKE_LOCK_TAG,
                        tagArg,
                        context.getLocation(tagArg),
                        "Wake lock tag must contain a colon separator (e.g., `packageName:tagName`)");
            }
        }
    }
}