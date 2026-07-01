package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.ConstantEvaluator;
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UExpression;

public class PowerManagerDetector extends Detector implements SourceCodeScanner {

    private static final String ANDROID_OS_POWER_MANAGER = "android.os.PowerManager";
    private static final String POWER_MANAGER_SIMPLE_NAME = "PowerManager";
    private static final String NEW_WAKE_LOCK = "newWakeLock";

    public static final Issue INVALID_WAKE_LOCK_TAG = Issue.create(
            "InvalidWakeLockTag",
            "Invalid Wake Lock Tag",
            "Wake Lock tags must follow the naming conventions defined in the PowerManager documentation: "
                    + "they should be in the form \"package:tag\", must not be empty, must not "
                    + "contain spaces, must not start or end with ':', must be no longer than 50 "
                    + "characters, and must not use platform tags such as \"android:tag\" or "
                    + "\"com.android:tag\".",
            Category.CORRECTNESS,
            4,
            Severity.WARNING,
            new Implementation(PowerManagerDetector.class, Scope.JAVA_FILE_SCOPE));

    @Override
    public List<String> getApplicableCallNames() {
        return Collections.singletonList(NEW_WAKE_LOCK);
    }

    @Override
    public void visitMethodCall(
            @NotNull JavaContext context,
            @NotNull UCallExpression call,
            @NotNull PsiMethod method) {
        if (!isPowerManagerWakeLockMethod(context, call, method)) {
            return;
        }

        List<UExpression> arguments = call.getValueArguments();
        if (arguments.size() != 2) {
            return;
        }

        UExpression tagArgument = arguments.get(1);
        String tag = ConstantEvaluator.evaluateString(context, tagArgument, false);
        if (tag == null) {
            return;
        }

        if (!isValidWakeLockTag(tag)) {
            context.report(
                    INVALID_WAKE_LOCK_TAG,
                    tagArgument,
                    context.getLocation(tagArgument),
                    "Invalid Wake Lock tag \"" + tag + "\". Wake lock tags must be in the form "
                            + "\"package:tag\", must not be empty, must not contain spaces, must "
                            + "not start or end with ':', must be no longer than 50 characters, and "
                            + "must not use platform tags such as \"android:tag\" or "
                            + "\"com.android:tag\".");
        }
    }

    private static boolean isPowerManagerWakeLockMethod(
            @NotNull JavaContext context,
            @NotNull UCallExpression call,
            @NotNull PsiMethod method) {
        if (!NEW_WAKE_LOCK.equals(method.getName())) {
            return false;
        }
        if (method.getParameterList().getParametersCount() != 2) {
            return false;
        }

        PsiClass containingClass = method.getContainingClass();
        if (containingClass != null) {
            String qualifiedName = containingClass.getQualifiedName();
            if (ANDROID_OS_POWER_MANAGER.equals(qualifiedName)) {
                return true;
            }
            String simpleName = containingClass.getName();
            if (POWER_MANAGER_SIMPLE_NAME.equals(simpleName)
                    && (qualifiedName == null || !qualifiedName.contains("."))) {
                return true;
            }
        }

        UExpression receiver = call.getReceiver();
        if (receiver != null) {
            PsiClass receiverClass = context.getEvaluator().getTypeClass(receiver.getExpressionType());
            if (receiverClass != null) {
                String qualifiedName = receiverClass.getQualifiedName();
                if (ANDROID_OS_POWER_MANAGER.equals(qualifiedName)) {
                    return true;
                }
                String simpleName = receiverClass.getName();
                if (POWER_MANAGER_SIMPLE_NAME.equals(simpleName)
                        && (qualifiedName == null || !qualifiedName.contains("."))) {
                    return true;
                }
            }
        }

        return false;
    }

    private static boolean isValidWakeLockTag(@NotNull String tag) {
        int length = tag.length();
        if (length == 0 || length > 50) {
            return false;
        }

        int colon = tag.indexOf(':');
        if (colon <= 0 || colon == length - 1) {
            return false;
        }

        if (tag.indexOf(' ') >= 0) {
            return false;
        }

        String prefix = tag.substring(0, colon);
        return !prefix.equals("android") && !prefix.equals("com.android");
    }
}