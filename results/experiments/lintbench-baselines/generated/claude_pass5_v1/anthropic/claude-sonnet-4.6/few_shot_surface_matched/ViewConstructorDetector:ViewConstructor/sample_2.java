package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
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

    private static final String ANDROID_VIEW = "android.view.View";
    private static final String CONTEXT_CLASS = "android.content.Context";
    private static final String ATTRIBUTE_SET_CLASS = "android.util.AttributeSet";

    public static final Issue ISSUE =
            Issue.create(
                            "ViewConstructor",
                            "Missing View constructors for XML inflation",
                            "Some layout tools (such as the Android layout editor) need to find a "
                                    + "constructor with one of the following signatures:\n"
                                    + " * `View(Context context)`\n"
                                    + " * `View(Context context, AttributeSet attrs)`\n"
                                    + " * `View(Context context, AttributeSet attrs, int defStyle)`\n"
                                    + "\n"
                                    + "If your custom view needs to perform initialization which does "
                                    + "not apply when used in a layout editor, you can surround the "
                                    + "given code with a check to see if `View#isInEditMode()` is "
                                    + "false, since that method will return `false` at runtime but "
                                    + "true within a user interface editor.",
                            Category.USABILITY,
                            3,
                            Severity.WARNING,
                            IMPLEMENTATION)
                    .setAndroidSpecific(true);

    public ViewConstructorDetector() {}

    @Nullable
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

        // Skip the View class itself
        if (ANDROID_VIEW.equals(declaration.getQualifiedName())) {
            return;
        }

        // Check if the class has at least one of the valid View constructors
        for (PsiMethod method : declaration.getConstructors()) {
            if (isValidViewConstructor(method)) {
                return;
            }
        }

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

    private boolean isValidViewConstructor(@NonNull PsiMethod constructor) {
        PsiParameterList parameterList = constructor.getParameterList();
        PsiParameter[] parameters = parameterList.getParameters();

        if (parameters.length == 1) {
            return typeMatches(parameters[0], CONTEXT_CLASS);
        } else if (parameters.length == 2) {
            return typeMatches(parameters[0], CONTEXT_CLASS)
                    && typeMatches(parameters[1], ATTRIBUTE_SET_CLASS);
        } else if (parameters.length == 3) {
            return typeMatches(parameters[0], CONTEXT_CLASS)
                    && typeMatches(parameters[1], ATTRIBUTE_SET_CLASS)
                    && isIntType(parameters[2]);
        }

        return false;
    }

    private boolean typeMatches(@NonNull PsiParameter parameter, @NonNull String typeFqn) {
        String canonicalText = parameter.getType().getCanonicalText();
        return typeFqn.equals(canonicalText);
    }

    private boolean isIntType(@NonNull PsiParameter parameter) {
        String canonicalText = parameter.getType().getCanonicalText();
        return "int".equals(canonicalText);
    }
}