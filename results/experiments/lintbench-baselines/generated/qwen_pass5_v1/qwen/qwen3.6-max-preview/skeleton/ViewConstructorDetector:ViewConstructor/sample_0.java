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
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiType;
import java.util.Arrays;
import java.util.List;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UConstructor;
import org.jetbrains.uast.UParameter;

public class ViewConstructorDetector extends Detector implements SourceCodeScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(ViewConstructorDetector.class, Scope.JAVA_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "ViewConstructor",
                    "Missing View constructors for XML inflation",
                    "Some layout tools (such as the Android layout editor) need to find a constructor with one of the following signatures:\n" +
                    "* `View(Context context)`\n" +
                    "* `View(Context context, AttributeSet attrs)`\n" +
                    "* `View(Context context, AttributeSet attrs, int defStyle)`\n\n" +
                    "If your custom view needs to perform initialization which does not apply when used in a layout editor, you can surround the given code with a check to see if `View#isInEditMode()` is false, since that method will return `false` at runtime but true within a user interface editor.",
                    Category.USABILITY,
                    3,
                    Severity.WARNING,
                    IMPLEMENTATION);

    @Override
    public List<String> applicableSuperClasses() {
        return Arrays.asList("android.view.View");
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        if (declaration.isInterface() || declaration.hasModifier(PsiModifier.ABSTRACT)) {
            return;
        }

        boolean hasValidConstructor = false;
        for (UConstructor constructor : declaration.getConstructors()) {
            if (isValidConstructor(constructor.getUastParameters())) {
                hasValidConstructor = true;
                break;
            }
        }

        if (!hasValidConstructor) {
            context.report(
                    ISSUE,
                    declaration,
                    context.getNameLocation(declaration),
                    "Custom view `%1$s` is missing constructor used by tools: `(Context)` or `(Context,AttributeSet)` or `(Context,AttributeSet,int)`",
                    declaration.getName());
        }
    }

    private static boolean isValidConstructor(List<UParameter> parameters) {
        int count = parameters.size();
        if (count == 1) {
            return isType(parameters.get(0), "android.content.Context");
        } else if (count == 2) {
            return isType(parameters.get(0), "android.content.Context")
                    && isType(parameters.get(1), "android.util.AttributeSet");
        } else if (count == 3) {
            return isType(parameters.get(0), "android.content.Context")
                    && isType(parameters.get(1), "android.util.AttributeSet")
                    && isType(parameters.get(2), "int");
        }
        return false;
    }

    private static boolean isType(UParameter parameter, String qualifiedName) {
        PsiType type = parameter.getType();
        return type != null && qualifiedName.equals(type.getCanonicalText());
    }
}