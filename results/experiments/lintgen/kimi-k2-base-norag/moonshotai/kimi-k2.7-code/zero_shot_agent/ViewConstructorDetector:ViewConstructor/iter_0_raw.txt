package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiParameterList;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.InheritanceUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UElementHandler;

import java.util.Collections;
import java.util.List;

public class ViewConstructorDetector extends Detector implements Detector.UastScanner {

    private static final String CLASS_VIEW = "android.view.View";
    private static final String CLASS_CONTEXT = "android.content.Context";
    private static final String CLASS_ATTRIBUTE_SET = "android.util.AttributeSet";

    public static final Issue ISSUE = Issue.create(
            "ViewConstructor",
            "Missing View constructors for XML inflation",
            "Some layout tools (such as the Android layout editor) need to find a "
                    + "constructor with one of the following signatures:\n"
                    + "* `View(Context context)`\n"
                    + "* `View(Context context, AttributeSet attrs)`\n"
                    + "* `View(Context context, AttributeSet attrs, int defStyle)`\n"
                    + "\n"
                    + "If your custom view needs to perform initialization which does "
                    + "not apply when used in a layout editor, you can surround the "
                    + "given code with a check to see if `View#isInEditMode()` is "
                    + "false, since that method will return `false` at runtime but "
                    + "true within a user interface editor.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(ViewConstructorDetector.class, Scope.JAVA_FILE_SCOPE));

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.singletonList(UClass.class);
    }

    @Override
    public UElementHandler createUastHandler(@NotNull JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitClass(@NotNull UClass node) {
                PsiClass psiClass = node.getPsi();
                if (psiClass == null) {
                    return;
                }

                if (psiClass.isInterface() || psiClass.isEnum()
                        || psiClass.isAnnotationType()
                        || psiClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
                    return;
                }

                if (psiClass.getQualifiedName() == null
                        || CLASS_VIEW.equals(psiClass.getQualifiedName())) {
                    return;
                }

                if (!InheritanceUtil.isInheritor(psiClass, CLASS_VIEW)) {
                    return;
                }

                if (psiClass.getContainingClass() != null
                        && !psiClass.hasModifierProperty(PsiModifier.STATIC)) {
                    return;
                }

                if (hasRequiredConstructor(psiClass)) {
                    return;
                }

                String name = psiClass.getName();
                String message = "Custom view `" + name
                        + "` is missing constructor accepting `Context`, "
                        + "`AttributeSet`, or `int defStyle` parameter(s)";
                context.report(ISSUE, node, context.getNameLocation(node), message);
            }
        };
    }

    private static boolean hasRequiredConstructor(PsiClass psiClass) {
        for (PsiMethod constructor : psiClass.getConstructors()) {
            if (!constructor.hasModifierProperty(PsiModifier.PUBLIC)) {
                continue;
            }

            PsiParameterList parameterList = constructor.getParameterList();
            int count = parameterList.getParametersCount();
            if (count < 1 || count > 3) {
                continue;
            }

            PsiParameter[] parameters = parameterList.getParameters();
            if (!isType(parameters[0].getType(), CLASS_CONTEXT)) {
                continue;
            }

            if (count == 1) {
                return true;
            }

            if (!isType(parameters[1].getType(), CLASS_ATTRIBUTE_SET)) {
                continue;
            }

            if (count == 2) {
                return true;
            }

            if (PsiType.INT.equals(parameters[2].getType())) {
                return true;
            }
        }
        return false;
    }

    private static boolean isType(PsiType type, String fullyQualifiedName) {
        if (!(type instanceof PsiClassType)) {
            return false;
        }
        PsiClass resolved = ((PsiClassType) type).resolve();
        return resolved != null && fullyQualifiedName.equals(resolved.getQualifiedName());
    }
}