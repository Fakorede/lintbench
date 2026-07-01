package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.JavaEvaluator;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.UastScanner;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NonNull;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UParameter;

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
        new Implementation(ViewConstructorDetector.class, Scope.JAVA_FILE_SCOPE)
    );

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.singletonList(UClass.class);
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass node) {
        if (node.isInterface() || node.isEnum() || node.isAnnotationType()) {
            return;
        }
        if (node.hasModifierProperty(PsiModifier.ABSTRACT)) {
            return;
        }
        if (!node.isInheritor("android.view.View", true)) {
            return;
        }

        boolean hasValidConstructor = false;
        for (UMethod method : node.getMethods()) {
            if (method.isConstructor() && method.getContainingClass() == node) {
                if (isValidViewConstructor(context, method)) {
                    hasValidConstructor = true;
                    break;
                }
            }
        }

        if (!hasValidConstructor) {
            context.report(ISSUE, node, context.getNameLocation(node),
                "Custom view `%1$s` is missing constructor used by tools: " +
                "`(Context)` or `(Context,AttributeSet)` or `(Context,AttributeSet,int)`",
                node.getName());
        }
    }

    private boolean isValidViewConstructor(@NonNull JavaContext context, @NonNull UMethod constructor) {
        UParameter[] parameters = constructor.getUastParameters();
        int count = parameters.length;
        JavaEvaluator evaluator = context.getEvaluator();

        if (count == 0) return false;

        PsiType type0 = parameters[0].getType();
        if (type0 == null || !evaluator.typeMatches(type0, "android.content.Context")) return false;
        if (count == 1) return true;

        PsiType type1 = parameters[1].getType();
        if (type1 == null || !evaluator.typeMatches(type1, "android.util.AttributeSet")) return false;
        if (count == 2) return true;

        if (count == 3) {
            PsiType type2 = parameters[2].getType();
            return type2 != null && type2.equals(PsiType.INT);
        }

        return false;
    }
}