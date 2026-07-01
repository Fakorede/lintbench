package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.*;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.uast.*;

import java.util.Collections;
import java.util.List;

public class ViewConstructorDetector extends Detector implements SourceCodeScanner {

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

    @Override
    public UElementHandler createUastHandler(@NotNull JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitClass(@NotNull UClass node) {
                if (node.getName() == null || node.isInterface() || node.isAnnotationType() ||
                        node.isEnum() || node.isAbstract()) {
                    return;
                }

                JavaEvaluator evaluator = context.getEvaluator();
                if (!evaluator.extendsClass(node, "android.view.View", false)) {
                    return;
                }

                List<UMethod> constructors = node.getConstructors();
                boolean hasValidConstructor = false;

                for (UMethod constructor : constructors) {
                    List<UParameter> parameters = constructor.getUastParameters();
                    int size = parameters.size();
                    if (size < 1 || size > 3) {
                        continue;
                    }

                    PsiType type1 = parameters.get(0).getType();
                    if (!isType(type1, "android.content.Context")) {
                        continue;
                    }

                    if (size == 1) {
                        hasValidConstructor = true;
                        break;
                    }

                    PsiType type2 = parameters.get(1).getType();
                    if (!isType(type2, "android.util.AttributeSet")) {
                        continue;
                    }

                    if (size == 2) {
                        hasValidConstructor = true;
                        break;
                    }

                    PsiType type3 = parameters.get(2).getType();
                    if (isType(type3, "int") || isType(type3, "java.lang.Integer")) {
                        hasValidConstructor = true;
                        break;
                    }
                }

                if (!hasValidConstructor) {
                    String message = "Custom view `" + node.getName() +
                            "` is missing constructor used by tools: " +
                            "`(Context)`, `(Context,AttributeSet)`, or `(Context,AttributeSet,int)`";
                    context.report(ISSUE, node, context.getNameLocation(node), message);
                }
            }
        };
    }

    private static boolean isType(@NotNull PsiType type, @NotNull String expected) {
        return expected.equals(type.getCanonicalText());
    }
}