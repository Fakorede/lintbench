package com.android.tools.lint.checks;

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
import com.intellij.psi.PsiParameterList;
import com.intellij.psi.PsiType;
import org.jetbrains.uast.UClass;
import java.util.Collections;
import java.util.List;

public class ViewConstructorDetector extends Detector implements SourceCodeScanner {

    public static final Issue ISSUE = Issue.create(
        "ViewConstructor",
        "Missing View constructors for XML inflation",
        "Some layout tools (such as the Android layout editor) need to find a " +
        "constructor with one of the following signatures:\n" +
        " * `View(Context context)`\n" +
        " * `View(Context context, AttributeSet attrs)`\n" +
        " * `View(Context context, AttributeSet attrs, int defStyle)`\n" +
        "\n" +
        "If your custom view needs to perform initialization which does " +
        "not apply when used in a layout editor, you can surround the " +
        "given code with a check to see if `View#isInEditMode()` is " +
        "false, since that method will return `false` at runtime but " +
        "true within a user interface editor.",
        Category.USABILITY,
        3,
        Severity.WARNING,
        new Implementation(
            ViewConstructorDetector.class,
            Scope.JAVA_FILE_SCOPE
        )
    );

    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList("android.view.View");
    }

    @Override
    public void visitClass(JavaContext context, UClass declaration) {
        if (declaration.hasModifierProperty(PsiModifier.ABSTRACT)) {
            return;
        }

        if (declaration.hasModifierProperty(PsiModifier.PRIVATE)) {
            return;
        }

        if (declaration.getContainingClass() != null && !declaration.hasModifierProperty(PsiModifier.STATIC)) {
            return;
        }

        PsiMethod[] constructors = declaration.getConstructors();
        boolean hasStandardConstructor = false;

        for (PsiMethod constructor : constructors) {
            if (isStandardConstructor(constructor)) {
                hasStandardConstructor = true;
                break;
            }
        }

        if (!hasStandardConstructor) {
            String message = String.format(
                "Custom view `%1$s` is missing standard constructors (such as `(Context, AttributeSet)`)",
                declaration.getName()
            );
            context.report(ISSUE, declaration, context.getNameLocation(declaration), message);
        }
    }

    private static boolean isStandardConstructor(PsiMethod constructor) {
        PsiParameterList parameterList = constructor.getParameterList();
        PsiParameter[] parameters = parameterList.getParameters();
        int paramCount = parameters.length;

        if (paramCount < 1 || paramCount > 4) {
            return false;
        }

        if (!isType(parameters[0].getType(), "android.content.Context")) {
            return false;
        }

        if (paramCount >= 2) {
            if (!isType(parameters[1].getType(), "android.util.AttributeSet")) {
                return false;
            }
        }

        if (paramCount >= 3) {
            PsiType type = parameters[2].getType();
            if (!type.equals(PsiType.INT) && !isType(type, "java.lang.Integer")) {
                return false;
            }
        }

        if (paramCount == 4) {
            PsiType type = parameters[3].getType();
            if (!type.equals(PsiType.INT) && !isType(type, "java.lang.Integer")) {
                return false;
            }
        }

        return true;
    }

    private static boolean isType(PsiType type, String qualifiedName) {
        if (type == null) {
            return false;
        }
        String canonical = type.getCanonicalText();
        int lastSpace = canonical.lastIndexOf(' ');
        if (lastSpace != -1) {
            canonical = canonical.substring(lastSpace + 1);
        }
        return canonical.equals(qualifiedName);
    }
}