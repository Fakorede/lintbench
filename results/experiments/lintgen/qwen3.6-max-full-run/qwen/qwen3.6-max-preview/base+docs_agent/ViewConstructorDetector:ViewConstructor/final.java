package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.UElementHandler;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.JvmModifier;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UParameter;

import java.util.Collections;
import java.util.List;

public class ViewConstructorDetector extends Detector implements Detector.UastScanner {

    public static final Issue ISSUE = Issue.create(
            "ViewConstructor",
            "Missing View constructors for XML inflation",
            "Some layout tools (such as the Android layout editor) need to find a " +
            "constructor with one of the following signatures:\n" +
            "* `View(Context context)`\n" +
            "* `View(Context context, AttributeSet attrs)`\n" +
            "* `View(Context context, AttributeSet attrs, int defStyle)`\n\n" +
            "If your custom view needs to perform initialization which does " +
            "not apply when used in a layout editor, you can surround the " +
            "given code with a check to see if `View#isInEditMode()` is " +
            "false, since that method will return `false` at runtime but " +
            "true within a user interface editor.",
            Category.CORRECTNESS,
            3,
            Severity.WARNING,
            new Implementation(ViewConstructorDetector.class, Scope.JAVA_FILE_SCOPE)
    );

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.singletonList(UClass.class);
    }

    @Nullable
    @Override
    public UElementHandler createUastHandler(@NotNull JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitClass(@NotNull UClass node) {
                String name = node.getName();
                if (name == null || node.isInterface() || node.isEnum() || node.hasModifier(JvmModifier.ABSTRACT)) {
                    return;
                }

                if (!context.getEvaluator().extendsClass(node, "android.view.View", false)) {
                    return;
                }

                boolean hasValidConstructor = false;

                for (UMethod method : node.getMethods()) {
                    if (!method.isConstructor()) {
                        continue;
                    }

                    List<UParameter> parameters = method.getUastParameters();
                    int size = parameters.size();
                    if (size < 1 || size > 3) {
                        continue;
                    }

                    String type1 = parameters.get(0).getType().getCanonicalText();
                    if (!"android.content.Context".equals(type1)) {
                        continue;
                    }

                    if (size == 1) {
                        hasValidConstructor = true;
                        break;
                    }

                    String type2 = parameters.get(1).getType().getCanonicalText();
                    if (!"android.util.AttributeSet".equals(type2)) {
                        continue;
                    }

                    if (size == 2) {
                        hasValidConstructor = true;
                        break;
                    }

                    String type3 = parameters.get(2).getType().getCanonicalText();
                    if ("int".equals(type3)) {
                        hasValidConstructor = true;
                        break;
                    }
                }

                if (!hasValidConstructor) {
                    String message = "Custom view `" + name +
                            "` is missing constructor used by tools: " +
                            "`(Context)`, `(Context,AttributeSet)`, or `(Context,AttributeSet,int)`";
                    context.report(ISSUE, node, context.getNameLocation(node), message);
                }
            }
        };
    }
}