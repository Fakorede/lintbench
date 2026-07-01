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
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiType;
import java.util.Collections;
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
                            + " * `View(Context context)`\n"
                            + " * `View(Context context, AttributeSet attrs)`\n"
                            + " * `View(Context context, AttributeSet attrs, int defStyle)`\n\n"
                            + "If your custom view needs to perform initialization which does "
                            + "not apply when used in a layout editor, you can surround the "
                            + "given code with a check to see if `View#isInEditMode()` is "
                            + "false, since that method will return `false` at runtime but "
                            + "true within a user interface editor.",
                    Category.USABILITY,
                    3,
                    Severity.WARNING,
                    IMPLEMENTATION);

    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList("android.view.View");
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        if (declaration.isInterface()) {
            return;
        }
        if (declaration.hasModifierProperty(PsiModifier.ABSTRACT)) {
            return;
        }
        if (declaration.getContainingClass() != null && !declaration.hasModifierProperty(PsiModifier.STATIC)) {
            return;
        }

        PsiMethod[] constructors = declaration.getConstructors();
        boolean hasRequiredConstructor = false;

        for (PsiMethod constructor : constructors) {
            PsiParameter[] parameters = constructor.getParameterList().getParameters();
            int len = parameters.length;
            if (len == 1) {
                if (isContext(parameters[0].getType())) {
                    hasRequiredConstructor = true;
                    break;
                }
            } else if (len == 2) {
                if (isContext(parameters[0].getType()) && isAttributeSet(parameters[1].getType())) {
                    hasRequiredConstructor = true;
                    break;
                }
            } else if (len == 3) {
                if (isContext(parameters[0].getType()) && isAttributeSet(parameters[1].getType()) && isInt(parameters[2].getType())) {
                    hasRequiredConstructor = true;
                    break;
                }
            }
        }

        if (!hasRequiredConstructor) {
            context.report(
                    ISSUE,
                    declaration,
                    context.getNameLocation(declaration),
                    "Custom view `" + declaration.getName() + "` is missing recommended constructors");
        }
    }

    private static boolean isContext(PsiType type) {
        if (type == null) {
            return false;
        }
        String text = type.getCanonicalText();
        return text.equals("android.content.Context") || text.endsWith(".Context");
    }

    private static boolean isAttributeSet(PsiType type) {
        if (type == null) {
            return false;
        }
        String text = type.getCanonicalText();
        return text.equals("android.util.AttributeSet") || text.endsWith(".AttributeSet");
    }

    private static boolean isInt(PsiType type) {
        if (type == null) {
            return false;
        }
        return PsiType.INT.equals(type) || "int".equals(type.getCanonicalText());
    }
}