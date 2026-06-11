package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;

import org.jetbrains.uast.UAnnotation;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UIastVisitor;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.USimpleNameReferenceExpression;
import org.jetbrains.uast.visitor.AbstractUastVisitor;

import java.util.HashMap;
import java.util.Map;

public class OnClickDetector extends Detector implements Detector.UastScanner {

    public static final Issue ISSUE = Issue.create(
            "OnClickMethodDoesNotExist",
            "The `onClick` method does not exist in the View's context.",
            "The `onClick` attribute value should be the name of a method in this View's context to invoke when the view is clicked. This name must correspond to a public method that takes exactly one parameter of type `View`.",
            Category.CORRECTNESS,
            5, // Priority
            Severity.ERROR,
            new Implementation(
                    OnClickDetector.class,
                    Scope.JAVA_FILE_SCOPE));

    private final Map<String, UMethod> methodMap = new HashMap<>();

    @Override
    public List<Class<? extends UIastVisitor>> getApplicableUastTypes() {
        return Collections.singletonList(UCallExpression.class);
    }

    @NonNull
    @Override
    public JavaContext getJavaContext() {
        return (JavaContext) super.getJavaContext();
    }

    @Override
    public void visitMethod(@NonNull UMethod node, @NonNull JavaContext context) {
        if ("onClick".equals(node.getName())) {
            List<String> parameterTypes = node.getParameterTypes();
            if (parameterTypes.size() == 1 && "android.view.View".equals(parameterTypes.get(0))) {
                methodMap.put(node.getName(), node);
            }
        }
    }

    @Override
    public void visitUCallExpression(@NonNull UCallExpression node, @NonNull JavaContext context) {
        String methodName = node.getMethodName();
        if ("setOnClickListener".equals(methodName)) {
            USimpleNameReferenceExpression reference = (USimpleNameReferenceExpression) node.getReceiver();
            if (reference != null && !methodMap.containsKey(reference.getName())) {
                context.report(
                        ISSUE,
                        Location.create(node),
                        "The `onClick` method `" + reference.getName() + "` does not exist in the View's context.");
            }
        }
    }

    @Override
    public void afterCheckFile(@NonNull JavaContext context) {
        methodMap.clear();
    }
}