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
        // Skip abstract classes - they don't need to be inflated directly
        if (declaration.hasModifierProperty("abstract")) {
            return;
        }

        // Skip anonymous classes
        if (declaration.getName() == null) {
            return;
        }

        // Check if the class has at least one valid constructor for XML inflation
        PsiMethod[] constructors = declaration.getConstructors();

        // If no constructors are defined, the default constructor exists but it's not valid
        // for XML inflation
        if (constructors.length == 0) {
            // No explicit constructors - report issue
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

        // Check if any constructor matches the valid signatures
        for (PsiMethod constructor : constructors) {
            if (isValidViewConstructor(constructor)) {
                return;
            }
        }

        // No valid constructor found - report the issue
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

    /**
     * Returns true if the given constructor matches one of the valid View constructor signatures:
     * - View(Context)
     * - View(Context, AttributeSet)
     * - View(Context, AttributeSet, int)
     */
    private boolean isValidViewConstructor(@NonNull PsiMethod constructor) {
        PsiParameterList parameterList = constructor.getParameterList();
        PsiParameter[] parameters = parameterList.getParameters();

        if (parameters.length == 0 || parameters.length > 3) {
            return false;
        }

        // First parameter must always be Context
        String firstType = parameters[0].getType().getCanonicalText();
        if (!firstType.equals(CONTEXT_TYPE)) {
            return false;
        }

        if (parameters.length == 1) {
            // View(Context)
            return true;
        }

        // Second parameter must be AttributeSet
        String secondType = parameters[1].getType().getCanonicalText();
        if (!secondType.equals(ATTRIBUTE_SET_TYPE)) {
            return false;
        }

        if (parameters.length == 2) {
            // View(Context, AttributeSet)
            return true;
        }

        // Third parameter must be int
        String thirdType = parameters[2].getType().getCanonicalText();
        if (thirdType.equals("int")) {
            // View(Context, AttributeSet, int)
            return true;
        }

        return false;
    }
}