package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.JavaEvaluator;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiParameterList;
import com.intellij.psi.PsiType;
import java.util.Collections;
import java.util.List;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElementHandler;

public class ViewConstructorDetector extends Detector implements SourceCodeScanner {

    public static final Issue ISSUE = Issue.create(
            "ViewConstructor",
            "Missing View constructors for XML inflation",
            "Some layout tools (such as the Android layout editor) need to find a "
                    + "constructor with one of the following signatures:\n"
                    + " * `View(Context context)`\n"
                    + " * `View(Context context, AttributeSet attrs)`\n"
                    + " * `View(Context context, AttributeSet attrs, int defStyle)`\n\n"
                    + "If your custom view needs to perform initialization which does "
                    + "not apply when used in a layout editor, you can surround the "
                    + "given code with a check to see if `View#isInEditMode()` is false, "
                    + "since that method will return `false` at runtime but true within a "
                    + "user interface editor.",
            Category.USABILITY,
            3,
            Severity.WARNING,
            new Implementation(ViewConstructorDetector.class, Scope.JAVA_FILE_SCOPE));

    @Override
    public List<Class<? extends org.jetbrains.uast.UElement>> getApplicableUastTypes() {
        return Collections.singletonList(UClass.class);
    }

    @Override
    public UElementHandler createUastHandler(JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitClass(UClass node) {
                if (!(node.getJavaPsi() instanceof PsiClass)) {
                    return;
                }
                PsiClass psiClass = (PsiClass) node.getJavaPsi();
                if (psiClass.isInterface() || psiClass.isAnnotationType()) {
                    return;
                }
                if (psiClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
                    return;
                }

                JavaEvaluator evaluator = context.getEvaluator();
                if (!evaluator.extendsClass(psiClass, "android.view.View", false)) {
                    return;
                }

                boolean hasInflater = false;
                PsiMethod[] constructors = psiClass.getConstructors();
                for (PsiMethod constructor : constructors) {
                    PsiParameterList paramList = constructor.getParameterList();
                    PsiParameter[] params = paramList.getParameters();
                    int count = params.length;
                    if (count == 1 && isContext(params[0].getType())) {
                        hasInflater = true;
                        break;
                    } else if (count == 2
                            && isContext(params[0].getType())
                            && isAttributeSet(params[1].getType())) {
                        hasInflater = true;
                        break;
                    } else if (count == 3
                            && isContext(params[0].getType())
                            && isAttributeSet(params[1].getType())
                            && isInt(params[2].getType())) {
                        hasInflater = true;
                        break;
                    }
                }

                if (constructors.length > 0 && !hasInflater) {
                    String name = psiClass.getName();
                    context.report(
                            ISSUE,
                            context.getNameLocation(node),
                            String.format(
                                    "Custom view `%1$s` is missing a constructor with a "
                                            + "`Context` and `AttributeSet` signature needed "
                                            + "for XML inflation.",
                                    name));
                }
            }
        };
    }

    private static boolean isContext(PsiType type) {
        return type != null
                && "android.content.Context".equals(type.getCanonicalText());
    }

    private static boolean isAttributeSet(PsiType type) {
        return type != null
                && "android.util.AttributeSet".equals(type.getCanonicalText());
    }

    private static boolean isInt(PsiType type) {
        return type != null && "int".equals(type.getCanonicalText());
    }
}