package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.JavaEvaluator;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.UElementHandler;
import com.intellij.psi.PsiType;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UParameter;

import java.util.Collections;
import java.util.List;

public class ViewConstructorDetector extends Detector implements Detector.UastScanner {

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
                if (node.isInterface() || node.isAnnotationType() || node.isEnum() || node.isAbstract()) {
                    return;
                }

                JavaEvaluator evaluator = context.getEvaluator();
                if (!evaluator.extendsClass(node, "android.view.View", false)) {
                    return;
                }

                for (UMethod method : node.getMethods()) {
                    if (method.isConstructor() && isValidViewConstructor(method, evaluator)) {
                        return;
                    }
                }

                context.report(ISSUE, node, context.getNameLocation(node),
                        "Custom view `" + node.getName() + "` is missing constructor used by tools: " +
                        "`(Context)`, `(Context,AttributeSet)`, or `(Context,AttributeSet,int)`");
            }
        };
    }

    private static boolean isValidViewConstructor(UMethod constructor, JavaEvaluator evaluator) {
        List<UParameter> params = constructor.getUastParameters();
        int n = params.size();
        if (n < 1 || n > 4) {
            return false;
        }

        PsiType type0 = params.get(0).getType();
        if (type0 == null || !evaluator.inheritsFrom(type0, "android.content.Context", false)) {
            return false;
        }

        if (n > 1) {
            PsiType type1 = params.get(1).getType();
            if (type1 == null || !evaluator.inheritsFrom(type1, "android.util.AttributeSet", false)) {
                return false;
            }
        }

        if (n > 2) {
            if (!PsiType.INT.equals(params.get(2).getType())) {
                return false;
            }
        }

        if (n > 3) {
            if (!PsiType.INT.equals(params.get(3).getType())) {
                return false;
            }
        }

        return true;
    }
}