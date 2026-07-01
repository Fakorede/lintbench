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
import com.intellij.psi.PsiParameterList;
import java.util.Collections;
import java.util.List;
import org.jetbrains.uast.UClass;

public class ViewConstructorDetector extends Detector implements SourceCodeScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(ViewConstructorDetector.class, Scope.JAVA_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "ViewConstructor",
                    "Missing View constructors for XML inflation",
                    "Some layout tools (such as the Android layout editor) need to find a "
                            + "constructor with one of the following signatures:\n"
                            + " * `View(Context context)`\n"
                            + " * `View(Context context, AttributeSet attrs)`\n"
                            + " * `View(Context context, AttributeSet attrs, int defStyle)`\n\n"
                            + "If your custom view needs to perform initialization which does "
                            + "not apply when used in a layout editor, you can surround the "
                            + "given code with a check to see if `View#isInEditMode()` is "
                            + "false, since that method will return `false` at runtime but "
                            + "true within a user interface editor.",
                    Category.USABILITY,
                    3,
                    Severity.WARNING,
                    IMPLEMENTATION);

    public ViewConstructorDetector() {}

    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList("android.view.View");
    }

    @Override
    public void visitClass(JavaContext context, UClass declaration) {
        if (declaration.hasModifierProperty(PsiModifier.ABSTRACT)) {
            return;
        }

        if (declaration.hasModifierProperty(PsiModifier.PRIVATE)) {
            return;
        }

        PsiClass containingClass = declaration.getContainingClass();
        if (containingClass != null && !declaration.hasModifierProperty(PsiModifier.STATIC)) {
            return;
        }

        PsiMethod[] constructors = declaration.getConstructors();
        if (constructors.length == 0) {
            return;
        }

        boolean hasStandardConstructor = false;
        for (PsiMethod constructor : constructors) {
            if (isStandardConstructor(constructor)) {
                hasStandardConstructor = true;
                break;
            }
        }

        if (!hasStandardConstructor) {
            String message = String.format(
                    "Custom view `%1$s` is missing recommended constructors",
                    declaration.getName());
            context.report(
                    ISSUE,
                    declaration,
                    context.getNameLocation(declaration),
                    message);
        }
    }

    private boolean isStandardConstructor(PsiMethod constructor) {
        PsiParameterList parameterList = constructor.getParameterList();
        int count = parameterList.getParametersCount();
        if (count < 1 || count > 4) {
            return false;
        }

        PsiParameter[] parameters = parameterList.getParameters();

        if (!isType(parameters[0], "android.content.Context")) {
            return false;
        }

        if (count >= 2) {
            if (!isType(parameters[1], "android.util.AttributeSet")) {
                return false;
            }
        }

        if (count >= 3) {
            if (!isType(parameters[2], "int")) {
                return false;
            }
        }

        if (count == 4) {
            if (!isType(parameters[3], "int")) {
                return false;
            }
        }

        return true;
    }

    private boolean isType(PsiParameter parameter, String typeName) {
        return parameter.getType().getCanonicalText().equals(typeName);
    }
}