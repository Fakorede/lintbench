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
    private static final String CONTEXT = "android.content.Context";
    /** AttributeSet parameter type */
    private static final String ATTRIBUTE_SET = "android.util.AttributeSet";

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

        // Check if the class has at least one of the required constructors
        PsiMethod[] constructors = declaration.getConstructors();

        // If there are no constructors defined, the class relies on the default constructor
        // which doesn't match any of the required signatures
        if (constructors.length == 0) {
            // No constructors defined - report issue
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
     * Checks if the given constructor matches one of the required View constructor signatures:
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
        String firstParamType = parameters[0].getType().getCanonicalText();
        if (!CONTEXT.equals(firstParamType)) {
            return false;
        }

        if (parameters.length == 1) {
            // View(Context)
            return true;
        }

        // Second parameter must be AttributeSet
        String secondParamType = parameters[1].getType().getCanonicalText();
        if (!ATTRIBUTE_SET.equals(secondParamType)) {
            return false;
        }

        if (parameters.length == 2) {
            // View(Context, AttributeSet)
            return true;
        }

        // Third parameter must be int
        String thirdParamType = parameters[2].getType().getCanonicalText();
        if ("int".equals(thirdParamType)) {
            // View(Context, AttributeSet, int)
            return true;
        }

        return false;
    }
}