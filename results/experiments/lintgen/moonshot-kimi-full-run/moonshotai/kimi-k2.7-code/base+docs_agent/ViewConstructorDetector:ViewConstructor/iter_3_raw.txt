package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiType;
import java.util.Collections;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UElementHandler;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UParameter;

public class ViewConstructorDetector extends Detector implements Detector.UastScanner {

    private static final String CLASS_VIEW = "android.view.View";
    private static final String CLASS_CONTEXT = "android.content.Context";
    private static final String CLASS_ATTRIBUTE_SET = "android.util.AttributeSet";

    public static final Issue ISSUE = Issue.create(
            "ViewConstructor",
            "Missing View constructors for XML inflation",
            "Some layout tools (such as the Android layout editor) need to find a constructor "
                    + "with one of the following signatures:\n"
                    + " * `View(Context context)`\n"
                    + " * `View(Context context, AttributeSet attrs)`\n"
                    + " * `View(Context context, AttributeSet attrs, int defStyle)`\n\n"
                    + "If your custom view needs to perform initialization which does not apply "
                    + "when used in a layout editor, you can check `View#isInEditMode()`.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(ViewConstructorDetector.class, Scope.JAVA_FILE_SCOPE)
    );

    @Override
    @NotNull
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.singletonList(UClass.class);
    }

    @Override
    @NotNull
    public UElementHandler createUastHandler(@NotNull JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitClass(@NotNull UClass node) {
                if (node.isInterface() || node.isEnum()) {
                    return;
                }
                if (node.hasModifierProperty(PsiModifier.ABSTRACT)) {
                    return;
                }
                if (!context.getEvaluator().extendsClass(node, CLASS_VIEW, true)) {
                    return;
                }
                if (hasRequiredConstructor(node)) {
                    return;
                }

                String message = String.format(
                        "Custom view `%1$s` is missing a constructor with one of the required "
                                + "signatures for XML inflation: "
                                + "`View(Context)`, `View(Context, AttributeSet)`, or "
                                + "`View(Context, AttributeSet, int)`",
                        node.getName()
                );
                context.report(ISSUE, node, context.getLocation((UElement) node), message);
            }
        };
    }

    private static boolean hasRequiredConstructor(@NotNull UClass cls) {
        for (UMethod method : cls.getUastMethods()) {
            if (method.isConstructor() && matchesRequiredSignature(method)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesRequiredSignature(@NotNull UMethod method) {
        List<UParameter> parameters = method.getUastParameters();
        int count = parameters.size();

        if (count == 1) {
            return isType(parameters.get(0), CLASS_CONTEXT);
        }

        if (count == 2) {
            return isType(parameters.get(0), CLASS_CONTEXT)
                    && isType(parameters.get(1), CLASS_ATTRIBUTE_SET);
        }

        if (count == 3) {
            return isType(parameters.get(0), CLASS_CONTEXT)
                    && isType(parameters.get(1), CLASS_ATTRIBUTE_SET)
                    && isInt(parameters.get(2));
        }

        return false;
    }

    private static boolean isType(@NotNull UParameter parameter, @NotNull String typeName) {
        PsiType type = parameter.getType();
        return type != null && typeName.equals(type.getCanonicalText());
    }

    private static boolean isInt(@NotNull UParameter parameter) {
        PsiType type = parameter.getType();
        return type != null && (PsiType.INT.equals(type) || "int".equals(type.getCanonicalText()));
    }
}