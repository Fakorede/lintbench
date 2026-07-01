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
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiParameter;
import java.util.Collections;
import java.util.List;
import org.jetbrains.uast.UClass;

public class ViewConstructorDetector extends Detector implements SourceCodeScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(ViewConstructorDetector.class, Scope.JAVA_FILE_SCOPE);

    private static final String VIEW_CLASS = "android.view.View";

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
                                    + "given code with a check to see if `View#isInEditMode()` is false, "
                                    + "since that method will return `false` at runtime but true within "
                                    + "a user interface editor.",
                            Category.USABILITY,
                            6,
                            Severity.WARNING,
                            IMPLEMENTATION)
                    .setAndroidSpecific(true);

    public ViewConstructorDetector() {}

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        if (VIEW_CLASS.equals(declaration.getQualifiedName())) {
            return;
        }
        if (declaration.isInterface() || declaration.isEnum()) {
            return;
        }
        if (declaration.getModifierList() != null
                && declaration.getModifierList().hasModifierProperty(PsiModifier.ABSTRACT)) {
            return;
        }
        if (!context.getEvaluator().isInheritor(declaration, VIEW_CLASS, true)) {
            return;
        }

        String className = declaration.getName();
        for (PsiMethod method : declaration.getMethods()) {
            if (method.isConstructor() && className != null && className.equals(method.getName())) {
                if (isApplicableConstructor(method)) {
                    return;
                }
            }
        }

        context.report(
                ISSUE,
                declaration,
                context.getNameLocation(declaration),
                "Missing View constructors for XML inflation");
    }

    @Nullable
    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList(VIEW_CLASS);
    }

    private static boolean isApplicableConstructor(@NonNull PsiMethod method) {
        PsiParameter[] parameters = method.getParameterList().getParameters();
        if (parameters.length == 1) {
            return "android.content.Context".equals(parameters[0].getType().getCanonicalText());
        } else if (parameters.length == 2) {
            return "android.content.Context".equals(parameters[0].getType().getCanonicalText())
                    && "android.util.AttributeSet".equals(parameters[1].getType().getCanonicalText());
        } else if (parameters.length == 3) {
            return "android.content.Context".equals(parameters[0].getType().getCanonicalText())
                    && "android.util.AttributeSet".equals(parameters[1].getType().getCanonicalText())
                    && "int".equals(parameters[2].getType().getCanonicalText());
        }
        return false;
    }
}