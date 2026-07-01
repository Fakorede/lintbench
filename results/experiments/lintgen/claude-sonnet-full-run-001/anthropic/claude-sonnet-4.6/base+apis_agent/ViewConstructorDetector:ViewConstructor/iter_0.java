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
        if (psiClass == null) {
            return;
        }

        // Skip abstract classes
        if (psiClass.hasModifierProperty(com.intellij.psi.PsiModifier.ABSTRACT)) {
            return;
        }

        // Skip anonymous classes
        if (psiClass.getName() == null) {
            return;
        }

        // Check if the class directly extends View or a subclass
        // We only want to check classes that are custom views (not framework views)
        String qualifiedName = psiClass.getQualifiedName();
        if (qualifiedName != null && qualifiedName.startsWith("android.")) {
            return;
        }

        // Check constructors
        PsiMethod[] constructors = psiClass.getConstructors();

        // If there are no constructors defined, the default constructor is used,
        // which doesn't match any of the required signatures
        if (constructors.length == 0) {
            // No constructors defined - report issue
            context.report(
                    ISSUE,
                    declaration,
                    context.getNameLocation(declaration),
                    "Custom view `" + psiClass.getName() + "` is missing constructor used by tools: " +
                    "`(Context)` or `(Context, AttributeSet)` or `(Context, AttributeSet, int)`"
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
                    "Custom view `" + psiClass.getName() + "` is missing constructor used by tools: " +
                    "`(Context)` or `(Context, AttributeSet)` or `(Context, AttributeSet, int)`"
            );
        }
    }

    private boolean isValidViewConstructor(PsiMethod constructor) {
        PsiParameterList parameterList = constructor.getParameterList();
        PsiParameter[] parameters = parameterList.getParameters();

        if (parameters.length == 0) {
            return false;
        }

        // Check first parameter is Context
        if (!isContextType(parameters[0].getType())) {
            return false;
        }

        if (parameters.length == 1) {
            // View(Context context)
            return true;
        }

        // Check second parameter is AttributeSet
        if (!isAttributeSetType(parameters[1].getType())) {
            return false;
        }

        if (parameters.length == 2) {
            // View(Context context, AttributeSet attrs)
            return true;
        }

        // Check third parameter is int
        if (parameters.length == 3) {
            PsiType thirdType = parameters[2].getType();
            if (PsiType.INT.equals(thirdType)) {
                // View(Context context, AttributeSet attrs, int defStyle)
                return true;
            }
        }

        return false;
    }

    private boolean isContextType(PsiType type) {
        String canonicalText = type.getCanonicalText();
        return CONTEXT_CLASS.equals(canonicalText) ||
               canonicalText.endsWith(".Context") ||
               isSubtypeOf(type, CONTEXT_CLASS);
    }

    private boolean isAttributeSetType(PsiType type) {
        String canonicalText = type.getCanonicalText();
        return ATTRIBUTE_SET_CLASS.equals(canonicalText) ||
               canonicalText.endsWith(".AttributeSet") ||
               isSubtypeOf(type, ATTRIBUTE_SET_CLASS);
    }

    private boolean isSubtypeOf(PsiType type, String className) {
        if (type instanceof com.intellij.psi.PsiClassType) {
            com.intellij.psi.PsiClassType classType = (com.intellij.psi.PsiClassType) type;
            PsiClass psiClass = classType.resolve();
            if (psiClass != null) {
                return isClassOrSubclass(psiClass, className);
            }
        }
        return false;
    }

    private boolean isClassOrSubclass(PsiClass psiClass, String targetClassName) {
        if (psiClass == null) {
            return false;
        }
        String qualifiedName = psiClass.getQualifiedName();
        if (targetClassName.equals(qualifiedName)) {
            return true;
        }
        // Check superclass
        PsiClass superClass = psiClass.getSuperClass();
        if (superClass != null && !superClass.equals(psiClass)) {
            return isClassOrSubclass(superClass, targetClassName);
        }
        // Check interfaces
        for (PsiClass iface : psiClass.getInterfaces()) {
            if (isClassOrSubclass(iface, targetClassName)) {
                return true;
            }
        }
        return false;
    }
}