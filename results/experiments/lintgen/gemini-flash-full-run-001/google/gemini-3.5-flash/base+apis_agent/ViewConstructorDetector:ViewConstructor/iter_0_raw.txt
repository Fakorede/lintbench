package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiParameterList;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NonNull;
import org.jetbrains.uast.UClass;
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
            "* `View(Context context, AttributeSet attrs, int defStyle)`\n" +
            "\n" +
            "If your custom view needs to perform initialization which does " +
            "not apply when used in a layout editor, you can surround the " +
            "given code with a check to see if `View#isInEditMode()` is " +
            "false, since that method will return `false` at runtime but " +
            "true within a user interface editor.",
            Category.USABILITY,
            3,
            Severity.WARNING,
            new Implementation(
                    ViewConstructorDetector.class,
                    Scope.JAVA_FILE_SCOPE
            )
    );

    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList("android.view.View");
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        if (declaration.isInterface() || declaration.hasModifierProperty(PsiModifier.ABSTRACT)) {
            return;
        }

        if (declaration.getContainingClass() != null && !declaration.hasModifierProperty(PsiModifier.STATIC)) {
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
            context.report(
                    ISSUE,
                    declaration,
                    context.getNameLocation(declaration),
                    "Custom view `" + declaration.getName() + "` is missing recommended constructors"
            );
        }
    }

    private boolean isStandardConstructor(PsiMethod method) {
        PsiParameterList parameterList = method.getParameterList();
        PsiParameter[] parameters = parameterList.getParameters();
        int count = parameters.length;

        if (count < 1 || count > 4) {
            return false;
        }

        if (!parameters[0].getType().equalsToText("android.content.Context")) {
            return false;
        }

        if (count >= 2) {
            if (!parameters[1].getType().equalsToText("android.util.AttributeSet")) {
                return false;
            }
        }

        if (count >= 3) {
            if (!PsiType.INT.equals(parameters[2].getType())) {
                return false;
            }
        }

        if (count == 4) {
            if (!PsiType.INT.equals(parameters[3].getType())) {
                return false;
            }
        }

        return true;
    }
}