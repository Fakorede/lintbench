package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiParameterList;
import org.jetbrains.uast.UClass;

import java.util.Collections;
import java.util.List;

public class ViewConstructorDetector extends Detector implements Detector.UastScanner {

    public static final Issue ISSUE = Issue.create(
            "ViewConstructor",
            "Missing View constructors for XML inflation",
            "Some layout tools (such as the Android layout editor) need to find a " +
            "constructor with one of the following signatures:\n" +
            " * `View(Context context)`\n" +
            " * `View(Context context, AttributeSet attrs)`\n" +
            " * `View(Context context, AttributeSet attrs, int defStyle)`\n" +
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
                    Scope.JAVA_FILE_SCOPE));

    private static final String CONTEXT_CLASS = "android.content.Context";
    private static final String ATTRIBUTE_SET_CLASS = "android.util.AttributeSet";
    private static final String VIEW_CLASS = "android.view.View";

    public ViewConstructorDetector() {
    }

    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList(VIEW_CLASS);
    }

    @Override
    public void visitClass(JavaContext context, UClass declaration) {
        // Skip anonymous classes
        if (declaration.getName() == null) {
            return;
        }

        // Skip abstract classes
        PsiClass psiClass = declaration.getJavaPsi();
        if (psiClass.hasModifierProperty(com.intellij.psi.PsiModifier.ABSTRACT)) {
            return;
        }

        // Check if the class has at least one of the required constructors
        PsiMethod[] constructors = psiClass.getConstructors();

        // If there are no constructors defined, the default constructor exists
        // but it won't match any of the required signatures
        if (constructors.length == 0) {
            // No explicit constructors - report issue
            context.report(
                    ISSUE,
                    declaration,
                    context.getNameLocation(declaration),
                    "Custom view `" + declaration.getName() + "` is missing constructor " +
                    "used by tools: `(Context)` or `(Context, AttributeSet)` " +
                    "or `(Context, AttributeSet, int)`");
            return;
        }

        boolean hasValidConstructor = false;

        for (PsiMethod constructor : constructors) {
            if (isValidViewConstructor(constructor)) {
                hasValidConstructor = true;
                break;
            }
        }

        if (!hasValidConstructor) {
            context.report(
                    ISSUE,
                    declaration,
                    context.getNameLocation(declaration),
                    "Custom view `" + declaration.getName() + "` is missing constructor " +
                    "used by tools: `(Context)` or `(Context, AttributeSet)` " +
                    "or `(Context, AttributeSet, int)`");
        }
    }

    private boolean isValidViewConstructor(PsiMethod constructor) {
        PsiParameterList parameterList = constructor.getParameterList();
        PsiParameter[] parameters = parameterList.getParameters();

        if (parameters.length == 0) {
            return false;
        }

        // First parameter must be Context
        String firstParamType = parameters[0].getType().getCanonicalText();
        if (!isContextType(firstParamType)) {
            return false;
        }

        if (parameters.length == 1) {
            // View(Context context)
            return true;
        }

        // Second parameter must be AttributeSet
        String secondParamType = parameters[1].getType().getCanonicalText();
        if (!isAttributeSetType(secondParamType)) {
            return false;
        }

        if (parameters.length == 2) {
            // View(Context context, AttributeSet attrs)
            return true;
        }

        // Third parameter must be int
        String thirdParamType = parameters[2].getType().getCanonicalText();
        if (parameters.length == 3 && thirdParamType.equals("int")) {
            // View(Context context, AttributeSet attrs, int defStyle)
            return true;
        }

        return false;
    }

    private boolean isContextType(String typeName) {
        return CONTEXT_CLASS.equals(typeName) ||
               typeName.endsWith(".Context") ||
               "Context".equals(typeName);
    }

    private boolean isAttributeSetType(String typeName) {
        return ATTRIBUTE_SET_CLASS.equals(typeName) ||
               typeName.endsWith(".AttributeSet") ||
               "AttributeSet".equals(typeName);
    }
}