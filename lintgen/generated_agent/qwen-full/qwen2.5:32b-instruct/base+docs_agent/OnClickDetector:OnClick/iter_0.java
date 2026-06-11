package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.client.api.UElementHandler;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.intellij.psi.PsiMethod;

import org.jetbrains.uast.UAnnotation;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.USimpleNameReferenceExpression;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class OnClickDetector extends Detector implements SourceCodeScanner {

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

    private final Map<String, PsiMethod> methodMap = new HashMap<>();

    @Nullable
    @Override
    public UElementHandler createUastHandler(@NonNull Context context) {
        return new UElementHandler() {
            @Override
            public void visitClass(@NonNull UClass node) {
                for (UMethod method : node.getMethods()) {
                    if (method.getName() != null && method.getParameterCount() == 1 &&
                            "android.view.View".equals(method.getParameterTypes()[0])) {
                        methodMap.put(method.getName(), method);
                    }
                }
            }

            @Override
            public void visitSimpleNameReferenceExpression(@NonNull USimpleNameReferenceExpression node) {
                if (node.getContainingMethod() != null && node.getContainingMethod().getAnnotationOfType("android.view.View.OnClickListener") != null) {
                    String methodName = node.getName();
                    if (!methodMap.containsKey(methodName)) {
                        context.report(
                                ISSUE,
                                Location.create(node),
                                "The `onClick` method `" + methodName + "` does not exist in the View's context.");
                    }
                }
            }

            @Override
            public void visitAnnotation(@NonNull UAnnotation node) {
                if ("android.view.View.OnClickListener".equals(node.getQualifiedName())) {
                    JavaContext javaContext = (JavaContext) context;
                    Collection<PsiMethod> methods = javaContext.getModuleScope().getMethodsByName(node.getName(), false);
                    for (PsiMethod method : methods) {
                        if (!methodMap.containsKey(method.getName())) {
                            Location location = Location.create(javaContext, method);
                            context.report(ISSUE, location, "The `onClick` method `" + method.getName() + "` does not exist in the View's context.");
                        }
                    }
                }
            }
        };
    }

    @Nullable
    @Override
    public Collection<String> getApplicableMethodNames() {
        return Collections.singletonList("setOnClickListener");
    }

    @Nullable
    @Override
    public List<QuickFix> getQuickFixes(@NonNull Context context) {
        return null;
    }
}