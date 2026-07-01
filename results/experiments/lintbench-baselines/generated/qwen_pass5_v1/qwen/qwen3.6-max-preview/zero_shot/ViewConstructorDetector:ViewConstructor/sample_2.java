package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.JavaEvaluator;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.UastScanner;
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

public class ViewConstructorDetector extends Detector implements UastScanner {

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
    public UElementHandler createUastHandler(@NonNull JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitClass(@NonNull UClass node) {
                if (node.isInterface() || node.isAnnotationType() || node.isAbstract()) {
                    return;
                }

                JavaEvaluator evaluator = context.getEvaluator();
                if (!evaluator.extendsClass(node, "android.view.View", false)) {
                    return;
                }

                List<UMethod> constructors = node.getConstructors();
                if (constructors.isEmpty()) {
                    context.report(ISSUE, node, context.getLocation(node),
                        "Missing constructor. Custom views must provide at least one constructor for XML inflation.");
                    return;
                }

                for (UMethod constructor : constructors) {
                    if (hasValidConstructorSignature(evaluator, constructor)) {
                        return;
                    }
                }

                context.report(ISSUE, node, context.getLocation(node),
                    "Missing constructor. Custom views must provide at least one constructor for XML inflation.");
            }
        };
    }

    private static boolean hasValidConstructorSignature(@NonNull JavaEvaluator evaluator, @NonNull UMethod constructor) {
        List<UParameter> parameters = constructor.getParameterList().getParameters();
        int count = parameters.size();

        if (count == 1) {
            return isContext(evaluator, parameters.get(0).getType());
        } else if (count == 2) {
            return isContext(evaluator, parameters.get(0).getType()) &&
                   isAttributeSet(evaluator, parameters.get(1).getType());
        } else if (count == 3) {
            return isContext(evaluator, parameters.get(0).getType()) &&
                   isAttributeSet(evaluator, parameters.get(1).getType()) &&
                   isInt(parameters.get(2).getType());
        } else if (count == 4) {
            return isContext(evaluator, parameters.get(0).getType()) &&
                   isAttributeSet(evaluator, parameters.get(1).getType()) &&
                   isInt(parameters.get(2).getType()) &&
                   isInt(parameters.get(3).getType());
        }
        return false;
    }

    private static boolean isContext(@NonNull JavaEvaluator evaluator, @Nullable PsiType type) {
        return evaluator.typeMatches(type, "android.content.Context");
    }

    private static boolean isAttributeSet(@NonNull JavaEvaluator evaluator, @Nullable PsiType type) {
        return evaluator.typeMatches(type, "android.util.AttributeSet");
    }

    private static boolean isInt(@Nullable PsiType type) {
        return PsiType.INT.equals(type);
    }
}