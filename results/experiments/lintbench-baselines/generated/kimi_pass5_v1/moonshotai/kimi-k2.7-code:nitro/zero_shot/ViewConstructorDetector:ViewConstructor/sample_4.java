package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.intellij.psi.PsiModifier;
import java.util.Collections;
import java.util.List;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UElementHandler;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UParameter;
import org.jetbrains.uast.UastVisibility;

public class ViewConstructorDetector extends Detector implements SourceCodeScanner {

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
                    + " * `View(Context context, AttributeSet attrs, int defStyle)`\n"
                    + "If your custom view needs to perform initialization which does not apply "
                    + "when used in a layout editor, you can surround the given code with a "
                    + "check to see if `View#isInEditMode()` is false, since that method will "
                    + "return false at runtime but true within a user interface editor.",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(ViewConstructorDetector.class, Scope.JAVA_FILE_SCOPE));

    @Override
    public List<Class<? extends UElement>> getApplicableUObjectTypes() {
        return Collections.singletonList(UClass.class);
    }

    @Override
    public UElementHandler createUastHandler(JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitClass(UClass node) {
                if (node.isInterface() || node.isEnumType()) {
                    return;
                }
                if (node.hasModifierProperty(PsiModifier.ABSTRACT)) {
                    return;
                }
                if (CLASS_VIEW.equals(node.getQualifiedName())) {
                    return;
                }
                if (!context.getEvaluator().extendsClass(node, CLASS_VIEW, false)) {
                    return;
                }
                if (!hasRequiredConstructor(node)) {
                    context.report(
                            ISSUE,
                            node,
                            context.getNameLocation(node),
                            "Missing one of the required constructors for XML inflation "
                                    + "(Context), (Context, AttributeSet), or "
                                    + "(Context, AttributeSet, int)");
                }
            }
        };
    }

    private static boolean hasRequiredConstructor(UClass node) {
        for (UMethod method : node.getMethods()) {
            if (!method.isConstructor() || method.getVisibility() != UastVisibility.PUBLIC) {
                continue;
            }
            List<UParameter> params = method.getUastParameters();
            int count = params.size();
            if (count == 1
                    && isContext(params.get(0))) {
                return true;
            }
            if (count == 2
                    && isContext(params.get(0))
                    && isAttributeSet(params.get(1))) {
                return true;
            }
            if (count == 3
                    && isContext(params.get(0))
                    && isAttributeSet(params.get(1))
                    && isInt(params.get(2))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isContext(UParameter parameter) {
        return CLASS_CONTEXT.equals(parameter.getType().getCanonicalText());
    }

    private static boolean isAttributeSet(UParameter parameter) {
        return CLASS_ATTRIBUTE_SET.equals(parameter.getType().getCanonicalText());
    }

    private static boolean isInt(UParameter parameter) {
        return "int".equals(parameter.getType().getCanonicalText());
    }
}