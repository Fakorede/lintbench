package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiType;
import java.util.Collections;
import java.util.List;
import org.jetbrains.uast.UClass;

public class ViewConstructorDetector extends Detector implements SourceCodeScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(ViewConstructorDetector.class, Scope.JAVA_FILE_SCOPE);

    private static final String ANDROID_VIEW = "android.view.View";
    private static final String CONTEXT_TYPE = "android.content.Context";
    private static final String ATTRIBUTE_SET_TYPE = "android.util.AttributeSet";

    public static final Issue ISSUE =
            Issue.create(
                            "ViewConstructor",
                            "Missing View constructors for XML inflation",
                            "Some layout tools (such as the Android layout editor) need to find a "
                                    + "constructor with one of the following signatures:\n"
                                    + "* `View(Context context)`\n"
                                    + "* `View(Context context, AttributeSet attrs)`\n"
                                    + "* `View(Context context, AttributeSet attrs, int defStyle)`\n\n"
                                    + "If your custom view needs to perform initialization which does "
                                    + "not apply when used in a layout editor, you can surround the "
                                    + "given code with a check to see if `View#isInEditMode()` is "
                                    + "false, since that method will return false at runtime but "
                                    + "true within a user interface editor.",
                            Category.USABILITY,
                            6,
                            Severity.WARNING,
                            IMPLEMENTATION)
                    .setAndroidSpecific(true);

    public ViewConstructorDetector() {}

    @Nullable
    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList(ANDROID_VIEW);
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        if (ANDROID_VIEW.equals(declaration.getQualifiedName())) {
            return;
        }

        for (PsiMethod method : declaration.getMethods()) {
            if (method.isConstructor() && hasValidViewConstructorSignature(method)) {
                return;
            }
        }

        context.report(
                ISSUE,
                declaration,
                context.getNameLocation(declaration),
                "This custom view should define a constructor with one of the signatures "
                        + "View(Context), View(Context, AttributeSet) or "
                        + "View(Context, AttributeSet, int); otherwise it cannot be used "
                        + "in a layout XML file");
    }

    private static boolean hasValidViewConstructorSignature(@NonNull PsiMethod method) {
        PsiParameter[] parameters = method.getParameterList().getParameters();

        if (parameters.length == 1) {
            return isType(parameters[0], CONTEXT_TYPE);
        } else if (parameters.length == 2) {
            return isType(parameters[0], CONTEXT_TYPE)
                    && isType(parameters[1], ATTRIBUTE_SET_TYPE);
        } else if (parameters.length == 3) {
            return isType(parameters[0], CONTEXT_TYPE)
                    && isType(parameters[1], ATTRIBUTE_SET_TYPE)
                    && isType(parameters[2], "int");
        }

        return false;
    }

    private static boolean isType(@NonNull PsiParameter parameter, @NonNull String type) {
        PsiType parameterType = parameter.getType();
        return type.equals(parameterType.getCanonicalText());
    }
}