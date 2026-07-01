package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiParameterList;
import com.intellij.psi.PsiType;
import java.util.Collections;
import java.util.List;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UastUtils;

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
            new Implementation(ViewConstructorDetector.class, Scope.JAVA_FILE_SCOPE)
    );

    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList("android.view.View");
    }

    @Override
    public void visitClass(JavaContext context, UClass declaration) {
        if (declaration.isInterface() || context.getEvaluator().isAbstract(declaration)) {
            return;
        }

        // Skip non-static inner classes
        if (UastUtils.getContainingClass(declaration) != null && !context.getEvaluator().isStatic(declaration)) {
            return;
        }

        PsiMethod[] constructors = declaration.getConstructors();
        if (constructors.length == 0) {
            report(context, declaration);
            return;
        }

        boolean hasValidConstructor = false;
        for (PsiMethod constructor : constructors) {
            if (isValidConstructor(constructor)) {
                hasValidConstructor = true;
                break;
            }
        }

        if (!hasValidConstructor) {
            report(context, declaration);
        }
    }

    private static boolean isValidConstructor(PsiMethod method) {
        if (method.getModifierList().hasExplicitModifier(PsiModifier.PRIVATE)) {
            return false;
        }
        PsiParameterList parameterList = method.getParameterList();
        int count = parameterList.getParametersCount();
        if (count < 1 || count > 4) {
            return false;
        }
        PsiParameter[] parameters = parameterList.getParameters();
        PsiType type0 = parameters[0].getType();
        if (!isType(type0, "android.content.Context")) {
            return false;
        }
        if (count == 1) {
            return true;
        }
        PsiType type1 = parameters[1].getType();
        if (!isType(type1, "android.util.AttributeSet")) {
            return false;
        }
        if (count == 2) {
            return true;
        }
        PsiType type2 = parameters[2].getType();
        if (!type2.equals(PsiType.INT)) {
            return false;
        }
        if (count == 3) {
            return true;
        }
        PsiType type3 = parameters[3].getType();
        return type3.equals(PsiType.INT);
    }

    private static boolean isType(PsiType type, String fqName) {
        if (type == null) {
            return false;
        }
        if (type instanceof PsiClassType) {
            PsiClass clazz = ((PsiClassType) type).resolve();
            if (clazz != null) {
                return fqName.equals(clazz.getQualifiedName());
            }
        }
        String canonical = type.getCanonicalText();
        int lastSpace = canonical.lastIndexOf(' ');
        if (lastSpace != -1) {
            canonical = canonical.substring(lastSpace + 1);
        }
        return canonical.equals(fqName);
    }

    private void report(JavaContext context, UClass declaration) {
        context.report(
                ISSUE,
                declaration,
                context.getNameLocation(declaration),
                "Custom view `" + declaration.getName() + "` is missing recommended constructors"
        );
    }
}