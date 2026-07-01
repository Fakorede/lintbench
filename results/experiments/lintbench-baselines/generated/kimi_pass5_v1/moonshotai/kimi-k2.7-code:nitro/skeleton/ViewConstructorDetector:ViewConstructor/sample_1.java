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
                            + "View(Context context), View(Context context, AttributeSet attrs), "
                            + "View(Context context, AttributeSet attrs, int defStyle). "
                            + "If your custom view needs initialization that does not apply in a "
                            + "layout editor, check View#isInEditMode() to skip it at tool time.",
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
        if (declaration.isInterface()
                || declaration.isEnum()
                || declaration.hasModifierProperty(PsiModifier.ABSTRACT)) {
            return;
        }

        boolean hasContext = false;
        boolean hasContextAttributeSet = false;
        boolean hasContextAttributeSetDefStyle = false;

        for (UMethod constructor : declaration.getConstructors()) {
            List<UParameter> parameters = constructor.getUastParameters();
            int size = parameters.size();
            if (size == 1 && isType(parameters.get(0), ANDROID_CONTEXT)) {
                hasContext = true;
            } else if (size == 2
                    && isType(parameters.get(0), ANDROID_CONTEXT)
                    && isType(parameters.get(1), ANDROID_ATTRIBUTE_SET)) {
                hasContextAttributeSet = true;
            } else if (size == 3
                    && isType(parameters.get(0), ANDROID_CONTEXT)
                    && isType(parameters.get(1), ANDROID_ATTRIBUTE_SET)
                    && isInt(parameters.get(2))) {
                hasContextAttributeSetDefStyle = true;
            }
        }

        if (hasContext && hasContextAttributeSet && hasContextAttributeSetDefStyle) {
            return;
        }

        List<String> missing = new ArrayList<>();
        if (!hasContext) {
            missing.add("View(Context)");
        }
        if (!hasContextAttributeSet) {
            missing.add("View(Context, AttributeSet)");
        }
        if (!hasContextAttributeSetDefStyle) {
            missing.add("View(Context, AttributeSet, int)");
        }

        String name = declaration.getName();
        if (name == null) {
            name = declaration.getQualifiedName();
        }

        String message =
                "Custom view "
                        + name
                        + " is missing constructors required for XML inflation: "
                        + String.join(", ", missing);

        context.report(ISSUE, declaration, context.getLocation(declaration), message);
    }

    private static final String ANDROID_CONTEXT = "android.content.Context";
    private static final String ANDROID_ATTRIBUTE_SET = "android.util.AttributeSet";

    private static boolean isType(UParameter parameter, String fullyQualifiedName) {
        PsiType type = parameter.getType();
        return type != null && fullyQualifiedName.equals(type.getCanonicalText());
    }

    private static boolean isInt(UParameter parameter) {
        PsiType type = parameter.getType();
        return type != null
                && ("int".equals(type.getCanonicalText())
                        || "java.lang.Integer".equals(type.getCanonicalText()));
    }
}