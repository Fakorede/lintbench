package com.android.tools.lint.checks;

import com.android.tools.lint.client.api.JavaEvaluator;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiParameterList;
import java.util.Collections;
import java.util.List;
import org.jetbrains.uast.UAnonymousClass;
import org.jetbrains.uast.UClass;

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
            Category.CORRECTNESS,
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
        if (declaration instanceof UAnonymousClass || declaration.getName() == null) {
            return;
        }

        JavaEvaluator evaluator = context.getEvaluator();
        if (declaration.isInterface() || evaluator.isAbstract(declaration)) {
            return;
        }

        if (evaluator.isPrivate(declaration)) {
            return;
        }

        PsiClass containingClass = declaration.getContainingClass();
        if (containingClass != null && !evaluator.isStatic(declaration)) {
            return;
        }

        PsiMethod[] constructors = declaration.getConstructors();
        boolean hasStandardConstructor = false;
        for (PsiMethod constructor : constructors) {
            if (isStandardConstructor(evaluator, constructor)) {
                hasStandardConstructor = true;
                break;
            }
        }

        if (!hasStandardConstructor) {
            String message = String.format(
                    "Custom view `%1$s` is missing constructor used by tools: (Context) or (Context,AttributeSet) or (Context,AttributeSet,int)",
                    declaration.getName()
            );
            context.report(
                    ISSUE,
                    declaration,
                    context.getNameLocation(declaration),
                    message
            );
        }
    }

    private boolean isStandardConstructor(JavaEvaluator evaluator, PsiMethod method) {
        PsiParameterList parameterList = method.getParameterList();
        int count = parameterList.getParametersCount();
        if (count < 1 || count > 4) {
            return false;
        }
        PsiParameter[] parameters = parameterList.getParameters();

        if (!evaluator.typeMatches(parameters[0].getType(), "android.content.Context")) {
            return false;
        }
        if (count == 1) {
            return true;
        }

        if (!evaluator.typeMatches(parameters[1].getType(), "android.util.AttributeSet")) {
            return false;
        }
        if (count == 2) {
            return true;
        }

        if (!evaluator.typeMatches(parameters[2].getType(), "int")) {
            return false;
        }
        if (count == 3) {
            return true;
        }

        return evaluator.typeMatches(parameters[3].getType(), "int") && count == 4;
    }
}