package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiType;
import java.util.Collections;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UIdentifier;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UParameter;

public class ViewConstructorDetector extends Detector implements Detector.UastScanner {

    private static final String CLASS_VIEW = "android.view.View";
    private static final String CLASS_CONTEXT = "android.content.Context";
    private static final String CLASS_ATTRIBUTE_SET = "android.util.AttributeSet";

    private static final String ERROR_MESSAGE =
            "This custom view is missing a constructor with one of the following signatures: "
                    + "View(Context context), View(Context context, AttributeSet attrs), "
                    + "or View(Context context, AttributeSet attrs, int defStyleAttr)";

    public static final Issue ISSUE = Issue.create(
            "ViewConstructor",
            "Missing View constructors for XML inflation",
            "Some layout tools (such as the Android layout editor) need to find a constructor "
                    + "with one of the following signatures:\n"
                    + "* `View(Context context)`\n"
                    + "* `View(Context context, AttributeSet attrs)`\n"
                    + "* `View(Context context, AttributeSet attrs, int defStyle)`\n\n"
                    + "If your custom view needs to perform initialization which does not apply "
                    + "when used in a layout editor, you can surround the given code with a check "
                    + "to see if `View#isInEditMode()` is false, since that method will return "
                    + "`false` at runtime but true within a user interface editor.",
            Category.CORRECTNESS,
            5,
            Severity.WARNING,
            new Implementation(ViewConstructorDetector.class, Scope.JAVA_FILE_SCOPE));

    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList(CLASS_VIEW);
    }

    @Override
    public void visitClass(@NotNull JavaContext context, @NotNull UClass classNode) {
        if (classNode.isInterface() || classNode.isEnum()) {
            return;
        }
        if (classNode.hasModifierProperty(PsiModifier.ABSTRACT)) {
            return;
        }
        if (hasXmlConstructor(classNode, context)) {
            return;
        }

        UIdentifier nameIdentifier = classNode.getNameIdentifier();
        Location location = nameIdentifier != null
                ? context.getLocation(nameIdentifier)
                : context.getLocation(classNode);
        context.report(ISSUE, classNode, location, ERROR_MESSAGE);
    }

    private static boolean hasXmlConstructor(@NotNull UClass cls, @NotNull JavaContext context) {
        for (UMethod constructor : cls.getConstructors()) {
            List<UParameter> parameters = constructor.getUastParameters();
            int size = parameters.size();
            if (size < 1 || size > 3) {
                continue;
            }
            if (!isContext(context, parameters.get(0))) {
                continue;
            }
            if (size == 1) {
                return true;
            }
            if (!isAttributeSet(context, parameters.get(1))) {
                continue;
            }
            if (size == 2) {
                return true;
            }
            if (isInt(parameters.get(2).getType())) {
                return true;
            }
        }
        return false;
    }

    private static boolean isContext(@NotNull JavaContext context, @NotNull UParameter parameter) {
        PsiClass typeClass = context.getEvaluator().getTypeClass(parameter.getType());
        return typeClass != null && CLASS_CONTEXT.equals(typeClass.getQualifiedName());
    }

    private static boolean isAttributeSet(@NotNull JavaContext context, @NotNull UParameter parameter) {
        PsiClass typeClass = context.getEvaluator().getTypeClass(parameter.getType());
        return typeClass != null && CLASS_ATTRIBUTE_SET.equals(typeClass.getQualifiedName());
    }

    private static boolean isInt(PsiType type) {
        return type != null && (PsiType.INT.equals(type) || "int".equals(type.getCanonicalText()));
    }
}