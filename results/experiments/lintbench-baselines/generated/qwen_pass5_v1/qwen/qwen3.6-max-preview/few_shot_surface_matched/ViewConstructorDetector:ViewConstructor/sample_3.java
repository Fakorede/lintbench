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
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiType;
import java.util.Collections;
import java.util.List;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UMethod;

public class ViewConstructorDetector extends Detector implements SourceCodeScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(ViewConstructorDetector.class, Scope.JAVA_FILE_SCOPE);

    private static final String VIEW_CLASS = "android.view.View";
    private static final String CONTEXT_CLASS = "android.content.Context";
    private static final String ATTRIBUTE_SET_CLASS = "android.util.AttributeSet";

    public static final Issue ISSUE = Issue.create(
            "ViewConstructor",
            "Missing View constructors for XML inflation",
            "Some layout tools (such as the Android layout editor) need to find a "
                    + "constructor with one of the following signatures:\n"
                    + "* `View(Context context)`\n"
                    + "* `View(Context context, AttributeSet attrs)`\n"
                    + "* `View(Context context, AttributeSet attrs, int defStyle)`\n\n"
                    + "If your custom view needs to perform initialization which does "
                    + "not apply when used in a layout editor, you can surround the "
                    + "given code with a check to see if `View#isInEditMode()` is "
                    + "false, since that method will return `false` at runtime but "
                    + "true within a user interface editor.",
            Category.USABILITY,
            3,
            Severity.WARNING,
            IMPLEMENTATION);

    @Nullable
    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList(VIEW_CLASS);
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        if (VIEW_CLASS.equals(declaration.getQualifiedName())) {
            return;
        }

        boolean hasRequiredConstructor = false;
        for (UMethod method : declaration.getMethods()) {
            if (!method.isConstructor()) {
                continue;
            }
            PsiParameter[] parameters = method.getParameterList().getParameters();
            int count = parameters.length;
            if (count == 1 && matchesType(parameters[0], CONTEXT_CLASS)) {
                hasRequiredConstructor = true;
                break;
            } else if (count == 2
                    && matchesType(parameters[0], CONTEXT_CLASS)
                    && matchesType(parameters[1], ATTRIBUTE_SET_CLASS)) {
                hasRequiredConstructor = true;
                break;
            } else if (count == 3
                    && matchesType(parameters[0], CONTEXT_CLASS)
                    && matchesType(parameters[1], ATTRIBUTE_SET_CLASS)
                    && PsiType.INT.equals(parameters[2].getType())) {
                hasRequiredConstructor = true;
                break;
            }
        }

        if (!hasRequiredConstructor) {
            context.report(
                    ISSUE,
                    declaration,
                    context.getNameLocation(declaration),
                    "This custom view should have a constructor taking a `Context` parameter, "
                            + "or a constructor taking `Context` and `AttributeSet` parameters, "
                            + "or a constructor taking `Context`, `AttributeSet` and `int` parameters");
        }
    }

    private static boolean matchesType(@NonNull PsiParameter parameter, @NonNull String fqn) {
        return fqn.equals(parameter.getType().getCanonicalText());
    }
}