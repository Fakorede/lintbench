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

    private static final String ISSUE_ID = "InvalidWakeLockTag";
    private static final String DESCRIPTION = "Invalid Wake Lock Tag";
    private static final String EXPLANATION = "Wake Lock tags must follow the naming conventions defined in the PowerManager documentation. "
            + "Tags should be in the format `Prefix:Suffix` (e.g., `com.example.app:MyFeature` or `MyClass:myMethod`).";
    private static final Category CATEGORY = Category.CORRECTNESS;
    private static final int PRIORITY = 6;
    private static final Severity SEVERITY = Severity.WARNING;

    public static final Issue ISSUE = Issue.create(
            ISSUE_ID,
            DESCRIPTION,
            EXPLANATION,
            CATEGORY,
            PRIORITY,
            SEVERITY,
            new Implementation(PowerManagerDetector.class, Scope.JAVA_FILE_SCOPE)
    );

    private static final Pattern VALID_TAG_PATTERN = Pattern.compile("^[^:]+:[^:]+$");

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
        Object evaluated = context.getEvaluator().evaluate(tagArg);
        if (!(evaluated instanceof String)) {
            return;
        }

        String tag = (String) evaluated;
        if (!VALID_TAG_PATTERN.matcher(tag).matches()) {
            context.report(
                    ISSUE,
                    context.getLocation(tagArg),
                    "Wake lock tags must be in the format `Prefix:Suffix` (e.g., `com.example.app:Feature`). "
                    + "Found: `" + tag + "`"
            );
        }
    }
}