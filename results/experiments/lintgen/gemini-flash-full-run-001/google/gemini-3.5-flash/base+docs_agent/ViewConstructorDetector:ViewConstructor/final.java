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
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiParameterList;
import com.intellij.psi.PsiType;
import java.util.Collections;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
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
            Category.USABILITY,
            3,
            Severity.WARNING,
            new Implementation(
                    ViewConstructorDetector.class,
                    Scope.JAVA_FILE_SCOPE
            )
    );

    @Nullable
    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList("android.view.View");
    }

    @Override
    public void visitClass(@NotNull JavaContext context, @NotNull UClass declaration) {
        if (declaration.isInterface() || context.getEvaluator().isAbstract(declaration)) {
            return;
        }

        if (declaration.getContainingClass() != null && !context.getEvaluator().isStatic(declaration)) {
            return;
        }

        PsiMethod[] constructors = declaration.getConstructors();

        boolean hasContext = false;
        boolean hasContextAndAttrs = false;

        for (PsiMethod constructor : constructors) {
            PsiParameterList parameterList = constructor.getParameterList();
            int count = parameterList.getParametersCount();
            if (count == 1) {
                PsiParameter p0 = parameterList.getParameters()[0];
                if (isType(p0.getType(), "android.content.Context")) {
                    hasContext = true;
                }
            } else if (count == 2) {
                PsiParameter p0 = parameterList.getParameters()[0];
                PsiParameter p1 = parameterList.getParameters()[1];
                if (isType(p0.getType(), "android.content.Context")
                        && isType(p1.getType(), "android.util.AttributeSet")) {
                    hasContextAndAttrs = true;
                }
            } else if (count == 3) {
                PsiParameter p0 = parameterList.getParameters()[0];
                PsiParameter p1 = parameterList.getParameters()[1];
                PsiParameter p2 = parameterList.getParameters()[2];
                if (isType(p0.getType(), "android.content.Context")
                        && isType(p1.getType(), "android.util.AttributeSet")
                        && isType(p2.getType(), "int")) {
                    hasContextAndAttrs = true;
                }
            }
        }

        if (!hasContextAndAttrs) {
            String message = String.format(
                    "Custom view `%1$s` is missing constructor `%1$s(Context, AttributeSet)`",
                    declaration.getName()
            );
            context.report(ISSUE, declaration, context.getNameLocation(declaration), message);
        } else if (!hasContext) {
            String message = String.format(
                    "Custom view `%1$s` is missing constructor `%1$s(Context)`",
                    declaration.getName()
            );
            context.report(ISSUE, declaration, context.getNameLocation(declaration), message);
        }
    }

    private static boolean isType(@Nullable PsiType type, @NotNull String qualifiedName) {
        if (type == null) {
            return false;
        }
        return type.equalsToText(qualifiedName);
    }
}