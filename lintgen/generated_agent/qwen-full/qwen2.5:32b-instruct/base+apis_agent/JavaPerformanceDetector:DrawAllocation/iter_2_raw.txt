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
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UastUtils;

import java.util.Collections;
import java.util.List;

public class JavaPerformanceDetector extends Detector implements Detector.SourceCodeScanner {

    public static final Issue ISSUE = Issue.create(
            "MemoryAllocationInDrawingCode",
            "Avoid allocating objects during drawing or layout operations.",
            "These are called frequently, so a smooth UI can be interrupted by garbage collection pauses caused by the object allocations. The way this is generally handled is to allocate the needed objects up front and to reuse them for each drawing operation.",
            Category.PERFORMANCE,
            6,
            Severity.WARNING,
            new Implementation(
                    JavaPerformanceDetector.class,
                    Scope.JAVA_FILE_SCOPE
            )
    );

    @Override
    public List<String> getApplicableMethodNames() {
        return Collections.singletonList("create");
    }

    @Override
    public void visitMethodCall(JavaContext context, UCallExpression node) {
        PsiMethod method = context.getUastResolver().resolveMethod(node);
        if (method != null && "create".equals(method.getName())) {
            UClass containingClass = UastUtils.getContainingClass(node);
            if (containingClass != null && "android.graphics.Bitmap".equals(containingClass.getQualifiedName())) {
                context.report(ISSUE, node, context.getLocation(node), "Avoid allocating objects during drawing or layout operations.");
            }
        }
    }

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.singletonList(UMethod.class);
    }

    @Override
    public void visitClass(JavaContext context, UClass klass) {
        if ("android.view.View".equals(klass.getQualifiedName())) {
            for (UMethod method : klass.getMethods()) {
                if ("onDraw".equals(method.getName()) || "onLayout".equals(method.getName())) {
                    context.report(ISSUE, method, context.getLocation(method), "Avoid allocating objects within drawing or layout methods.");
                }
            }
        }
    }

}