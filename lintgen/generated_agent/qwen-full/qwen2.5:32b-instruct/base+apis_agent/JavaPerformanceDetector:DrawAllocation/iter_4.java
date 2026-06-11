package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;

import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UastUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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

    @Nullable
    @Override
    public UElementHandler createUastHandler(@NotNull JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitCallExpression(@NotNull JavaContext context, @NotNull UCallExpression node) {
                PsiMethod method = context.getResolveHelper().resolveMethod(node);
                if (method != null && "create".equals(method.getName())) {
                    UClass containingClass = UastUtils.getContainingClass(node);
                    if (containingClass != null && "android.graphics.Bitmap".equals(containingClass.getQualifiedName())) {
                        context.report(ISSUE, node, context.getLocation(node), "Avoid allocating objects during drawing or layout operations.");
                    }
                }
            }

            @Override
            public void visitMethod(@NotNull JavaContext context, @NotNull UMethod node) {
                if ("onDraw".equals(node.getName()) || "onLayout".equals(node.getName())) {
                    for (UElement child : node.getContainingFile().getDeclarations()) {
                        if (child instanceof UCallExpression && ((UCallExpression) child).resolve() != null) {
                            context.report(ISSUE, child, context.getLocation(child), "Avoid allocating objects within drawing or layout methods.");
                        }
                    }
                }
            }
        };
    }

}