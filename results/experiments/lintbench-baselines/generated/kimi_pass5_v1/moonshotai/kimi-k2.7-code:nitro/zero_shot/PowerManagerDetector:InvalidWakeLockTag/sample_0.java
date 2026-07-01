package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.ConstantEvaluator;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.intellij.psi.PsiMethod;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UExpression;

import java.util.Collections;
import java.util.List;

public class PowerManagerDetector extends Detector implements Detector.SourceCodeScanner {

    private static final String ANDROID_OS_POWER_MANAGER = "android.os.PowerManager";
    private static final String NEW_WAKE_LOCK = "newWakeLock";
    private static final int MAX_TAG_LENGTH = 100;

    public static final Issue ISSUE = Issue.create(
            "InvalidWakeLockTag",
            "Invalid Wake Lock Tag",
            "Wake lock tags are shown in the battery stats log and must follow the naming conventions "
                    + "described in the PowerManager documentation: they must be no more than 100 characters "
                    + "and must not contain newline characters.",
            Category.CORRECTNESS,
            5,
            Severity.WARNING,
            new Implementation(PowerManagerDetector.class, Scope.JAVA_FILE_SCOPE)
    );

    @Override
    @Nullable
    public List<String> getApplicableMethodNames() {
        return Collections.singletonList(NEW_WAKE_LOCK);
    }

    @Override
    public void visitMethodCall(@NonNull JavaContext context, @NonNull UCallExpression call, @NonNull PsiMethod method) {
        if (!context.getEvaluator().isMemberInClass(method, ANDROID_OS_POWER_MANAGER)) {
            return;
        }

        List<UExpression> arguments = call.getValueArguments();
        if (arguments.size() != 2) {
            return;
        }

        UExpression tagArgument = arguments.get(1);
        Object value = ConstantEvaluator.evaluate(context, tagArgument);
        if (!(value instanceof String)) {
            return;
        }

        String tag = (String) value;
        if (tag.length() > MAX_TAG_LENGTH) {
            context.report(ISSUE, tagArgument, context.getLocation(tagArgument),
                    "Wake lock tag must be no longer than 100 characters");
        } else if (tag.indexOf('\n') >= 0 || tag.indexOf('\r') >= 0) {
            context.report(ISSUE, tagArgument, context.getLocation(tagArgument),
                    "Wake lock tag must not contain newline characters");
        }
    }
}