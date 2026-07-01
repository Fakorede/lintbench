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

        // Check if the class directly defines any constructors
        PsiMethod[] constructors = psiClass.getConstructors();
        if (constructors.length == 0) {
            // No constructors defined; the default constructor is used, which is fine
            // Actually, if no constructors are defined, the compiler generates a no-arg
            // constructor, which won't work for XML inflation. But we only flag classes
            // that define constructors but none of the required ones.
            return;
        }

        // Check if any of the required constructors exist
        if (hasValidViewConstructor(constructors)) {
            return;
        }

        // Report the issue on the class declaration
        context.report(
                ISSUE,
                declaration,
                context.getNameLocation(declaration),
                "Custom view `" + declaration.getName() + "` is missing constructor used by " +
                "tools: `(Context)` or `(Context, AttributeSet)` or " +
                "`(Context, AttributeSet, int)`"
        );
    }

    private boolean hasValidViewConstructor(PsiMethod[] constructors) {
        for (PsiMethod constructor : constructors) {
            PsiParameterList parameterList = constructor.getParameterList();
            PsiParameter[] parameters = parameterList.getParameters();

            if (parameters.length == 1) {
                // View(Context context)
                if (isContext(parameters[0].getType())) {
                    return true;
                }
            } else if (parameters.length == 2) {
                // View(Context context, AttributeSet attrs)
                if (isContext(parameters[0].getType()) &&
                        isAttributeSet(parameters[1].getType())) {
                    return true;
                }
            } else if (parameters.length == 3) {
                // View(Context context, AttributeSet attrs, int defStyle)
                if (isContext(parameters[0].getType()) &&
                        isAttributeSet(parameters[1].getType()) &&
                        isInt(parameters[2].getType())) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isContext(PsiType type) {
        return typeInheritsFrom(type, CONTEXT_CLASS);
    }

    private boolean isAttributeSet(PsiType type) {
        return typeInheritsFrom(type, ATTRIBUTE_SET_CLASS);
    }

    private boolean isInt(PsiType type) {
        return type.equals(PsiType.INT) ||
                type.getCanonicalText().equals("int");
    }

    private boolean typeInheritsFrom(PsiType type, String qualifiedName) {
        String canonicalText = type.getCanonicalText();
        if (canonicalText.equals(qualifiedName)) {
            return true;
        }
        // Check superclasses/interfaces via resolved class
        if (type instanceof com.intellij.psi.PsiClassType) {
            com.intellij.psi.PsiClassType classType = (com.intellij.psi.PsiClassType) type;
            PsiClass psiClass = classType.resolve();
            if (psiClass != null) {
                return checkClassInheritsFrom(psiClass, qualifiedName);
            }
        }
        return false;
    }

    private boolean checkClassInheritsFrom(PsiClass psiClass, String qualifiedName) {
        if (qualifiedName.equals(psiClass.getQualifiedName())) {
            return true;
        }
        for (PsiClass superClass : psiClass.getSupers()) {
            if (checkClassInheritsFrom(superClass, qualifiedName)) {
                return true;
            }
        }
        return false;
    }
}