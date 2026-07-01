package com.android.tools.lint.checks;

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

    public static final Issue ISSUE = Issue.create(
            "InvalidWakeLockTag",
            "Invalid Wake Lock Tag",
            "Wake Lock tags must follow the naming conventions defined in the `PowerManager` documentation. " +
            "A wake lock tag should be prefixed with your application's package name or a unique prefix, " +
            "followed by a colon and a name that describes the wake lock's purpose. " +
            "The tag should not contain spaces, and its length should be 50 characters or less.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(
                    PowerManagerDetector.class,
                    Scope.JAVA_FILE_SCOPE
            )
    );

    @Override
    public List<String> getApplicableMethodNames() {
        return Collections.singletonList("newWakeLock");
    }

    @Override
    public void visitMethodCall(JavaContext context, UCallExpression node, PsiMethod method) {
        if (!context.getEvaluator().isMemberInClass(method, "android.os.PowerManager")) {
            return;
        }
        List<UExpression> arguments = node.getValueArguments();
        if (arguments.size() < 2) {
            return;
        }
        UExpression tagArgument = arguments.get(1);
        Object evaluated = tagArgument.evaluate();
        if (evaluated instanceof String) {
            String tag = (String) evaluated;
            validateTag(context, tagArgument, tag);
        }
    }

    private void validateTag(JavaContext context, UExpression tagArgument, String tag) {
        if (tag.isEmpty()) {
            context.report(ISSUE, tagArgument, context.getLocation(tagArgument),
                    "Wake lock tag cannot be empty");
            return;
        }
        if (tag.length() > 50) {
            context.report(ISSUE, tagArgument, context.getLocation(tagArgument),
                    "Wake lock tag is too long (" + tag.length() + " characters); should be 50 characters or less");
            return;
        }
        if (tag.contains(" ")) {
            context.report(ISSUE, tagArgument, context.getLocation(tagArgument),
                    "Wake lock tag should not contain spaces");
            return;
        }
        if (!tag.contains(":")) {
            context.report(ISSUE, tagArgument, context.getLocation(tagArgument),
                    "Wake lock tag should be prefixed with your application's package name or a unique prefix, followed by a colon (e.g. `myapp:mytag`)");
            return;
        }
        if (tag.startsWith("android:")) {
            context.report(ISSUE, tagArgument, context.getLocation(tagArgument),
                    "Avoid using the `android:` prefix for wake lock tags; it is reserved for the system");
        }
    }
}