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
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UastUtils;
import org.jetbrains.uast.visitor.AbstractUastVisitor;

import java.util.Collections;
import java.util.List;

public class JavaPerformanceDetector extends Detector implements Detector.SourceCodeScanner {

    public static final Issue ISSUE = Issue.create(
            "UseValueOf",
            "You should not call the constructor for wrapper classes directly, such as `new Integer(42)`. Instead, call the `valueOf` factory method, such as `Integer.valueOf(42)`.",
            "This will typically use less memory because common integers such as 0 and 1 will share a single instance.",
            Category.PERFORMANCE,
            5,
            Severity.WARNING,
            new Implementation(
                    JavaPerformanceDetector.class,
                    Scope.JAVA_FILE_SCOPE
            )
    );

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.singletonList(UCallExpression.class);
    }

    @Override
    public UElementHandler createUastHandler(final JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitCallExpression(JavaContext context, UCallExpression node) {
                PsiMethod method = node.resolve();
                if (method != null && "<init>".equals(method.getName())) {
                    UClass containingClass = UastUtils.getContainingClass(node);
                    String className = containingClass == null ? "" : containingClass.getName();

                    switch (className) {
                        case "java.lang.Integer":
                        case "java.lang.Long":
                        case "java.lang.Float":
                        case "java.lang.Double":
                        case "java.lang.Character":
                            context.report(ISSUE, node, context.getLocation(node),
                                    "Use `valueOf` instead of calling the constructor directly");
                            break;
                    }
                }

                super.visitCallExpression(context, node);
            }
        };
    }
}