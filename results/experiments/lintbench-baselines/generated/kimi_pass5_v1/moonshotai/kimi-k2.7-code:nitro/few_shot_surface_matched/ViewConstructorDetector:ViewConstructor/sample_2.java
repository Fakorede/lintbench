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
import java.util.Collections;
import java.util.List;
import org.jetbrains.uast.UClass;

public class ViewConstructorDetector extends Detector implements SourceCodeScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(ViewConstructorDetector.class, Scope.JAVA_FILE_SCOPE);

    private static final String ANDROID_VIEW_CLASS = "android.view.View";

    public static final Issue VIEW_CONSTRUCTOR =
            Issue.create(
                            "ViewConstructor",
                            "Missing View constructors for XML inflation",
                            "Some layout tools (such as the Android layout editor) need to find a "
                                    + "constructor with one of the following signatures:\n"
                                    + "* `View(Context context)`\n"
                                    + "* `View(Context context, AttributeSet attrs)`\n"
                                    + "* `View(Context context, AttributeSet attrs, int defStyle)`\n"
                                    + "If your custom view needs to perform initialization which does "
                                    + "not apply when used in a layout editor, you can surround the "
                                    + "given code with a check to see if `View#isInEditMode()` is "
                                    + "false, since that method will return `false` at runtime but "
                                    + "true within a user interface editor.",
                            Category.USABILITY,
                            5,
                            Severity.WARNING,
                            IMPLEMENTATION)
                    .setAndroidSpecific(true);

    public ViewConstructorDetector() {}

    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList(ANDROID_VIEW_CLASS);
    }

    @Override
    public void visitClass(JavaContext context, UClass declaration) {
        if (declaration.isInterface() || declaration.hasModifierProperty(PsiModifier.ABSTRACT)) {
            return;
        }

        boolean hasInflationConstructor = false;
        for (PsiMethod constructor : declaration.getConstructors()) {
            PsiParameterList parameterList = constructor.getParameterList();
            PsiParameter[] parameters = parameterList.getParameters();
            if (parameters.length == 1 && isContext(parameters[0])) {
                hasInflationConstructor = true;
                break;
            } else if (parameters.length == 2
                    && isContext(parameters[0])
                    && isAttributeSet(parameters[1])) {
                hasInflationConstructor = true;
                break;
            } else if (parameters.length == 3
                    && isContext(parameters[0])
                    && isAttributeSet(parameters[1])
                    && isInt(parameters[2])) {
                hasInflationConstructor = true;
                break;
            }
        }

        if (hasInflationConstructor) {
            return;
        }

        context.report(
                VIEW_CONSTRUCTOR,
                declaration,
                context.getNameLocation(declaration),
                "Custom view `"
                        + declaration.getName()
                        + "` is missing a constructor with one of the required signatures: "
                        + "(Context), (Context, AttributeSet), or (Context, AttributeSet, int)");
    }

    private static boolean isContext(PsiParameter parameter) {
        return parameter.getType().equalsToText("android.content.Context");
    }

    private static boolean isAttributeSet(PsiParameter parameter) {
        return parameter.getType().equalsToText("android.util.AttributeSet");
    }

    private static boolean isInt(PsiParameter parameter) {
        return parameter.getType().equalsToText("int");
    }
}