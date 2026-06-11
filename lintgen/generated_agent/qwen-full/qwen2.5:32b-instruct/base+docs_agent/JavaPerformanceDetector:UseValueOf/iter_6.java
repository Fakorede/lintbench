package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNewExpression;

import java.util.Arrays;
import java.util.List;

public class JavaPerformanceDetector extends Detector implements Detector.NewInstanceUsage {

    private static final String ISSUE_ID = "UseValueOf";
    private static final String ISSUE_NAME = "Should use valueOf instead of new for wrapper classes";
    private static final String ISSUE_EXPLANATION =
            "You should not call the constructor for wrapper classes directly, such as `new Integer(42)`. Instead, call the `valueOf` factory method, such as `Integer.valueOf(42)`." +
                    " This will typically use less memory because common integers such as 0 and 1 will share a single instance.";

    public static final Issue ISSUE = Issue.create(
            ISSUE_ID,
            ISSUE_NAME,
            ISSUE_EXPLANATION,
            Category.PERFORMANCE,
            5, // Priority
            Severity.WARNING,
            new Implementation(JavaPerformanceDetector.class, Scope.JAVA_FILE_SCOPE)
    );

    @Override
    public List<String> getApplicableNewTypes() {
        return Arrays.asList("java.lang.Integer", "java.lang.Long", "java.lang.Short", "java.lang.Byte", "java.lang.Double", "java.lang.Float", "java.lang.Character");
    }

    @Override
    public void visitNewInstance(@NonNull JavaContext context, @NonNull PsiNewExpression newInstance) {
        String className = newInstance.getClassReference().getQualifiedName();
        if (isWrapperClass(className)) {
            context.report(ISSUE, newInstance, context.getLocation(newInstance), ISSUE_EXPLANATION);
        }
    }

    private boolean isWrapperClass(String className) {
        return "java.lang.Integer".equals(className)
                || "java.lang.Long".equals(className)
                || "java.lang.Short".equals(className)
                || "java.lang.Byte".equals(className)
                || "java.lang.Double".equals(className)
                || "java.lang.Float".equals(className)
                || "java.lang.Character".equals(className);
    }
}