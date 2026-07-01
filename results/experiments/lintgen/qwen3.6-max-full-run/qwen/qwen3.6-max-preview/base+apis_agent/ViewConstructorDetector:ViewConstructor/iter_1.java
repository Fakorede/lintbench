package com.android.tools.lint.checks;

import com.android.tools.lint.client.api.JavaEvaluator;
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
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiType;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;

import java.util.Collections;
import java.util.List;

public class ViewConstructorDetector extends Detector implements SourceCodeScanner {

    public static final Issue ISSUE = Issue.create(
            "ViewConstructor",
            "Missing View constructors for XML inflation",
            "Some layout tools (such as the Android layout editor) need to find a " +
            "constructor with one of the following signatures:\n" +
            "* `View(Context context)`\n" +
            "* `View(Context context, AttributeSet attrs)`\n" +
            "* `View(Context context, AttributeSet attrs, int defStyle)`\n\n" +
            "If your custom view needs to perform initialization which does " +
            "not apply when used in a layout editor, you can surround the " +
            "given code with a check to see if `View#isInEditMode()` is " +
            "false, since that method will return `false` at runtime but " +
            "true within a user interface editor.",
            Category.CORRECTNESS,
            3,
            Severity.WARNING,
            new Implementation(ViewConstructorDetector.class, Scope.JAVA_FILE_SCOPE));

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.singletonList(UClass.class);
    }

    @Override
    public UElementHandler createUastHandler(JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitClass(UClass node) {
                if (node.isInterface() || node.isEnum() || node.hasModifier(PsiModifier.ABSTRACT)) {
                    return;
                }

                PsiClass containingClass = node.getContainingClass();
                if (containingClass != null && !node.hasModifier(PsiModifier.STATIC)) {
                    return;
                }

                JavaEvaluator evaluator = context.getEvaluator();
                if (!evaluator.inheritsFrom(node, "android.view.View", false)) {
                    return;
                }

                boolean hasValidConstructor = false;
                for (PsiMethod constructor : node.getConstructors()) {
                    PsiParameter[] params = constructor.getParameterList().getParameters();
                    int count = params.length;
                    if (count < 1 || count > 3) {
                        continue;
                    }

                    boolean matches = true;

                    PsiType type0 = params[0].getType();
                    if (!evaluator.typeMatches(type0, "android.content.Context")) {
                        matches = false;
                    }

                    if (matches && count >= 2) {
                        PsiType type1 = params[1].getType();
                        if (!evaluator.typeMatches(type1, "android.util.AttributeSet")) {
                            matches = false;
                        }
                    }

                    if (matches && count == 3) {
                        PsiType type2 = params[2].getType();
                        if (!PsiType.INT.equals(type2)) {
                            matches = false;
                        }
                    }

                    if (matches) {
                        hasValidConstructor = true;
                        break;
                    }
                }

                if (!hasValidConstructor) {
                    String name = node.getName();
                    context.report(ISSUE, node, context.getNameLocation(node),
                            "Custom view `" + name + "` is missing constructor used by tools: " +
                            "(Context) or (Context,AttributeSet) or (Context,AttributeSet,int)");
                }
            }
        };
    }
}