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

    private static final String ANDROID_VIEW = "android.view.View";
    private static final String CONTEXT_CLASS = "android.content.Context";
    private static final String ATTRIBUTE_SET_CLASS = "android.util.AttributeSet";

    @Override
    public List<String> applicableSuperClasses() {
        return Arrays.asList(ANDROID_VIEW);
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        // Skip abstract classes — they don't need to be inflatable directly
        if (declaration.hasModifierProperty("abstract")) {
            return;
        }

        // Skip anonymous classes
        if (declaration.getName() == null || declaration.getName().isEmpty()) {
            return;
        }

        // Check if the class has at least one of the valid View constructors
        PsiMethod[] constructors = declaration.getConstructors();

        // If there are no constructors defined, the class relies on a default constructor,
        // which won't match any of the required signatures.
        // However, if the class has no constructors at all and inherits them, we only flag
        // classes that explicitly define constructors but none of the right kind.
        // Actually, we should flag any non-abstract custom View that doesn't have at least
        // one of the required constructors (whether defined or inherited).

        // Check declared constructors for the valid signatures
        for (PsiMethod constructor : constructors) {
            if (isValidViewConstructor(constructor)) {
                return;
            }
        }

        // If there are no declared constructors, check if a valid one is inherited
        if (constructors.length == 0) {
            // The class has no explicit constructors; it inherits from the superclass.
            // In this case we don't flag it since the parent constructors are available.
            return;
        }

        // The class declares constructors but none of them match the required signatures
        String message =
                "Custom view "
                        + declaration.getName()
                        + " is missing constructor used by tools: "
                        + "`(Context)` or `(Context, AttributeSet)` "
                        + "or `(Context, AttributeSet, int)`";

        context.report(ISSUE, declaration, context.getLocation(declaration.getNameIdentifier() != null
                ? declaration.getNameIdentifier()
                : declaration), message);
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

        // First parameter must be Context
        String firstType = parameters[0].getType().getCanonicalText();
        if (!CONTEXT_CLASS.equals(firstType)) {
            return false;
        }

        if (parameters.length == 1) {
            // View(Context)
            return true;
        }

        // Second parameter must be AttributeSet
        String secondType = parameters[1].getType().getCanonicalText();
        if (!ATTRIBUTE_SET_CLASS.equals(secondType)) {
            return false;
        }

        if (parameters.length == 2) {
            // View(Context, AttributeSet)
            return true;
        }

        // Third parameter must be int
        String thirdType = parameters[2].getType().getCanonicalText();
        if ("int".equals(thirdType)) {
            // View(Context, AttributeSet, int)
            return true;
        }

        return false;
    }
}