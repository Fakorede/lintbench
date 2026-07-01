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
import com.intellij.psi.PsiType;
import java.util.Collections;
import java.util.List;
import org.jetbrains.uast.UClass;

public class ViewConstructorDetector extends Detector implements SourceCodeScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(ViewConstructorDetector.class, Scope.JAVA_FILE_SCOPE);

    private static final String VIEW_CLASS = "android.view.View";
    private static final String CONTEXT_CLASS = "android.content.Context";
    private static final String ATTRIBUTE_SET_CLASS = "android.util.AttributeSet";

    public static final Issue ISSUE =
            Issue.create(
                            "ViewConstructor",
                            "Missing View constructors for XML inflation",
                            "Custom views must provide one of the constructors "
                                    + "`View(Context)`, `View(Context, AttributeSet)`, or "
                                    + "`View(Context, AttributeSet, int)` so that layout tools "
                                    + "(such as the Android layout editor) can inflate them.",
                            Category.USABILITY,
                            5,
                            Severity.WARNING,
                            IMPLEMENTATION)
                    .setAndroidSpecific(true);

    public ViewConstructorDetector() {}

    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList(VIEW_CLASS);
    }

    @Override
    public void visitClass(JavaContext context, UClass declaration) {
        if (declaration.isAbstract()) {
            return;
        }

        String qualifiedName = declaration.getQualifiedName();
        if (qualifiedName != null && qualifiedName.equals(VIEW_CLASS)) {
            return;
        }

        if (hasXmlConstructor(declaration)) {
            return;
        }

        context.report(
                ISSUE,
                declaration,
                context.getNameLocation(declaration),
                "Custom view `"
                        + declaration.getName()
                        + "` is missing a constructor for XML inflation; add one of "
                        + "`View(Context)`, `View(Context, AttributeSet)`, or "
                        + "`View(Context, AttributeSet, int)`");
    }

    private static boolean hasXmlConstructor(UClass declaration) {
        for (PsiMethod constructor : declaration.getConstructors()) {
            if (isXmlConstructor(constructor)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isXmlConstructor(PsiMethod constructor) {
        PsiParameter[] parameters = constructor.getParameterList().getParameters();

        if (parameters.length == 1) {
            return isType(parameters[0], CONTEXT_CLASS);
        }

        if (parameters.length == 2) {
            return isType(parameters[0], CONTEXT_CLASS)
                    && isType(parameters[1], ATTRIBUTE_SET_CLASS);
        }

        if (parameters.length == 3) {
            return isType(parameters[0], CONTEXT_CLASS)
                    && isType(parameters[1], ATTRIBUTE_SET_CLASS)
                    && isInt(parameters[2]);
        }

        return false;
    }

    private static boolean isType(PsiParameter parameter, String typeName) {
        return typeName.equals(parameter.getType().getCanonicalText());
    }

    private static boolean isInt(PsiParameter parameter) {
        return PsiType.INT.equals(parameter.getType());
    }
}