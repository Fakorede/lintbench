package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Evaluator;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NonNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UElementHandler;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UParameter;

import java.util.Collections;
import java.util.List;

public class ViewConstructorDetector extends Detector implements SourceCodeScanner {

    public static final Issue ISSUE = Issue.create(
            "ViewConstructor",
            "Missing View constructors for XML inflation",
            "Some layout tools (such as the Android layout editor) need to find a constructor with one of the following signatures:\n" +
            "* `View(Context context)`\n" +
            "* `View(Context context, AttributeSet attrs)`\n" +
            "* `View(Context context, AttributeSet attrs, int defStyle)`\n\n" +
            "If your custom view needs to perform initialization which does not apply when used in a layout editor, you can surround the given code with a check to see if `View#isInEditMode()` is false, since that method will return `false` at runtime but true within a user interface editor.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(ViewConstructorDetector.class, Scope.JAVA_FILE_SCOPE)
    );

    @Nullable
    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.singletonList(UClass.class);
    }

    @Nullable
    @Override
    public UElementHandler createUastHandler(@NonNull JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitClass(@NonNull UClass node) {
                if (node.isInterface() || node.hasModifierProperty(PsiModifier.ABSTRACT)) {
                    return;
                }

                if (node.getContainingClass() != null && !node.hasModifierProperty(PsiModifier.STATIC)) {
                    return;
                }

                Evaluator evaluator = context.getEvaluator();
                if (!evaluator.extendsClass(node, "android.view.View", false)) {
                    return;
                }

                boolean hasValidConstructor = false;
                for (UMethod method : node.getMethods()) {
                    if (!method.isConstructor()) {
                        continue;
                    }
                    List<UParameter> params = method.getUastParameters();
                    int count = params.size();
                    if (count == 1 && isContext(evaluator, params.get(0).getType())) {
                        hasValidConstructor = true;
                        break;
                    } else if (count == 2 && isContext(evaluator, params.get(0).getType()) && isAttributeSet(evaluator, params.get(1).getType())) {
                        hasValidConstructor = true;
                        break;
                    } else if (count == 3 && isContext(evaluator, params.get(0).getType()) && isAttributeSet(evaluator, params.get(1).getType()) && isInt(params.get(2).getType())) {
                        hasValidConstructor = true;
                        break;
                    } else if (count == 4 && isContext(evaluator, params.get(0).getType()) && isAttributeSet(evaluator, params.get(1).getType()) && isInt(params.get(2).getType()) && isInt(params.get(3).getType())) {
                        hasValidConstructor = true;
                        break;
                    }
                }

                if (!hasValidConstructor) {
                    String name = node.getName();
                    context.report(ISSUE, node, context.getNameLocation(node),
                            "Custom view `" + name + "` is missing constructor used by tools: `(Context)` or `(Context,AttributeSet)` or `(Context,AttributeSet,int)`");
                }
            }
        };
    }

    private static boolean isContext(@NonNull Evaluator evaluator, @Nullable PsiType type) {
        return type != null && evaluator.typeMatches(type, "android.content.Context");
    }

    private static boolean isAttributeSet(@NonNull Evaluator evaluator, @Nullable PsiType type) {
        return type != null && evaluator.typeMatches(type, "android.util.AttributeSet");
    }

    private static boolean isInt(@Nullable PsiType type) {
        return type != null && type.equalsToText("int");
    }
}