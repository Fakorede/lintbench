package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiParameterList;
import java.util.Arrays;
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
                            + "* `View(Context context)`\n"
                            + "* `View(Context context, AttributeSet attrs)`\n"
                            + "* `View(Context context, AttributeSet attrs, int defStyle)`\n"
                            + "\n"
                            + "If your custom view needs to perform initialization which does "
                            + "not apply when used in a layout editor, you can surround the "
                            + "given code with a check to see if `View#isInEditMode()` is "
                            + "false, since that method will return `false` at runtime but "
                            + "true within a user interface editor.",
                    Category.USABILITY,
                    3,
                    Severity.WARNING,
                    IMPLEMENTATION);

    /** Context parameter type */
    private static final String CONTEXT_TYPE = "android.content.Context";

    /** AttributeSet parameter type */
    private static final String ATTRIBUTE_SET_TYPE = "android.util.AttributeSet";

    @Override
    public List<String> applicableSuperClasses() {
        return Arrays.asList("android.view.View", "android.view.ViewGroup");
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        // Skip abstract classes - they don't need to be inflatable
        if (declaration.isInterface()) {
            return;
        }

        // Check if the class is abstract
        if (isAbstract(declaration)) {
            return;
        }

        // Check if the class is anonymous
        if (declaration.getName() == null) {
            return;
        }

        // Get all constructors
        PsiMethod[] constructors = declaration.getConstructors();

        // If there are no constructors, the default constructor (no args) is used,
        // which is not valid for XML inflation
        if (constructors.length == 0) {
            // No explicit constructors - default constructor won't work for XML inflation
            // But only report if the class itself defines no constructors
            // (inherited constructors might be fine)
            context.report(
                    ISSUE,
                    declaration,
                    context.getNameLocation(declaration),
                    "Custom view `"
                            + declaration.getName()
                            + "` is missing constructor used by tools: "
                            + "`(Context)` or `(Context, AttributeSet)` "
                            + "or `(Context, AttributeSet, int)`");
            return;
        }

        // Check if any of the constructors match the required signatures
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
                    "Custom view `"
                            + declaration.getName()
                            + "` is missing constructor used by tools: "
                            + "`(Context)` or `(Context, AttributeSet)` "
                            + "or `(Context, AttributeSet, int)`");
        }
    }

    /**
     * Checks if the given constructor matches one of the valid View constructor signatures:
     * - View(Context context)
     * - View(Context context, AttributeSet attrs)
     * - View(Context context, AttributeSet attrs, int defStyle)
     */
    private boolean isValidViewConstructor(@NonNull PsiMethod constructor) {
        PsiParameterList parameterList = constructor.getParameterList();
        PsiParameter[] parameters = parameterList.getParameters();

        if (parameters.length == 0) {
            return false;
        }

        // First parameter must be Context
        if (!isType(parameters[0], CONTEXT_TYPE)) {
            return false;
        }

        if (parameters.length == 1) {
            // View(Context context)
            return true;
        }

        // Second parameter must be AttributeSet
        if (!isType(parameters[1], ATTRIBUTE_SET_TYPE)) {
            return false;
        }

        if (parameters.length == 2) {
            // View(Context context, AttributeSet attrs)
            return true;
        }

        // Third parameter must be int
        if (parameters.length == 3) {
            String thirdType = parameters[2].getType().getCanonicalText();
            // View(Context context, AttributeSet attrs, int defStyle)
            return "int".equals(thirdType);
        }

        return false;
    }

    /**
     * Checks if a parameter has the given fully qualified type name.
     */
    private boolean isType(@NonNull PsiParameter parameter, @NonNull String typeName) {
        String canonicalText = parameter.getType().getCanonicalText();
        return typeName.equals(canonicalText);
    }

    /**
     * Checks if the class is abstract.
     */
    private boolean isAbstract(@NonNull UClass declaration) {
        com.intellij.psi.PsiModifierList modifierList = declaration.getModifierList();
        if (modifierList != null) {
            return modifierList.hasModifierProperty(com.intellij.psi.PsiModifier.ABSTRACT);
        }
        return false;
    }
}