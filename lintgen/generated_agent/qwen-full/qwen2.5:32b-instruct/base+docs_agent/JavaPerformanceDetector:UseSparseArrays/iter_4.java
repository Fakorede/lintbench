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
import com.intellij.psi.PsiMethodCallExpression;

import java.util.Collections;
import java.util.List;

public class JavaPerformanceDetector extends Detector implements Detector.JavaScanner {

    private static final String HASH_MAP = "HashMap";
    private static final String SPARSE_ARRAY = "SparseArray";

    public static final Issue ISSUE = Issue.create(
            "UseSparseArray",
            "HashMap can be replaced with SparseArray for better performance.",
            "For maps where the keys are of type integer, it's typically more efficient to use the Android `SparseArray` API. This check identifies scenarios where you might want to consider using `SparseArray` instead of `HashMap` for better performance.",
            Category.PERFORMANCE,
            5,
            Severity.WARNING,
            new Implementation(
                    JavaPerformanceDetector.class,
                    Scope.JAVA_FILE_SCOPE
            )
    );

    @Override
    public List<String> getApplicableMethodNames() {
        return Collections.singletonList(HASH_MAP);
    }

    @Override
    public void visitMethod(@NonNull JavaContext context, @NonNull PsiMethodCallExpression methodCall) {
        if (HASH_MAP.equals(methodCall.getMethodExpression().getQualifierText())) {
            reportIssue(context, methodCall);
        }
    }

    private void reportIssue(@NonNull JavaContext context, @NonNull PsiMethodCallExpression call) {
        context.report(
                ISSUE,
                call,
                context.getLocation(call),
                "Consider using SparseArray for better performance."
        );
    }
}