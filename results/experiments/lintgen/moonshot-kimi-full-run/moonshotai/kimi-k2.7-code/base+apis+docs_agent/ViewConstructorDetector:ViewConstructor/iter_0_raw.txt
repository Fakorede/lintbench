package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiType;
import java.util.Collections;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.UClass;

public class ViewConstructorDetector extends Detector implements SourceCodeScanner {

    private static final String VIEW_CLASS = "android.view.View";

    public static final Issue ISSUE = Issue.create(
            "ViewConstructor",
            "Missing View constructors for XML inflation",
            "Some layout tools (such as the Android layout editor) need to find a constructor with one of the following signatures:\n"
                    + "* `View(Context context)`\n"
                    + "* `View(Context context, AttributeSet attrs)`\n"
                    + "* `View(Context context, AttributeSet attrs, int defStyle)`\n\n"
                    + "If your custom view needs to perform initialization which does not apply when used in a layout editor, "
                    + "you can surround the given code with a check to see if `View#isInEditMode()` is false, since that method "
                    + "will return `false` at runtime but `true` within a user interface editor.",
            Category.CORRECTNESS,
            3,
            Severity.WARNING,
            new Implementation(ViewConstructorDetector.class, Scope.JAVA_FILE_SCOPE));

    @Nullable
    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList(VIEW_CLASS);
    }

    @Override
    public void visitClass(@NotNull JavaContext context, @NotNull UClass declaration) {
        if (declaration.getName() == null
                || declaration.isInterface()
                || declaration.isEnum()
                || declaration.isAnnotationType()
                || declaration.hasModifierProperty(PsiModifier.ABSTRACT)) {
            return;
        }

        for (PsiMethod constructor : declaration.getConstructors()) {
            if (!constructor.hasModifierProperty(PsiModifier.PUBLIC)
                    && !constructor.hasModifierProperty(PsiModifier.PROTECTED)) {
                continue;
            }

            PsiParameter[] parameters = constructor.getParameterList().getParameters();
            int count = parameters.length;
            if (count < 1 || count > 3) {
                continue;
            }

            if (matchesViewConstructor(context, parameters)) {
                return;
            }
        }

        String message = String.format(
                "Custom view %s is missing constructor used by tools: (Context context), "
                        + "(Context context, AttributeSet attrs), or "
                        + "(Context context, AttributeSet attrs, int defStyle)",
                declaration.getName());
        context.report(ISSUE, declaration, context.getNameLocation(declaration), message);
    }

    private static boolean matchesViewConstructor(@NotNull JavaContext context,
            @NotNull PsiParameter[] parameters) {
        for (int i = 0; i < parameters.length; i++) {
            PsiParameter parameter = parameters[i];
            switch (i) {
                case 0:
                    if (!isType(context, parameter, "android.content.Context")) {
                        return false;
                    }
                    break;
                case 1:
                    if (!isType(context, parameter, "android.util.AttributeSet")) {
                        return false;
                    }
                    break;
                case 2:
                    if (!PsiType.INT.equals(parameter.getType())) {
                        return false;
                    }
                    break;
                default:
                    return false;
            }
        }
        return true;
    }

    private static boolean isType(@NotNull JavaContext context,
            @NotNull PsiParameter parameter, @NotNull String qualifiedName) {
        PsiClass typeClass = context.getEvaluator().getTypeClass(parameter.getType());
        return typeClass != null && qualifiedName.equals(typeClass.getQualifiedName());
    }
}