package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.JavaEvaluator;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.intellij.psi.PsiType;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UElementHandler;
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
            5,
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
                String qualifiedName = node.getQualifiedName();
                if (qualifiedName == null || qualifiedName.startsWith("android.")) {
                    return;
                }

                if (node.isInterface() || node.isEnum() || node.isAbstract()) {
                    return;
                }

                JavaEvaluator evaluator = context.getEvaluator();
                if (!evaluator.extendsClass(node, "android.view.View", false)) {
                    return;
                }

                UMethod[] constructors = node.getConstructors();
                for (UMethod constructor : constructors) {
                    if (isViewConstructor(constructor, context)) {
                        return;
                    }
                }

                context.report(ISSUE, node, context.getNameLocation(node),
                        "Missing View constructors for XML inflation");
            }
        };
    }

    private static boolean isViewConstructor(UMethod constructor, JavaContext context) {
        List<UParameter> params = constructor.getUastParameters();
        int count = params.size();
        if (count < 1 || count > 4) {
            return false;
        }

        JavaEvaluator evaluator = context.getEvaluator();

        if (!evaluator.typeMatches(params.get(0).getType(), "android.content.Context")) {
            return false;
        }
        if (count == 1) {
            return true;
        }

        if (!evaluator.typeMatches(params.get(1).getType(), "android.util.AttributeSet")) {
            return false;
        }
        if (count == 2) {
            return true;
        }

        if (!PsiType.INT.equals(params.get(2).getType())) {
            return false;
        }
        if (count == 3) {
            return true;
        }

        return PsiType.INT.equals(params.get(3).getType());
    }
}