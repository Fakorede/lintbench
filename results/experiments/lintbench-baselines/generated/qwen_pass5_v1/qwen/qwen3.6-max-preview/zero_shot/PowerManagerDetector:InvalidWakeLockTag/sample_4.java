package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.JavaEvaluator;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.intellij.psi.PsiMethod;
import org.jetbrains.annotations.NonNull;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UExpression;

import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

public class PowerManagerDetector extends Detector implements Detector.UastScanner {

    private static final String ISSUE_ID = "InvalidWakeLockTag";
    private static final String DESCRIPTION = "Invalid Wake Lock Tag";
    private static final String EXPLANATION = "Wake Lock tags must follow the naming conventions defined in the PowerManager documentation. "
            + "The tag should be in the format `PackageName:WakeLockTag` (e.g., `com.example.myapp:MyWakeLock`).";

    private static final Pattern VALID_TAG_PATTERN = Pattern.compile("^[^:]+:.+$");

    public static final Issue ISSUE = Issue.create(
            ISSUE_ID,
            DESCRIPTION,
            EXPLANATION,
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(PowerManagerDetector.class, Scope.JAVA_FILE_SCOPE));

    @Override
    public List<String> getApplicableMethodNames() {
        return Collections.singletonList("newWakeLock");
    }

    @Override
    public void visitMethodCall(@NonNull JavaContext context, @NonNull UCallExpression call, @NonNull PsiMethod method) {
        JavaEvaluator evaluator = context.getEvaluator();
        if (!evaluator.isMemberInClass(method, "android.os.PowerManager")) {
            return;
        }

        List<UExpression> arguments = call.getValueArguments();
        if (arguments.size() < 2) {
            return;
        }

        UExpression tagArg = arguments.get(1);
        String tag = evaluator.evaluateString(tagArg);
        if (tag == null) {
            return;
        }

        if (!VALID_TAG_PATTERN.matcher(tag).matches()) {
            context.report(ISSUE, call, context.getLocation(tagArg),
                    "Wake lock tag should be in the format `PackageName:WakeLockTag`");
        }
    }
}