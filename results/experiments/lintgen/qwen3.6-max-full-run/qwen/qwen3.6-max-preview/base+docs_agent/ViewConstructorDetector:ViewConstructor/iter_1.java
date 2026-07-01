package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.JavaEvaluator;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.UElementHandler;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;

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
                String name = node.getName();
                if (name == null || node.isInterface() || node.isEnum()) {
                    return;
                }
                if (node.hasModifierProperty(PsiModifier.ABSTRACT)) {
                    return;
                }

                JavaEvaluator evaluator = context.getEvaluator();
                if (!evaluator.extendsClass(node, "android.view.View", false)) {
                    return;
                }

                PsiMethod[] constructors = node.getConstructors();
                boolean hasValidConstructor = false;

                for (PsiMethod constructor : constructors) {
                    PsiParameter[] parameters = constructor.getParameterList().getParameters();
                    int size = parameters.length;
                    if (size < 1 || size > 3) {
                        continue;
                    }

                    PsiType type1 = parameters[0].getType();
                    if (!"android.content.Context".equals(type1.getCanonicalText())) {
                        continue;
                    }

                    if (size == 1) {
                        hasValidConstructor = true;
                        break;
                    }

                    PsiType type2 = parameters[1].getType();
                    if (!"android.util.AttributeSet".equals(type2.getCanonicalText())) {
                        continue;
                    }

                    if (size == 2) {
                        hasValidConstructor = true;
                        break;
                    }

                    PsiType type3 = parameters[2].getType();
                    String type3Text = type3.getCanonicalText();
                    if ("int".equals(type3Text) || "java.lang.Integer".equals(type3Text)) {
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