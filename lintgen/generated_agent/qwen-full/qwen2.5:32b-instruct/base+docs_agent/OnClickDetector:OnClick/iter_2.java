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
import org.jetbrains.uast.USimpleNameReferenceExpression;
import org.jetbrains.uast.UastUtils;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class OnClickDetector extends Detector implements Detector.JavaScanner {

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
    public List<String> getApplicableMethods() {
        return Collections.singletonList("setOnClickListener");
    }

    @NonNull
    @Override
    public JavaContext getJavaContext() {
        return (JavaContext) super.getJavaContext();
    }

    @Override
    public void visitMethod(@NonNull UMethod node, @NonNull MethodContext context) {
        if ("setOnClickListener".equals(node.getName())) {
            USimpleNameReferenceExpression reference = UastUtils.findSimpleNameReferenceExpression(node);
            if (reference != null && !methodMap.containsKey(reference.getReferencedName())) {
                context.report(
                        ISSUE,
                        Location.create(reference),
                        "The `onClick` method `" + reference.getReferencedName() + "` does not exist in the View's context.");
            }
        }

        for (UMethod method : node.getClassContext().getMethods()) {
            if ("onClick".equals(method.getName())) {
                List<String> parameterTypes = UastUtils.getParameterTypeStrings(method);
                if (parameterTypes.size() == 1 && "android.view.View".equals(parameterTypes.get(0))) {
                    methodMap.put(method.getName(), method);
                }
            }
        }
    }

    @Override
    public void afterCheckFile(@NonNull JavaContext context) {
        methodMap.clear();
    }
}