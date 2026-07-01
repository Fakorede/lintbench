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
import com.intellij.psi.PsiType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UParameter;

public class ViewConstructorDetector extends Detector implements SourceCodeScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(ViewConstructorDetector.class, Scope.JAVA_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "ViewConstructor",
                    "Missing View constructors for XML inflation",
                    "Some layout tools (such as the Android layout editor) need to find a "
                            + "constructor with one of the following signatures: "
                            + "View(Context context), "
                            + "View(Context context, AttributeSet attrs), "
                            + "View(Context context, AttributeSet attrs, int defStyle). "
                            + "If your custom view needs to perform initialization which does "
                            + "not apply when used in a layout editor, you can surround the "
                            + "given code with a check to see if View#isInEditMode() is false, "
                            + "since that method will return false at runtime but true within a "
                            + "user interface editor.",
                    Category.USABILITY,
                    3,
                    Severity.WARNING,
                    IMPLEMENTATION);

    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList("android.view.View");
    }

    @Override
    public void visitClass(JavaContext context, UClass declaration) {
        if (declaration.hasModifierProperty(PsiModifier.ABSTRACT)) {
            return;
        }

        boolean hasOneArgContext = false;
        boolean hasTwoArgContextAttributeSet = false;
        boolean hasThreeArgWithDefStyle = false;

        for (UMethod method : declaration.getMethods()) {
            if (!method.isConstructor()) {
                continue;
            }

            List<UParameter> parameters = method.getUastParameters();
            int size = parameters.size();

            if (size == 1) {
                if (isContext(parameters.get(0).getType())) {
                    hasOneArgContext = true;
                }
            } else if (size == 2) {
                if (isContext(parameters.get(0).getType())
                        && isAttributeSet(parameters.get(1).getType())) {
                    hasTwoArgContextAttributeSet = true;
                }
            } else if (size == 3) {
                if (isContext(parameters.get(0).getType())
                        && isAttributeSet(parameters.get(1).getType())
                        && isInt(parameters.get(2).getType())) {
                    hasThreeArgWithDefStyle = true;
                }
            }
        }

        List<String> missing = new ArrayList<>();
        if (!hasOneArgContext) {
            missing.add("View(Context)");
        }
        if (!hasTwoArgContextAttributeSet) {
            missing.add("View(Context, AttributeSet)");
        }
        if (!hasThreeArgWithDefStyle) {
            missing.add("View(Context, AttributeSet, int)");
        }

        if (!missing.isEmpty()) {
            String className = declaration.getQualifiedName();
            if (className == null) {
                className = declaration.getName();
            }

            String message = "Custom view "
                    + className
                    + " is missing the following constructor(s) required for XML inflation: "
                    + join(missing, ", ");

            context.report(ISSUE, declaration, context.getLocation(declaration), message);
        }
    }

    private static boolean isContext(PsiType type) {
        return type != null && "android.content.Context".equals(type.getCanonicalText());
    }

    private static boolean isAttributeSet(PsiType type) {
        return type != null && "android.util.AttributeSet".equals(type.getCanonicalText());
    }

    private static boolean isInt(PsiType type) {
        return type != null && PsiType.INT.equals(type);
    }

    private static String join(List<String> parts, String separator) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) {
                sb.append(separator);
            }
            sb.append(parts.get(i));
        }
        return sb.toString();
    }
}