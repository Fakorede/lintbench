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
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiParameterList;
import com.intellij.psi.PsiType;

import org.jetbrains.uast.UClass;

import java.util.Collections;
import java.util.List;

public class ViewConstructorDetector extends Detector implements SourceCodeScanner {

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
                    Scope.JAVA_FILE_SCOPE
            )
    );

    private static final String ANDROID_VIEW = "android.view.View";
    private static final String CONTEXT_CLASS = "android.content.Context";
    private static final String ATTRIBUTE_SET_CLASS = "android.util.AttributeSet";

    public ViewConstructorDetector() {
    }

    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList(ANDROID_VIEW);
    }

    @Override
    public void visitClass(JavaContext context, UClass declaration) {
        // Skip abstract classes
        if (declaration.isInterface()) {
            return;
        }

        PsiClass psiClass = declaration.getJavaPsi();

        if (psiClass.hasModifierProperty(com.intellij.psi.PsiModifier.ABSTRACT)) {
            return;
        }

        // Skip anonymous classes
        if (psiClass instanceof com.intellij.psi.PsiAnonymousClass) {
            return;
        }

        // Check if the class directly extends View or a subclass
        // We only want to check classes that are custom views (not framework views)
        String qualifiedName = psiClass.getQualifiedName();
        if (qualifiedName != null && qualifiedName.startsWith("android.")) {
            return;
        }

        PsiMethod[] constructors = psiClass.getConstructors();

        // If there are no constructors defined, the default constructor is used,
        // which doesn't match any of the required signatures
        if (constructors.length == 0) {
            context.report(
                    ISSUE,
                    declaration,
                    context.getNameLocation(declaration),
                    "Custom view `" + declaration.getName() + "` is missing constructor " +
                    "used by tools: `(Context)` or `(Context, AttributeSet)` " +
                    "or `(Context, AttributeSet, int)`"
            );
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
                    "or `(Context, AttributeSet, int)`"
            );
        }
    }

    private boolean isValidViewConstructor(PsiMethod constructor) {
        PsiParameterList parameterList = constructor.getParameterList();
        PsiParameter[] parameters = parameterList.getParameters();

        if (parameters.length == 0 || parameters.length > 3) {
            return false;
        }

        // First parameter must be Context
        if (!isOfType(parameters[0], CONTEXT_CLASS)) {
            return false;
        }

        if (parameters.length == 1) {
            // View(Context context)
            return true;
        }

        // Second parameter must be AttributeSet
        if (!isOfType(parameters[1], ATTRIBUTE_SET_CLASS)) {
            return false;
        }

        if (parameters.length == 2) {
            // View(Context context, AttributeSet attrs)
            return true;
        }

        // Third parameter must be int
        PsiType thirdParamType = parameters[2].getType();
        if (parameters.length == 3 && thirdParamType.equals(PsiType.INT)) {
            // View(Context context, AttributeSet attrs, int defStyle)
            return true;
        }

        return false;
    }

    private boolean isOfType(PsiParameter parameter, String qualifiedClassName) {
        PsiType type = parameter.getType();
        String canonicalText = type.getCanonicalText();

        if (canonicalText.equals(qualifiedClassName)) {
            return true;
        }

        // Also check simple name match for cases where imports are used
        int dotIndex = qualifiedClassName.lastIndexOf('.');
        if (dotIndex >= 0) {
            String simpleName = qualifiedClassName.substring(dotIndex + 1);
            if (canonicalText.equals(simpleName)) {
                return true;
            }
        }

        return false;
    }
}