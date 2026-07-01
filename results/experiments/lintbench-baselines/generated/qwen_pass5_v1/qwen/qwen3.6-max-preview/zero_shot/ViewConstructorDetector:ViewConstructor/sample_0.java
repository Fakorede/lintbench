package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.*;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.uast.*;

import java.util.Collections;
import java.util.List;

public class ViewConstructorDetector extends Detector implements UastScanner {

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
            new Implementation(ViewConstructorDetector.class, Scope.JAVA_FILE_SCOPE));

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.singletonList(UClass.class);
    }

    @Override
    public UElementHandler createUastHandler(@NotNull JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitClass(@NotNull UClass node) {
                if (node.isInterface() || node.isAbstract()) {
                    return;
                }

                // Non-static inner classes cannot be instantiated by the layout inflater
                UElement parent = node.getUastParent();
                if (parent instanceof UClass && !node.isStatic()) {
                    return;
                }

                JavaEvaluator evaluator = context.getEvaluator();
                if (!evaluator.inheritsFrom(node, "android.view.View", false)) {
                    return;
                }

                boolean hasValidConstructor = false;
                for (UMethod constructor : node.getConstructors()) {
                    List<UParameter> parameters = constructor.getUastParameters();
                    int n = parameters.size();
                    if (n >= 1 && n <= 4) {
                        PsiType type1 = parameters.get(0).getType();
                        if (type1 != null && evaluator.inheritsFrom(type1, "android.content.Context", false)) {
                            if (n == 1) {
                                hasValidConstructor = true;
                                break;
                            }
                            PsiType type2 = parameters.get(1).getType();
                            if (type2 != null && evaluator.inheritsFrom(type2, "android.util.AttributeSet", false)) {
                                if (n == 2) {
                                    hasValidConstructor = true;
                                    break;
                                }
                                PsiType type3 = parameters.get(2).getType();
                                if (type3 != null && type3.equals(PsiType.INT)) {
                                    if (n == 3) {
                                        hasValidConstructor = true;
                                        break;
                                    }
                                    if (n == 4) {
                                        PsiType type4 = parameters.get(3).getType();
                                        if (type4 != null && type4.equals(PsiType.INT)) {
                                            hasValidConstructor = true;
                                            break;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (!hasValidConstructor) {
                    String name = node.getName();
                    context.report(ISSUE, node, context.getNameLocation(node),
                            "Custom view `" + name + "` is missing constructor used by tools: " +
                            "(Context) or (Context,AttributeSet) or (Context,AttributeSet,int)");
                }
            }
        };
    }
}