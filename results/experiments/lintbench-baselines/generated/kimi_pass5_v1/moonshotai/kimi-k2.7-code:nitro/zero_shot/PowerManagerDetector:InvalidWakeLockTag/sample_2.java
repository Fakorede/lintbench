package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.ConstantEvaluator;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.intellij.psi.PsiMethod;
import java.util.Collections;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UExpression;

public class PowerManagerDetector extends Detector implements Detector.SourceCodeScanner {

    public static final Issue ISSUE = Issue.create(
                    "InvalidWakeLockTag",
                    "Invalid Wake Lock Tag",
                    "Wake lock tags passed to PowerManager.newWakeLock() must follow the naming "
                            + "conventions described in the PowerManager documentation. In "
                            + "particular, the tag must not be empty, must not exceed 100 "
                            + "characters, and must not contain spaces or ':' characters.",
                    Category.CORRECTNESS,
                    5,
                    Severity.ERROR,
                    new Implementation(PowerManagerDetector.class, Scope.JAVA_FILE_SCOPE))
            .setMoreInfo("https://developer.android.com/reference/android/os/PowerManager.html");

    @Override
    public List<String> getApplicableMethodNames() {
        return Collections.singletonList("newWakeLock");
    }

    @Override
    public void visitMethodCall(
            @NotNull JavaContext context,
            @NotNull UCallExpression call,
            @NotNull PsiMethod method) {
        if (!context.getEvaluator().isMemberInClass(method, "android.os.PowerManager")) {
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
        String message = getInvalidTagMessage(tag);
        if (message != null) {
            context.report(
                    ISSUE,
                    tagArgument,
                    context.getLocation(tagArgument),
                    message);
        }
    }

    private static String getInvalidTagMessage(String tag) {
        if (tag.isEmpty()) {
            return "Wake lock tag must not be empty";
        }
        if (tag.length() > 100) {
            return "Wake lock tag must not exceed 100 characters";
        }
        for (int i = 0; i < tag.length(); i++) {
            char c = tag.charAt(i);
            if (c == ':' || Character.isWhitespace(c)) {
                return "Wake lock tag must not contain spaces or ':' characters";
            }
        }
        return null;
    }
}