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
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NonNull;
import org.jetbrains.uast.JvmModifier;
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
                if (node.isInterface() || node.isEnum() || node.hasModifier(JvmModifier.ABSTRACT)) {
                    return;
                }

                // Non-static inner classes cannot be inflated via XML anyway
                UClass containingClass = node.getContainingClass();
                if (containingClass != null && !node.hasModifier(JvmModifier.STATIC)) {
                    return;
                }

                JavaEvaluator evaluator = context.getEvaluator();
                if (!evaluator.inheritsFrom(node, "android.view.View", false)) {
                    return;
                }

                boolean hasValidConstructor = false;
                for (UMethod constructor : node.getConstructors()) {
                    List<UParameter> params = constructor.getUastParameters();
                    int count = params.size();
                    if (count < 1 || count > 3) {
                        continue;
                    }

                    boolean matches = true;

                    // First parameter must be Context
                    if (!evaluator.typeMatches(params.get(0).getType(), "android.content.Context")) {
                        matches = false;
                    }

                    // Second parameter, if present, must be AttributeSet
                    if (matches && count >= 2) {
                        if (!evaluator.typeMatches(params.get(1).getType(), "android.util.AttributeSet")) {
                            matches = false;
                        }
                    }

                    // Third parameter, if present, must be int
                    if (matches && count == 3) {
                        if (!params.get(2).getType().equals(PsiType.INT)) {
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