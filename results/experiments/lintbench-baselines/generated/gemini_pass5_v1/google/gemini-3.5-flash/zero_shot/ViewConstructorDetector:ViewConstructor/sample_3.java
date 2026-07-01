package com.android.tools.lint.checks;

import com.android.tools.lint.client.api.UElementHandler;
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
import com.intellij.psi.PsiType;
import java.util.Collections;
import java.util.List;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;

public class ViewConstructorDetector extends Detector implements SourceCodeScanner {

    public static final Issue ISSUE = Issue.create(
            "ViewConstructor",
            "Missing View constructors for XML inflation",
            "Some layout tools (such as the Android layout editor) need to find a " +
            "constructor with one of the following signatures:\n" +
            " * `View(Context context)`\n" +
            " * `View(Context context, AttributeSet attrs)`\n" +
            " * `View(Context context, AttributeSet attrs, int defStyle)`\n\n" +
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
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.singletonList(UClass.class);
    }

    @Override
    public UElementHandler createUastHandler(JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitClass(UClass node) {
                PsiClass psiClass = node.getJavaPsi();
                if (psiClass.isInterface() || psiClass.isAnnotationType()) {
                    return;
                }

                if (context.getEvaluator().isAbstract(psiClass)) {
                    return;
                }

                // Inner classes must be static to be inflated
                if (psiClass.getContainingClass() != null && !context.getEvaluator().isStatic(psiClass)) {
                    return;
                }

                if (!context.getEvaluator().extendsClass(psiClass, "android.view.View", false)) {
                    return;
                }

                PsiMethod[] constructors = psiClass.getConstructors();
                if (constructors.length == 0) {
                    reportMissingConstructor(context, node, psiClass);
                    return;
                }

                boolean hasValidConstructor = false;
                for (PsiMethod constructor : constructors) {
                    if (isValidViewConstructor(constructor)) {
                        hasValidConstructor = true;
                        break;
                    }
                }

                if (!hasValidConstructor) {
                    reportMissingConstructor(context, node, psiClass);
                }
            }
        };
    }

    private static void reportMissingConstructor(JavaContext context, UClass node, PsiClass psiClass) {
        context.report(
                ISSUE,
                node,
                context.getNameLocation(node),
                "Custom view `" + psiClass.getName() + "` is missing recommended constructors"
        );
    }

    private static boolean isValidViewConstructor(PsiMethod constructor) {
        PsiParameter[] parameters = constructor.getParameterList().getParameters();
        int count = parameters.length;
        if (count < 1 || count > 4) {
            return false;
        }

        // 1st param: Context
        if (!isType(parameters[0].getType(), "android.content.Context")) {
            return false;
        }

        // 2nd param: AttributeSet
        if (count >= 2 && !isType(parameters[1].getType(), "android.util.AttributeSet")) {
            return false;
        }

        // 3rd param: int
        if (count >= 3 && !isIntType(parameters[2].getType())) {
            return false;
        }

        // 4th param: int
        if (count == 4 && !isIntType(parameters[3].getType())) {
            return false;
        }

        return true;
    }

    private static boolean isType(PsiType type, String qualifiedName) {
        return type != null && type.getCanonicalText().equals(qualifiedName);
    }

    private static boolean isIntType(PsiType type) {
        if (type == null) {
            return false;
        }
        String text = type.getCanonicalText();
        return "int".equals(text) || "java.lang.Integer".equals(text);
    }
}