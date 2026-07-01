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
import com.intellij.psi.PsiType;
import java.util.Collections;
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
                    5,
                    Severity.WARNING,
                    IMPLEMENTATION);

    private static final String VIEW_CLASS = "android.view.View";
    private static final String CONTEXT_CLASS = "android.content.Context";
    private static final String ATTRIBUTE_SET_CLASS = "android.util.AttributeSet";

    public ViewConstructorDetector() {}

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
        if (declaration.isAbstract() || declaration.isInterface()) {
            return;
        }

        boolean hasValidConstructor = false;
        for (UConstructor constructor : declaration.getConstructors()) {
            List<UParameter> parameters = constructor.getUastParameters();
            if (isValidViewConstructor(parameters)) {
                hasValidConstructor = true;
                break;
            }
        }

        if (!hasValidConstructor) {
            context.report(
                    ISSUE,
                    declaration,
                    context.getNameLocation(declaration),
                    "Custom view should have a constructor with one of the following signatures: "
                            + "`View(Context)`, `View(Context, AttributeSet)`, or "
                            + "`View(Context, AttributeSet, int)`");
        }
    }

    private boolean isValidViewConstructor(List<UParameter> parameters) {
        int size = parameters.size();
        if (size < 1 || size > 3) {
            return false;
        }

        PsiType type0 = parameters.get(0).getType();
        if (type0 == null || !CONTEXT_CLASS.equals(type0.getCanonicalText())) {
            return false;
        }

        if (size == 1) {
            return true;
        }

        PsiType type1 = parameters.get(1).getType();
        if (type1 == null || !ATTRIBUTE_SET_CLASS.equals(type1.getCanonicalText())) {
            return false;
        }

        if (size == 2) {
            return true;
        }

        PsiType type2 = parameters.get(2).getType();
        return type2 != null && type2.equalsToText("int");
    }
}