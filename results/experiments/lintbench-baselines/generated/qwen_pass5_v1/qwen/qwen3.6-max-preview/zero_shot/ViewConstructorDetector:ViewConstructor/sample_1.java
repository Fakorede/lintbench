package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.JavaEvaluator;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
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
            new Implementation(ViewConstructorDetector.class, Scope.JAVA_FILE_SCOPE));

    @NotNull
    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.singletonList(UClass.class);
    }

    @Override
    public UElementHandler createUastHandler(@NotNull JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitClass(@NotNull UClass node) {
                String qualifiedName = node.getQualifiedName();
                if (qualifiedName == null || qualifiedName.startsWith("android.")) {
                    return;
                }

                if (node.isInterface() || node.hasModifierProperty(PsiModifier.ABSTRACT)) {
                    return;
                }

                JavaEvaluator evaluator = context.getEvaluator();
                if (!evaluator.extendsClass(node, "android.view.View", false)) {
                    return;
                }

                boolean hasValidConstructor = false;
                for (PsiMethod constructor : node.getConstructors()) {
                    PsiParameter[] parameters = constructor.getParameterList().getParameters();
                    int count = parameters.length;
                    if (count < 1 || count > 3) {
                        continue;
                    }

                    if (!evaluator.typeMatches(parameters[0].getType(), "android.content.Context")) {
                        continue;
                    }

                    if (count == 1) {
                        hasValidConstructor = true;
                        break;
                    }

                    if (!evaluator.typeMatches(parameters[1].getType(), "android.util.AttributeSet")) {
                        continue;
                    }

                    if (count == 2) {
                        hasValidConstructor = true;
                        break;
                    }

                    // count == 3
                    PsiType thirdType = parameters[2].getType();
                    if (thirdType.equals(PsiType.INT) || evaluator.typeMatches(thirdType, "java.lang.Integer")) {
                        hasValidConstructor = true;
                        break;
                    }
                }

                if (!hasValidConstructor) {
                    context.report(ISSUE, node, context.getNameLocation(node),
                            "Custom view `" + node.getName() + "` is missing constructor used by tools: " +
                            "(Context) or (Context,AttributeSet) or (Context,AttributeSet,int)");
                }
            }
        };
    }
}