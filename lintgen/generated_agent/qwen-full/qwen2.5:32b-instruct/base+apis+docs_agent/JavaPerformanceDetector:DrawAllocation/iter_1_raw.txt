package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;

import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UElementHandler;
import org.jetbrains.uast.UastUtils;

import java.util.Collections;
import java.util.List;

public class JavaPerformanceDetector extends Detector implements Detector.SourceCodeScanner {

    public static final Issue ISSUE = Issue.create(
            "MemoryAllocationInDrawingCode",
            "Avoid allocating objects during drawing or layout operations.",
            "These are called frequently, so a smooth UI can be interrupted by garbage collection pauses caused by the object allocations. " +
                    "The way this is generally handled is to allocate the needed objects up front and to reuse them for each drawing operation." +
                    "Some methods allocate memory on your behalf (such as `Bitmap.create`), and these should be handled in the same way.",
            Category.PERFORMANCE,
            6, // Priority
            Severity.WARNING,
            new Implementation(
                    JavaPerformanceDetector.class,
                    Scope.JAVA_FILE_SCOPE));

    @Override
    public List<String> getApplicableMethodNames() {
        return Collections.singletonList("create");
    }

    @Override
    public void visitMethodCall(JavaContext context, UCallExpression node, PsiMethod method) {
        if (node != null && method != null) {
            String methodName = method.getName();
            if ("create".equals(methodName)) {
                UClass containingClass = UastUtils.getContainingClass(node);
                if (containingClass != null) {
                    UMethod containingMethod = UastUtils.getContainingMethod(node);
                    if (isDrawingOrLayoutMethod(containingMethod)) {
                        context.report(ISSUE, node, context.getLocation(node),
                                "Avoid allocating objects during drawing or layout operations.");
                    }
                }
            }
        }
    }

    private boolean isDrawingOrLayoutMethod(UMethod method) {
        return method != null && (
                method.getName().equals("onDraw") ||
                        method.getName().equals("onMeasure") ||
                        method.getName().equals("onLayout")
        );
    }
}