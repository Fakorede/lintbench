package com.android.tools.lint.checks;

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

import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

public class PowerManagerDetector extends Detector implements Detector.UastScanner {

    private static final Pattern VALID_TAG_PATTERN = Pattern.compile("[*]?[a-zA-Z0-9._-]+:[a-zA-Z0-9._-]+");

    public static final Issue ISSUE = Issue.create(
            "InvalidWakeLockTag",
            "Invalid Wake Lock Tag",
            "Wake Lock tags must follow the naming conventions defined in the `PowerManager` documentation. " +
            "The tag should be of the form `your.package.name:your_tag`.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(PowerManagerDetector.class, Scope.JAVA_FILE_SCOPE)
    );

    @Override
    public List<String> getApplicableMethodNames() {
        return Collections.singletonList("newWakeLock");
    }

    @Override
    public void visitMethodCall(JavaContext context, UCallExpression call, PsiMethod method) {
        if (!context.getEvaluator().isMemberInClass(method, "android.os.PowerManager")) {
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
            if (!VALID_TAG_PATTERN.matcher(tag).matches()) {
                context.report(
                        ISSUE,
                        call,
                        context.getLocation(tagArg),
                        "Invalid wake lock tag format. Expected format: `your.package.name:your_tag`"
                );
            }
        }
    }
}