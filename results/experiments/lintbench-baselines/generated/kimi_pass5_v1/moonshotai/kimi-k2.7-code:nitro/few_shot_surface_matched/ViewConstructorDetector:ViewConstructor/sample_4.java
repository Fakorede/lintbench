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
import com.intellij.psi.PsiType;
import java.util.Collections;
import java.util.List;
import org.jetbrains.uast.UClass;

public class ViewConstructorDetector extends Detector implements SourceCodeScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(ViewConstructorDetector.class, Scope.JAVA_FILE_SCOPE);

    private static final String VIEW_CLASS = "android.view.View";
    private static final String CONTEXT_CLASS = "android.content.Context";
    private static final String ATTRIBUTE_SET_CLASS = "android.util.AttributeSet";

    public static final Issue ISSUE =
            Issue.create(
                    "ViewConstructor",
                    "Missing View constructors for XML inflation",
                    "Custom views must provide one of the following constructors so that they can "
                            + "be inflated from XML:\n"
                            + " * `View(Context context)`\n"
                            + " * `View(Context context, AttributeSet attrs)`\n"
                            + " * `View(Context context, AttributeSet attrs, int defStyle)`\n\n"
                            + "If your custom view needs to perform initialization which does not "
                            + "apply when used in a layout editor, you can guard that code with "
                            + "`if (!isInEditMode())`.",
                    Category.USABILITY,
                    6,
                    Severity.WARNING,
                    IMPLEMENTATION)
                    .setAndroidSpecific(true);

    public ViewConstructorDetector() {}

    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList(VIEW_CLASS);
    }

    @Override
    public void visitClass(JavaContext context, UClass declaration) {
        if (VIEW_CLASS.equals(declaration.getQualifiedName())) {
            return;
        }
        if (declaration.isAbstract() || declaration.isInterface()) {
            return;
        }

        for (PsiMethod method : declaration.getMethods()) {
            if (isRequiredConstructor(method)) {
                return;
            }
        }

        context.report(
                ISSUE,
                declaration,
                context.getNameLocation(declaration),
                "Custom view `" + declaration.getName() + "` is missing a constructor for XML inflation. "
                        + "Add one of: `View(Context)`, `View(Context, AttributeSet)`, or "
                        + "`View(Context, AttributeSet, int)`");
    }

    private static boolean isRequiredConstructor(PsiMethod method) {
        if (!method.isConstructor() || !method.hasModifierProperty(PsiModifier.PUBLIC)) {
            return false;
        }

        PsiParameter[] parameters = method.getParameterList().getParameters();
        if (parameters.length == 1) {
            return isType(parameters[0].getType(), CONTEXT_CLASS);
        }
        if (parameters.length == 2) {
            return isType(parameters[0].getType(), CONTEXT_CLASS)
                    && isType(parameters[1].getType(), ATTRIBUTE_SET_CLASS);
        }
        if (parameters.length == 3) {
            return isType(parameters[0].getType(), CONTEXT_CLASS)
                    && isType(parameters[1].getType(), ATTRIBUTE_SET_CLASS)
                    && parameters[2].getType().equals(PsiType.INT);
        }
        return false;
    }

    private static boolean isType(PsiType type, String expectedType) {
        return type != null && type.equalsToText(expectedType);
    }
}